// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정)
const SPRING_BOOT_BASE_URL = "http://localhost:80"; // Spring Boot 기본 URL (예: "http://localhost:8080") - 필요에 따라 설정

const LATEST_DEFECTS_API_URL = `${SPRING_BOOT_BASE_URL}/api/latest-defects`; // 최신 불량 정보 API 엔드포인트
const DETECTION_LOGS_API_URL = `${SPRING_BOOT_BASE_URL}/api/detection-logs`; // 감지 로그 API 엔드포인트

// 스크립트 제어 API 엔드포인트 (Spring Boot에서 구현 필요)
const SCRIPT_CONTROL_START_URL = `${SPRING_BOOT_BASE_URL}/api/control/start`;
const SCRIPT_CONTROL_STOP_URL = `${SPRING_BOOT_BASE_URL}/api/control/stop`;
// 스크립트 상태 조회 API 엔드포인트 (Spring Boot에서 구현 필요)
const SCRIPT_STATUS_API_URL = `${SPRING_BOOT_BASE_URL}/api/status/script`;


// Chart.js 인스턴스를 저장할 전역 변수 (이 페이지에서는 사용되지 않지만, 대시보드 페이지와 구조 통일)
let weekStatusChart;
let detectionStatusChart;
let yearStatusChart;


// 불량 정보를 가져와서 화면에 표시하는 함수 (최신 불량 결과 테이블)
// 이 함수는 차트 데이터에 직접 사용되지 않습니다. 차트는 감지 로그 데이터를 사용합니다.
async function fetchAndDisplayLatestDefects() {
  const defectTableBody = document.querySelector("#defect-table tbody");
  if (!defectTableBody) {
    console.error(
      "Error: Element with id 'defect-table tbody' not found."
    );
    return; // Exit if the element is not found
  }
  defectTableBody.innerHTML = `
      <tr>
        <td colspan="6" class="py-4 px-4 text-center text-gray-500">
          불량 정보 가져오는 중...
        </td>
    </tr>
  `; // 로딩 메시지

  try {
    const response = await fetch(LATEST_DEFECTS_API_URL);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const defects = await response.json(); // JSON 응답 파싱 (List<DefectInfo>)

    defectTableBody.innerHTML = ""; // 이전 내용 지우기

    if (defects && defects.length > 0) {
      // 불량 정보가 있으면 각 항목을 테이블 행으로 표시
      defects.forEach((defect) => {
        const row = document.createElement("tr");
        row.classList.add("hover:bg-gray-50"); // Tailwind hover 효과

        // 클래스 (clazz)
        let clazzCell = document.createElement("td");
        clazzCell.classList.add("py-2", "px-4", "border-b");
        clazzCell.textContent = defect.clazz || "-"; // 데이터가 없을 경우 '-' 표시
        row.appendChild(clazzCell);

        // 사유 (reason)
        let reasonCell = document.createElement("td");
        reasonCell.classList.add("py-2", "px-4", "border-b");
        reasonCell.textContent = defect.reason || "-";
        row.appendChild(reasonCell);

        // 신뢰도 (confidence)
        let confidenceCell = document.createElement("td");
        confidenceCell.classList.add("py-2", "px-4", "border-b");
        confidenceCell.textContent =
          defect.confidence !== undefined && defect.confidence !== null
            ? defect.confidence.toFixed(2)
            : "-";
        row.appendChild(confidenceCell);

        // 위치 (box)
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

        // 면적 비율 (areaPercentOnApple)
        let areaPercentCell = document.createElement("td");
        areaPercentCell.classList.add("py-2", "px-4", "border-b");
        areaPercentCell.textContent =
          defect.areaPercentOnApple !== undefined &&
          defect.areaPercentOnApple !== null
            ? defect.areaPercentOnApple.toFixed(2) + "%"
            : "-";
        row.appendChild(areaPercentCell);

        // 감지 사진 (imageUrl)
        let imageCell = document.createElement("td");
        imageCell.classList.add(
          "py-2",
          "px-4",
          "border-b",
          "text-center"
        ); // 이미지 가운데 정렬
        if (defect.imageUrl) {
          const img = document.createElement("img");
          img.src = defect.imageUrl;
          img.alt = "Defect Snapshot";
          img.classList.add("defect-image"); // CSS 클래스 적용
          imageCell.appendChild(img);
        } else {
          imageCell.textContent = "이미지 없음";
        }
        row.appendChild(imageCell);

        defectTableBody.appendChild(row); // 테이블 본문에 행 추가
      });
    } else {
      // 불량 정보가 없으면 메시지 표시
      defectTableBody.innerHTML = `
        <tr>
          <td colspan="6" class="py-4 px-4 text-center text-gray-500">
            감지된 불량이 없습니다.
          </td>
        </tr>
      `;
    }
  } catch (error) {
    console.error("불량 정보를 가져오는 중 오류 발생:", error);
    defectTableBody.innerHTML = `
      <tr>
        <td colspan="6" class="py-4 px-4 text-center text-red-500">
          오류 발생: ${error.message}
        </td>
      </tr>
    `;
  }
}

// 감지 로그를 가져와서 화면에 표시하는 함수 (감지 로그 테이블)
// 이 함수는 Spring Boot에서 구현된 /api/detection-logs 엔드포인트에서 데이터를 가져옵니다.
async function fetchAndDisplayDetectionLogs() {
  const logTableBody = document.querySelector(
    "#detection-log-table tbody"
  );
  if (!logTableBody) {
    console.error(
      "Error: Element with id 'detection-log-table tbody' not found."
    );
    return; // Exit if the element is not found
  }
  logTableBody.innerHTML = `
          <tr>
           <td colspan="5" class="py-4 px-4 text-center text-gray-500">
                감지 로그 가져오는 중...
           </td>
         </tr>
       `; // 로딩 메시지

  try {
    const response = await fetch(DETECTION_LOGS_API_URL);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    // 감지 로그 데이터의 JSON 구조에 맞게 파싱 및 처리
    // 예상 구조: List<DetectionLog> 객체
    const logs = await response.json(); // JSON 응답 파싱

    logTableBody.innerHTML = ""; // 이전 내용 지우기

    if (logs && logs.length > 0) {
      logs.forEach((log) => {
        const row = document.createElement("tr");
        row.classList.add("hover:bg-gray-50");

        // 시간 (detectionTime)
        let timeCell = document.createElement("td");
        timeCell.classList.add("py-2", "px-4", "border-b");
        // LocalDateTime 객체는 ISO 8601 형식 문자열로 넘어올 수 있습니다.
        // JavaScript Date 객체로 파싱하여 로컬 시간 문자열로 변환합니다.
        try {
          timeCell.textContent = log.detectionTime
            ? new Date(log.detectionTime).toLocaleString()
            : "-";
        } catch (e) {
          console.error(
            "Error parsing detection time:",
            log.detectionTime,
            e
          );
          timeCell.textContent = log.detectionTime || "-"; // 파싱 실패 시 원본 문자열 표시
        }
        row.appendChild(timeCell);

        // 상태 (status) - 예: "Normal", "Defect Detected"
        let statusCell = document.createElement("td");
        statusCell.classList.add("py-2", "px-4", "border-b");
        statusCell.textContent = log.status || "-";
        row.appendChild(statusCell);

        // 불량 유형 요약 (defectSummary) - 불량 감지 시에만 해당
        let summaryCell = document.createElement("td");
        summaryCell.classList.add("py-2", "px-4", "border-b");
        summaryCell.textContent = log.defectSummary || "-"; // defectSummary 필드 사용
        row.appendChild(summaryCell);

        // 불량 개수 (defectCount) - 불량 감지 시에만 해당
        let countCell = document.createElement("td");
        countCell.classList.add("py-2", "px-4", "border-b");
        countCell.textContent =
          log.defectCount !== undefined && log.defectCount !== null
            ? log.defectCount
            : "-";
        row.appendChild(countCell);

        // 이미지 (imageUrl) - 불량 감지 시 스냅샷 이미지
        let imageCell = document.createElement("td");
        imageCell.classList.add(
          "py-2",
          "px-4",
          "border-b",
          "text-center"
        );
        if (log.imageUrl) {
          const img = document.createElement("img");
          img.src = log.imageUrl;
          img.alt = "Log Snapshot";
          img.classList.add("defect-image");
          imageCell.appendChild(img);
        } else {
          imageCell.textContent = "이미지 없음";
        }
        row.appendChild(imageCell);

        // TODO: 필요에 따라 다른 컬럼 추가

        logTableBody.appendChild(row);
      });
    } else {
      logTableBody.innerHTML = `
                <tr>
                  <td colspan="5" class="py-4 px-4 text-center text-gray-500">
                        감지 로그가 없습니다.
                  </td>
                </tr>
              `;
    }

    // 차트 업데이트 함수 호출 (이 페이지에서는 차트가 없지만, 함수 구조 유지)
    // updateCharts(logs);

  } catch (error) {
    console.error("감지 로그를 가져오는 중 오류 발생:", error);
    logTableBody.innerHTML = `
            <tr>
             <td colspan="5" class="py-4 px-4 text-center text-red-500">
                오류 발생: ${error.message}
             </td>
            </tr>
          `;
  }
}

// 스크립트 상태를 가져와서 표시하는 함수
async function fetchAndDisplayScriptStatus() {
    const statusSpan = document.getElementById('script-status');
     if (!statusSpan) {
        console.error("Error: Element with id 'script-status' not found.");
        return;
    }
    try {
        const response = await fetch(SCRIPT_STATUS_API_URL);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const status = await response.text(); // 상태는 단순 문자열로 예상
        statusSpan.textContent = status;
    } catch (error) {
        console.error("스크립트 상태를 가져오는 중 오류 발생:", error);
        statusSpan.textContent = `오류: ${error.message}`;
        statusSpan.style.color = 'red'; // 오류 발생 시 빨간색으로 표시
    }
}

// 스크립트 시작 명령을 보내는 함수
async function startScript() {
    try {
        const response = await fetch(SCRIPT_CONTROL_START_URL, {
            method: 'POST', // POST 요청
            headers: {
                'Content-Type': 'application/json' // 필요에 따라 Content-Type 설정
            },
            // body: JSON.stringify({}) // 필요한 경우 요청 본문 추가
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        console.log("스크립트 시작 명령 전송 성공");
        // 명령 전송 후 상태를 즉시 업데이트 시도
        fetchAndDisplayScriptStatus();
    } catch (error) {
        console.error("스크립트 시작 명령 전송 중 오류 발생:", error);
        alert(`스크립트 시작 명령 전송 실패: ${error.message}`); // 사용자에게 알림
    }
}

// 스크립트 중지 명령을 보내는 함수
async function stopScript() {
    try {
         const response = await fetch(SCRIPT_CONTROL_STOP_URL, {
            method: 'POST', // POST 요청
             headers: {
                'Content-Type': 'application/json' // 필요에 따라 Content-Type 설정
            },
            // body: JSON.stringify({}) // 필요한 경우 요청 본문 추가
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        console.log("스크립트 중지 명령 전송 성공");
         // 명령 전송 후 상태를 즉시 업데이트 시도
        fetchAndDisplayScriptStatus();
    } catch (error) {
        console.error("스크립트 중지 명령 전송 중 오류 발생:", error);
         alert(`스크립트 중지 명령 전송 실패: ${error.message}`); // 사용자에게 알림
    }
}


// 페이지 로드 시 및 주기적으로 불량 정보, 감지 로그, 스크립트 상태 가져오기
document.addEventListener("DOMContentLoaded", () => {
  fetchAndDisplayLatestDefects(); // 페이지 로드 시 최신 불량 정보 즉시 가져오기
  fetchAndDisplayDetectionLogs(); // 페이지 로드 시 감지 로그 즉시 가져오기
  fetchAndDisplayScriptStatus(); // 페이지 로드 시 스크립트 상태 즉시 가져오기

  // 5초마다 불량 정보 업데이트 (주기는 필요에 따라 조정)
  setInterval(fetchAndDisplayLatestDefects, 5000);
  // 10초마다 감지 로그 업데이트 (주기는 필요에 따라 조정)
  setInterval(fetchAndDisplayDetectionLogs, 10000);
  // 5초마다 스크립트 상태 업데이트 (주기는 필요에 따라 조정)
  setInterval(fetchAndDisplayScriptStatus, 5000);

  // 스크립트 제어 버튼 이벤트 리스너 추가
  const startButton = document.getElementById('start-script-btn');
  const stopButton = document.getElementById('stop-script-btn');

  if (startButton) {
      startButton.addEventListener('click', startScript);
  } else {
       console.error("Error: Element with id 'start-script-btn' not found.");
  }

  if (stopButton) {
      stopButton.addEventListener('click', stopScript);
  } else {
       console.error("Error: Element with id 'stop-script-btn' not found.");
  }

  // --- 이미지 소스 동적 로딩을 위한 JavaScript 함수 ---
  const primaryStreamUrl = "http://localhost:8080";
  const fallbackStreamUrl = "http://192.168.0.124:8000/stream.mjpg";
  const streamImg = document.getElementById('mjpeg-stream-img');
  const statusMessage = document.getElementById('stream-status-message');

  // 대체 URL 시도 여부를 추적하는 플래그
  let fallbackAttempted = false;


  // Check if elements exist before adding listeners
  if (streamImg && statusMessage) {
      // 이미지 로드 성공 시 호출될 함수
      const handleStreamLoad = () => {
          console.log("스트림 로드 성공:", streamImg.src);
          statusMessage.textContent = "스트림 연결됨";
          statusMessage.className = "stream-status status-success"; // 성공 상태 스타일 적용
          // 성공 후에는 오류 핸들러를 제거하여 불필요한 로직 실행 방지
          streamImg.removeEventListener('error', handleStreamError);
           streamImg.removeEventListener('load', handleStreamLoad); // 로드 핸들러도 제거 (한 번만 필요)
      };

      // 이미지 로드 실패 시 호출될 함수
      const handleStreamError = () => {
          console.error("스트림 로드 실패:", streamImg.src);

          // 아직 대체 URL을 시도하지 않았다면 시도합니다.
          if (!fallbackAttempted) {
              console.log("첫 번째 스트림 실패. 두 번째 스트림 시도:", fallbackStreamUrl);
              streamImg.src = fallbackStreamUrl; // 대체 URL로 변경
              statusMessage.textContent = "첫 번째 스트림 연결 실패. 대체 스트림 시도 중...";
              statusMessage.className = "stream-status status-loading"; // 로딩 상태 스타일 유지
              fallbackAttempted = true; // 대체 시도 플래그 설정

              // 대체 URL 로드 성공/실패에 대한 새로운 이벤트 리스너는 필요 없습니다.
              // 동일한 handleStreamLoad/handleStreamError 함수가 호출될 것입니다.
              // 단, 오류 발생 시 fallbackAttempted 플래그를 체크하여 무한 루프를 방지합니다.

          } else {
              // 이미 대체 URL을 시도했는데 또 오류가 발생했거나,
              // 첫 번째 URL 시도 중 플래그 설정 전에 다시 오류가 발생한 경우 (거의 발생하지 않음)
              console.error("두 번째 스트림도 실패:", streamImg.src);
              statusMessage.textContent = "스트림 연결 실패";
              statusMessage.className = "stream-status status-error"; // 오류 상태 스타일 적용
               // 최종 실패 후에는 모든 이벤트 리스너 제거
               streamImg.removeEventListener('load', handleStreamLoad);
               streamImg.removeEventListener('error', handleStreamError);
          }
      };

      // 이벤트 리스너 추가
      // DOMContentLoaded 내부에서 추가하면 요소가 존재함을 보장
      streamImg.addEventListener('load', handleStreamLoad);
      streamImg.addEventListener('error', handleStreamError);

      // Initial check in case the image loads from cache immediately
      // 이미지 로드가 이미 완료된 경우 (캐시 등) handleStreamLoad를 수동으로 호출
      // complete 속성은 이미지 로드가 완료되었는지, naturalHeight는 이미지 크기가 유효한지 확인
      if (streamImg.complete && streamImg.naturalHeight !== 0) {
           handleStreamLoad();
      } else {
           // If not complete, the error or load event will fire
           // Ensure initial status is set
           statusMessage.textContent = "스트림 연결 중...";
           statusMessage.className = "stream-status status-loading";
      }

  } else {
      console.error("Error: Image or status message element not found for stream loading.");
      // Optionally set a status message if the elements are missing
      const container = document.querySelector('.card');
      if(container) {
          const errorDiv = document.createElement('div');
          errorDiv.textContent = "스트림 표시 요소를 찾을 수 없습니다.";
          errorDiv.style.color = 'red';
          errorDiv.style.textAlign = 'center';
          container.appendChild(errorDiv);
      }
  }


});

// 이 페이지에서는 차트가 사용되지 않으므로 관련 함수는 주석 처리하거나 제거
// function updateCharts(logs) { ... }
// function loadInitialCharts() { ... }
