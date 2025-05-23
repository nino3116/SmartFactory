// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정)
// 이 엔드포인트는 서버 측 DefectController에서 제공하며,
// DefectService를 통해 데이터베이스에 저장된 최신 불량 정보를 가져옵니다.
const LATEST_DEFECTS_API_URL = "/api/latest-defects"; // 최신 불량 정보 API 엔드포인트

// 감지 로그 API 엔드포인트 URL
// 이 엔드포인트는 Spring Boot DefectController에서 구현되었습니다.
// 이 엔드포인트는 불량 감지 이벤트의 로그 데이터를 반환합니다.
const DETECTION_LOGS_API_URL = "/api/detection-logs"; // 감지 로그 API 엔드포인트

// Chart.js 인스턴스를 저장할 전역 변수
let weekStatusChart;
let detectionStatusChart;
let yearStatusChart;
let dayStatusChart; // 당일 감지 비율 차트 변수 추가

// Chart.js 플러그인: 데이터가 없을 때 텍스트 표시 (특히 파이 차트용)
const noDataTextPlugin = {
  id: "noDataText",
  beforeDraw: function (chart) {
    // 데이터셋이 비어 있는지 확인
    const dataset = chart.data.datasets[0];
    // 데이터셋이 없거나, 데이터가 없거나, 모든 값이 0인지 확인
    if (
      !dataset ||
      !dataset.data ||
      dataset.data.length === 0 ||
      dataset.data.every((value) => value === 0)
    ) {
      const ctx = chart.ctx;
      const width = chart.width;
      const height = chart.height;
      chart.clear(); // 기존 차트 내용 지우기

      ctx.save();
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.font = "16px sans-serif"; // 텍스트 스타일 조정
      ctx.fillStyle = "#6b7280"; // Tailwind gray-500 색상

      // 텍스트 위치 조정 (차트 중앙)
      const centerX = width / 2;
      const centerY = height / 2;

      ctx.fillText("데이터 없음", centerX, centerY); // 표시할 텍스트
      ctx.restore();
    }
  },
};

// 불량 정보를 가져와서 화면에 표시하는 함수 (최신 불량 결과 테이블)
// 이 함수는 차트 데이터에 직접 사용되지 않습니다. 차트는 감지 로그 데이터를 사용합니다.
async function fetchAndDisplayLatestDefects() {
  const defectTableBody = document.querySelector("#defect-table tbody");
  if (!defectTableBody) {
    console.error("Error: Element with id 'defect-table tbody' not found.");
    return; // Exit if the element is not found
  }

  // 로딩 메시지 표시 (첫 로딩 시 또는 데이터가 비어있을 때)
  if (
    defectTableBody.rows.length === 0 ||
    defectTableBody.rows[0].cells.length === 1
  ) {
    defectTableBody.innerHTML = `
          <tr>
            <td colspan="6" class="py-4 px-4 text-center text-gray-500">
              불량 정보 가져오는 중...
            </td>
        </tr>
      `;
  }

  try {
    const response = await fetch(LATEST_DEFECTS_API_URL);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const defects = await response.json(); // JSON 응답 파싱 (List<DefectInfo>)

    // 기존 행들을 Map에 저장하여 비교 및 업데이트 효율화
    const existingRows = new Map();
    defectTableBody.querySelectorAll("tr").forEach((row) => {
      // 각 행을 고유하게 식별할 수 있는 키 생성 (임시 방편, 백엔드 ID가 있다면 좋음)
      // 여기서는 클래스, 사유, 신뢰도, 박스 정보, 이미지 URL을 조합하여 키 생성
      // 실제 애플리케이션에서는 백엔드에서 제공하는 고유 ID를 사용하는 것이 가장 좋습니다.
      const cells = row.cells;
      if (cells.length > 1) {
        // 로딩/데이터 없음 메시지 행이 아닌 경우
        const key = `${cells[0].textContent}-${cells[1].textContent}-${
          cells[2].textContent
        }-${cells[3].textContent}-${cells[4].textContent}-${
          cells[5].querySelector("img")?.src || "no-image"
        }`;
        existingRows.set(key, row);
      }
    });

    const newTbody = document.createElement("tbody");
    let dataChanged = false; // 데이터 변경 여부 플래그

    if (defects && defects.length > 0) {
      // 새로 가져온 데이터를 순회하며 테이블 업데이트
      defects.forEach((defect) => {
        // 새로운 데이터 항목에 대한 키 생성
        const newKey = `${defect.clazz || "-"}-${defect.reason || "-"}-${
          defect.confidence !== undefined && defect.confidence !== null
            ? defect.confidence.toFixed(2)
            : "-"
        }-${
          defect.box && defect.box.length === 4
            ? `[${defect.box.map((coord) => coord.toFixed(2)).join(", ")}]`
            : "-"
        }-${
          defect.areaPercentOnApple !== undefined &&
          defect.areaPercentOnApple !== null
            ? defect.areaPercentOnApple.toFixed(2) + "%"
            : "-"
        }-${defect.imageUrl || "no-image"}`;

        if (existingRows.has(newKey)) {
          // 이미 존재하는 데이터이면 기존 행을 그대로 사용 (순서 유지를 위해 newTbody에 추가)
          newTbody.appendChild(existingRows.get(newKey));
          existingRows.delete(newKey); // 사용한 행은 Map에서 제거
        } else {
          // 새로운 데이터이면 새 행 생성 및 newTbody에 추가
          const row = document.createElement("tr");
          row.classList.add("hover:bg-gray-50", "new-row"); // 새 행에 애니메이션 클래스 추가
          dataChanged = true; // 데이터 변경 감지

          let clazzCell = document.createElement("td");
          clazzCell.classList.add("py-2", "px-4", "border-b");
          clazzCell.textContent = defect.clazz || "-";
          row.appendChild(clazzCell);

          let reasonCell = document.createElement("td");
          reasonCell.classList.add("py-2", "px-4", "border-b");
          reasonCell.textContent = defect.reason || "-";
          row.appendChild(reasonCell);

          let confidenceCell = document.createElement("td");
          confidenceCell.classList.add("py-2", "px-4", "border-b");
          confidenceCell.textContent =
            defect.confidence !== undefined && defect.confidence !== null
              ? defect.confidence.toFixed(2)
              : "-";
          row.appendChild(confidenceCell);

          let boxCell = document.createElement("td");
          boxCell.classList.add("py-2", "px-4", "border-b");
          if (defect.box && defect.box.length === 4) {
            boxCell.textContent = `[${defect.box
              .map((coord) => coord.toFixed(2))
              .join(", ")}]`;
          } else {
            boxCell.textContent = "-";
          }
          row.appendChild(boxCell);

          let areaPercentCell = document.createElement("td");
          areaPercentCell.classList.add("py-2", "px-4", "border-b");
          areaPercentCell.textContent =
            defect.areaPercentOnApple !== undefined &&
            defect.areaPercentOnApple !== null
              ? defect.areaPercentOnApple.toFixed(2) + "%"
              : "-";
          row.appendChild(areaPercentCell);

          let imageCell = document.createElement("td");
          imageCell.classList.add("py-2", "px-4", "border-b", "text-center");
          if (defect.imageUrl) {
            const img = document.createElement("img");
            img.src = defect.imageUrl;
            img.alt = "Defect Snapshot";
            img.classList.add("defect-image");
            img.onerror = function () {
              this.onerror = null;
              this.src =
                "https://placehold.co/100x100/e0e0e0/6b7280?text=No+Image";
            }; // 이미지 로드 오류 시 대체 이미지
            imageCell.appendChild(img);
          } else {
            imageCell.textContent = "이미지 없음";
          }
          row.appendChild(imageCell);

          newTbody.appendChild(row); // 새 행 추가
        }
      });

      // 기존에 있었지만 새로 가져온 데이터에 없는 행들은 제거
      for (const [_, row] of existingRows) {
        row.remove(); // DOM에서 행 제거
        dataChanged = true; // 데이터 변경 감지
      }

      // 새로운 tbody로 교체 (DOM 조작 최소화)
      if (dataChanged || defectTableBody.rows.length !== defects.length) {
        defectTableBody.parentNode.replaceChild(newTbody, defectTableBody);
        newTbody.id = "defect-table-body"; // ID 유지 또는 새로운 ID 할당
        // 다음 업데이트를 위해 전역 변수 업데이트 필요 (또는 다시 쿼리)
        // 여기서는 함수 내에서만 사용하므로 굳이 전역 변수 업데이트는 필요 없음
      }

      // 차트 업데이트는 감지 로그 데이터를 사용하므로 여기서 호출하지 않습니다.
      // updateCharts(defects);
    } else {
      // 불량 정보가 없으면 메시지 표시하고 기존 행 모두 제거
      if (
        defectTableBody.rows.length > 1 ||
        (defectTableBody.rows.length === 1 &&
          defectTableBody.rows[0].cells.length > 1)
      ) {
        dataChanged = true; // 데이터 변경 감지
      }
      defectTableBody.innerHTML = `
          <tr>
            <td colspan="6" class="py-4 px-4 text-center text-gray-500">
              감지된 불량이 없습니다.
            </td>
          </tr>
        `;
      if (dataChanged) {
        // 새로운 tbody로 교체 (데이터 없음 메시지 포함)
        const emptyTbody = document.createElement("tbody");
        emptyTbody.id = "defect-table-body";
        emptyTbody.innerHTML = `
                <tr>
                  <td colspan="6" class="py-4 px-4 text-center text-gray-500">
                    감지된 불량이 없습니다.
                  </td>
                </tr>
            `;
        defectTableBody.parentNode.replaceChild(emptyTbody, defectTableBody);
      }
    }
  } catch (error) {
    console.error("불량 정보를 가져오는 중 오류 발생:", error);
    // 오류 발생 시 메시지 표시 및 기존 행 모두 제거
    if (
      defectTableBody.rows.length > 1 ||
      (defectTableBody.rows.length === 1 &&
        defectTableBody.rows[0].cells.length > 1)
    ) {
      // 기존에 데이터가 있었다면 변경된 것으로 간주
    }
    defectTableBody.innerHTML = `
      <tr>
        <td colspan="6" class="py-4 px-4 text-center text-red-500">
          오류 발생: ${error.message}
        </td>
      </tr>
    `;
    // 새로운 tbody로 교체 (오류 메시지 포함)
    const errorTbody = document.createElement("tbody");
    errorTbody.id = "defect-table-body";
    errorTbody.innerHTML = `
        <tr>
          <td colspan="6" class="py-4 px-4 text-center text-red-500">
            오류 발생: ${error.message}
          </td>
        </tr>
    `;
    defectTableBody.parentNode.replaceChild(errorTbody, defectTableBody);
  }
}

// 차트 업데이트 함수 (Chart.js 사용)
// 이 함수는 감지 로그 데이터를 사용하여 차트를 업데이트합니다.
async function updateChartsFromLogs() {
  try {
    const response = await fetch(DETECTION_LOGS_API_URL); // 감지 로그 API 호출

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const logs = await response.json(); // JSON 응답 파싱 (List<DetectionLog> 객체)

    // 데이터가 없거나 비어있는 경우를 처리
    const hasData = logs && logs.length > 0;

    // 1. 전체 감지 상태 비율 차트 데이터 가공 (파이 차트)
    let detectionStatusData;
    if (hasData) {
      const statusCounts = logs.reduce((acc, log) => {
        acc[log.status] = (acc[log.status] || 0) + 1;
        return acc;
      }, {});
      detectionStatusData = {
        labels: Object.keys(statusCounts),
        datasets: [
          {
            label: "감지 상태",
            data: Object.values(statusCounts),
            backgroundColor: [
              "rgba(75, 192, 192, 0.5)", // Normal (Greenish)
              "rgba(255, 99, 132, 0.5)", // Defect Detected (Reddish)
              // 다른 상태가 있다면 추가 색상 정의
            ],
            borderColor: ["rgba(75, 192, 192, 1)", "rgba(255, 99, 132, 1)"],
            borderWidth: 1,
          },
        ],
      };
    } else {
      // 데이터가 없을 때 빈 데이터셋 설정
      detectionStatusData = {
        labels: [],
        datasets: [
          {
            label: "감지 상태",
            data: [],
            backgroundColor: [],
            borderColor: [],
            borderWidth: 1,
          },
        ],
      };
    }

    // 2. 주간 감지 상태 차트 데이터 가공 (막대 차트 - 최근 7일)
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7); // 7일 전 날짜 계산
    sevenDaysAgo.setHours(0, 0, 0, 0); // 시간을 0으로 설정하여 날짜만 비교

    let weekStatusData;
    if (hasData) {
      const dailyDefectCounts = logs.reduce((acc, log) => {
        const logDate = new Date(log.detectionTime);
        logDate.setHours(0, 0, 0, 0); // 시간을 0으로 설정하여 날짜만 비교
        // 최근 7일 이내의 로그만 포함 (오늘 포함)
        if (logDate >= sevenDaysAgo) {
          // 날짜를 'YYYY-MM-DD' 형식의 문자열로 변환하여 키로 사용
          // Use local date components to construct the key
          const dateString = `${logDate.getFullYear()}-${(
            logDate.getMonth() + 1
          )
            .toString()
            .padStart(2, "0")}-${logDate
            .getDate()
            .toString()
            .padStart(2, "0")}`;
          if (log.status === "Defect Detected" && log.defectCount > 0) {
            acc[dateString] = (acc[dateString] || 0) + log.defectCount;
          } else if (
            log.status === "Normal" &&
            (acc[dateString] === undefined || acc[dateString] === null)
          ) {
            // 정상 감지일 경우, 해당 날짜에 불량 감지 기록이 없으면 0으로 초기화
            // 이 부분은 주간 "불량 개수" 차트이므로 Normal 로그는 개수에 포함하지 않습니다.
            // acc[dateString] = acc[dateString] || 0;
          }
        }
        return acc;
      }, {});

      // 최근 7일간의 모든 날짜를 포함하도록 데이터 보강
      const dateLabels = [];
      const defectCountsData = [];
      for (let i = 6; i >= 0; i--) {
        // 오늘부터 7일 전까지 역순으로
        const date = new Date();
        date.setDate(date.getDate() - i);
        // Set hours to 0 for consistent date comparison
        date.setHours(0, 0, 0, 0);
        // Use local date components to construct the key
        const dateString = `${date.getFullYear()}-${(date.getMonth() + 1)
          .toString()
          .padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`;
        // 라벨은 'MM-DD' 형식으로 표시
        dateLabels.push(`${date.getMonth() + 1}-${date.getDate()}`);
        // 해당 날짜의 불량 개수, 없으면 0
        defectCountsData.push(dailyDefectCounts[dateString] || 0);
      }

      weekStatusData = {
        labels: dateLabels, // 'MM-DD' 형식의 날짜 라벨
        datasets: [
          {
            label: "불량 개수",
            data: defectCountsData, // 일별 불량 개수
            backgroundColor: "rgba(54, 162, 235, 0.5)", // 파랑
            borderColor: "rgba(54, 162, 235, 1)",
            borderWidth: 1,
          },
        ],
      };
    } else {
      // 데이터가 없을 때 최근 7일 라벨만 생성하고 데이터는 0으로 채움
      const dateLabels = [];
      const defectCountsData = [];
      for (let i = 6; i >= 0; i--) {
        const date = new Date();
        date.setDate(date.getDate() - i);
        dateLabels.push(`${date.getMonth() + 1}-${date.getDate()}`);
        defectCountsData.push(0); // 데이터 없음
      }
      weekStatusData = {
        labels: dateLabels,
        datasets: [
          {
            label: "불량 개수",
            data: defectCountsData,
            backgroundColor: "rgba(54, 162, 235, 0.5)",
            borderColor: "rgba(54, 162, 235, 1)",
            borderWidth: 1,
          },
        ],
      };
    }

    // 3. 연간 불량 감지 추이 차트 데이터 가공 (선형 차트 - 최근 12개월)
    const twelveMonthsAgo = new Date();
    twelveMonthsAgo.setMonth(twelveMonthsAgo.getMonth() - 11); // 12개월 전 (현재 월 포함)
    twelveMonthsAgo.setDate(1); // 해당 월의 1일로 설정
    twelveMonthsAgo.setHours(0, 0, 0, 0); // 시간을 0으로 설정

    let yearStatusData;
    if (hasData) {
      const monthlyDefectCounts = logs.reduce((acc, log) => {
        const logDate = new Date(log.detectionTime);
        logDate.setDate(1); // 해당 월의 1일로 설정
        logDate.setHours(0, 0, 0, 0); // 시간을 0으로 설정
        // 최근 12개월 이내의 로그만 포함
        if (logDate >= twelveMonthsAgo) {
          // 월을 'YYYY-MM' 형식의 문자열로 변환하여 키로 사용
          const yearMonthString = `${logDate.getFullYear()}-${(
            logDate.getMonth() + 1
          )
            .toString()
            .padStart(2, "0")}`;
          if (log.status === "Defect Detected" && log.defectCount > 0) {
            acc[yearMonthString] =
              (acc[yearMonthString] || 0) + log.defectCount;
          } else if (
            log.status === "Normal" &&
            (acc[yearMonthString] === undefined ||
              acc[yearMonthString] === null)
          ) {
            // 정상 감지일 경우, 해당 월에 불량 감지 기록이 없으면 0으로 초기화
            // 이 부분은 연간 "불량 개수" 차트이므로 Normal 로그는 개수에 포함하지 않습니다.
            // acc[yearMonthString] = acc[yearMonthString] || 0;
          }
        }
        return acc;
      }, {});

      // 최근 12개월의 모든 월을 포함하도록 데이터 보강
      const monthLabels = [];
      const monthlyData = [];
      for (let i = 11; i >= 0; i--) {
        // 현재 월부터 12개월 전까지 역순으로
        const date = new Date();
        date.setMonth(date.getMonth() - i);
        const yearMonthString = `${date.getFullYear()}-${(date.getMonth() + 1)
          .toString()
          .padStart(2, "0")}`;
        // 라벨은 'YYYY-MM' 형식으로 표시
        monthLabels.push(yearMonthString);
        // 해당 월의 불량 개수, 없으면 0
        monthlyData.push(monthlyDefectCounts[yearMonthString] || 0);
      }

      yearStatusData = {
        labels: monthLabels, // 'YYYY-MM' 형식의 월 라벨
        datasets: [
          {
            label: "불량 개수",
            data: monthlyData, // 월별 불량 개수
            fill: false, // 선 아래 영역 채우지 않음
            borderColor: "rgba(255, 159, 64, 1)", // 주황색
            tension: 0.1, // 선의 곡률
          },
        ],
      };
    } else {
      // 데이터가 없을 때 최근 12개월 라벨만 생성하고 데이터는 0으로 채움
      const monthLabels = [];
      const monthlyData = [];
      for (let i = 11; i >= 0; i--) {
        const date = new Date();
        date.setMonth(date.getMonth() - i);
        const yearMonthString = `${date.getFullYear()}-${(date.getMonth() + 1)
          .toString()
          .padStart(2, "0")}`;
        monthLabels.push(yearMonthString);
        monthlyData.push(0); // 데이터 없음
      }
      yearStatusData = {
        labels: monthLabels,
        datasets: [
          {
            label: "불량 개수",
            data: monthlyData,
            fill: false,
            borderColor: "rgba(255, 159, 64, 1)",
            tension: 0.1,
          },
        ],
      };
    }

    // 4. 당일 감지 상태 비율 차트 데이터 가공 (파이 차트)
    let dayStatusData;
    const today = new Date();
    today.setHours(0, 0, 0, 0); // 오늘 날짜의 시작 시간

    if (hasData) {
      const todayLogs = logs.filter((log) => {
        try {
          const logDate = new Date(log.detectionTime);
          // 날짜 부분만 비교
          const logDateOnly = new Date(
            logDate.getFullYear(),
            logDate.getMonth(),
            logDate.getDate()
          );
          return logDateOnly.getTime() === today.getTime();
        } catch (e) {
          console.error(
            "Error parsing log date for today's chart:",
            log.detectionTime,
            e
          );
          return false; // 파싱 오류 시 해당 로그 제외
        }
      });

      const todayStatusCounts = todayLogs.reduce((acc, log) => {
        acc[log.status] = (acc[log.status] || 0) + 1;
        return acc;
      }, {});

      // 데이터가 없더라도 'Normal'과 'Defect Detected' 라벨은 항상 표시
      const labels = ["Normal", "Defect Detected"];
      const data = [
        todayStatusCounts.Normal || 0, // Normal 개수, 없으면 0
        todayStatusCounts["Defect Detected"] || 0, // Defect Detected 개수, 없으면 0
      ];

      dayStatusData = {
        labels: labels,
        datasets: [
          {
            label: "감지 상태",
            data: data,
            backgroundColor: [
              "rgba(75, 192, 192, 0.5)", // Normal (Greenish)
              "rgba(255, 99, 132, 0.5)", // Defect Detected (Reddish)
            ],
            borderColor: ["rgba(75, 192, 192, 1)", "rgba(255, 99, 132, 1)"],
            borderWidth: 1,
          },
        ],
      };
    } else {
      // 데이터가 없을 때 빈 데이터셋 설정
      dayStatusData = {
        labels: ["Normal", "Defect Detected"], // 라벨은 유지
        datasets: [
          {
            label: "감지 상태",
            data: [0, 0], // 데이터는 0으로 설정
            backgroundColor: [
              "rgba(75, 192, 192, 0.5)",
              "rgba(255, 99, 132, 0.5)",
            ],
            borderColor: ["rgba(75, 192, 192, 1)", "rgba(255, 99, 132, 1)"],
            borderWidth: 1,
          },
        ],
      };
    }

    // 차트 업데이트 또는 새로 생성
    // Detection Status Chart (Pie Chart)
    const ctx1 = document
      .getElementById("detectionStatusChart")
      .getContext("2d");
    if (detectionStatusChart) {
      detectionStatusChart.data = detectionStatusData;
      detectionStatusChart.update();
    } else {
      detectionStatusChart = new Chart(ctx1, {
        type: "pie", // 파이 차트
        data: detectionStatusData,
        options: {
          responsive: true,
          maintainAspectRatio: false,
          layout: {
            padding: 40,
          },
          plugins: {
            legend: {
              position: "top",
            },
            title: {
              display: true,
              text: "전체 감지 상태 비율",
            },
            // DataLabels 플러그인 설정 추가
            datalabels: {
              formatter: (value, ctx) => {
                const total = ctx.chart.data.datasets[0].data.reduce(
                  (sum, val) => sum + val,
                  0
                );
                if (total === 0) return ""; // 데이터가 없으면 빈 문자열 반환
                const percentage = `${((value / total) * 100).toFixed(1)}%`;
                return percentage;
              },
              color: "#fff", // 라벨 색상 (흰색)
              textShadowBlur: 4, // 텍스트 그림자 블러
              textShadowColor: "rgba(0, 0, 0, 0.5)", // 텍스트 그림자 색상
            },
            // noDataText 플러그인 등록
            noDataText: noDataTextPlugin,
          },
        },
        // plugins 배열에 noDataText 플러그인 추가 (datalabels는 options.plugins에 설정)
        plugins: [noDataTextPlugin],
      });
    }

    // Week Status Chart (Bar Chart)
    const ctx2 = document.getElementById("weekStatusChart").getContext("2d");
    if (weekStatusChart) {
      weekStatusChart.data = weekStatusData;
      weekStatusChart.update();
    } else {
      weekStatusChart = new Chart(ctx2, {
        type: "bar", // 막대 차트
        data: weekStatusData,
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: {
              beginAtZero: true,
              title: {
                display: true,
                text: "불량 개수",
              },
            },
            x: {
              title: {
                display: true,
                text: "날짜",
              },
            },
          },
          layout: {
            padding: 40,
          },
          plugins: {
            legend: {
              display: true,
              position: "top",
            },
            title: {
              display: true,
              text: "주간 불량 감지 추이",
            },
          },
        },
      });
    }

    // Year Status Chart (Line Chart)
    const ctx3 = document.getElementById("yearStatusChart").getContext("2d");
    if (yearStatusChart) {
      yearStatusChart.data = yearStatusData;
      yearStatusChart.update();
    } else {
      yearStatusChart = new Chart(ctx3, {
        type: "line", // 선형 차트
        data: yearStatusData,
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: {
              beginAtZero: true,
              title: {
                display: true,
                text: "불량 개수",
              },
            },
            x: {
              title: {
                display: true,
                text: "월",
              },
            },
          },
          layout: {
            padding: 40,
          },
          plugins: {
            legend: {
              display: true,
              position: "top",
            },
            title: {
              display: true,
              text: "연간 불량 감지 추이",
            },
          },
        },
      });
    }

    // Day Status Chart (Pie Chart) - 새로 추가된 차트
    const ctx4 = document.getElementById("dayStatusChart").getContext("2d");
    if (dayStatusChart) {
      dayStatusChart.data = dayStatusData;
      dayStatusChart.update();
    } else {
      dayStatusChart = new Chart(ctx4, {
        type: "pie", // 파이 차트
        data: dayStatusData,
        options: {
          responsive: true,
          maintainAspectRatio: false,
          layout: {
            padding: 40,
          },
          plugins: {
            legend: {
              position: "top",
            },
            title: {
              display: true,
              text: "당일 감지 상태 비율",
            },
            // DataLabels 플러그인 설정 추가
            datalabels: {
              formatter: (value, ctx) => {
                const total = ctx.chart.data.datasets[0].data.reduce(
                  (sum, val) => sum + val,
                  0
                );
                if (total === 0) return ""; // 데이터가 없으면 빈 문자열 반환
                const percentage = `${((value / total) * 100).toFixed(1)}%`;
                return percentage;
              },
              color: "#fff", // 라벨 색상 (흰색)
              textShadowBlur: 4, // 텍스트 그림자 블러
              textShadowColor: "rgba(0, 0, 0, 0.5)", // 텍스트 그림자 색상
            },
            // noDataText 플러그인 등록
            noDataText: noDataTextPlugin,
          },
        },
        // plugins 배열에 noDataText 플러그인 추가 (datalabels는 options.plugins에 설정)
        plugins: [noDataTextPlugin],
      });
    }
  } catch (error) {
    console.error("차트 데이터 가져오는 중 오류 발생:", error);
    // 오류 발생 시 차트 초기화 또는 오류 메시지 표시
    if (weekStatusChart) weekStatusChart.destroy();
    if (detectionStatusChart) detectionStatusChart.destroy();
    if (yearStatusChart) yearStatusChart.destroy();
    if (dayStatusChart) dayStatusChart.destroy();
    // 필요에 따라 "데이터 없음" 메시지 등을 캔버스 영역에 표시하는 로직 추가
  }
}

// 페이지 로드 시 및 주기적으로 불량 정보 및 감지 로그 가져오기
document.addEventListener("DOMContentLoaded", () => {
  fetchAndDisplayLatestDefects(); // 페이지 로드 시 최신 불량 정보 즉시 가져오기
  updateChartsFromLogs(); // 페이지 로드 시 감지 로그 즉시 가져오기 (차트 업데이트 포함)

  // 5초마다 불량 정보 업데이트 (주기는 필요에 따라 조정)
  setInterval(fetchAndDisplayLatestDefects, 5000);
  // 10초마다 감지 로그 업데이트 (주기는 필요에 따라 조정) - 이제 차트 업데이트만 수행
  setInterval(updateChartsFromLogs, 10000); // 감지 로그 데이터를 사용하여 차트만 업데이트
});

// TODO: 초기 차트 로딩 함수는 updateChartsFromLogs 함수 내에서 차트 인스턴스를
// 전역 변수에 할당하고 없으면 새로 생성하는 방식으로 대체되었습니다.
// function loadInitialCharts() { }

// 차트 업데이트 함수 이름 변경 (로그 데이터를 사용함을 명확히 함)
// function updateCharts(logs) { ... }
