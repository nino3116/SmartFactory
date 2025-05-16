// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정)
const LATEST_DEFECTS_API_URL = "/api/latest-defects"; // 최신 불량 정보 API 엔드포인트
const DETECTION_LOGS_API_URL = "/api/detection-logs"; // 감지 로그 API 엔드포인트

// Chart.js 인스턴스를 저장할 전역 변수
let weekStatusChart = null;
let detectionStatusChart = null;
let yearStatusChart = null;
let dayStatusChart = null; // 당일 감지 비율 차트 변수

// Chart.js 플러그인: 데이터가 없을 때 텍스트 표시 (파이 차트용)
const noDataTextPlugin = {
	id: "noDataText",
	beforeDraw: function (chart) {
		const dataset = chart.data && chart.data.datasets && chart.data.datasets[0];

		// 데이터셋이 없거나, 데이터가 비어있거나, 모든 값이 0인 경우
		if (
			!dataset ||
			!dataset.data ||
			dataset.data.length === 0 ||
			dataset.data.every((value) => value === 0)
		) {
			const ctx = chart.ctx;
			const width = chart.width;
			const height = chart.height;
			chart.clear();

			ctx.save();
			ctx.textAlign = "center";
			ctx.textBaseline = "middle";
			ctx.font = "16px sans-serif";
			ctx.fillStyle = "#6b7280"; // Tailwind gray-500

			const centerX = width / 2;
			const centerY = height / 2;

			ctx.fillText("데이터 없음", centerX, centerY);
			ctx.restore();
		}
	},
};

// 불량 정보를 가져와서 화면에 표시하는 함수 (최신 불량 결과 테이블)
// 이 함수는 차트 데이터에 직접 사용되지 않고 테이블만 업데이트합니다.
async function fetchAndDisplayLatestDefects() {
	const defectTableBody = document.querySelector("#defect-table tbody");
	if (!defectTableBody) {
		console.error("Error: Element with id 'defect-table tbody' not found.");
		return;
	}

	// 로딩 메시지 표시
	defectTableBody.innerHTML = `
            <tr>
              <td colspan="6" class="py-4 px-4 text-center text-gray-500">
                불량 정보 가져오는 중...
              </td>
          </tr>
        `;

	try {
		const response = await fetch(LATEST_DEFECTS_API_URL);

		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}

		const defects = await response.json(); // List<DefectInfo> 객체

		const newTbody = document.createElement("tbody");
		newTbody.id = "defect-table-body"; // ID 유지

		if (defects && defects.length > 0) {
			defects.forEach((defect) => {
				const row = document.createElement("tr");
				row.classList.add("hover:bg-gray-50");

				// 데이터가 없을 경우 "-" 또는 기본값 표시
				const clazz = defect.clazz || "-";
				const reason = defect.reason || "-";
				const confidence =
					defect.confidence !== undefined && defect.confidence !== null
						? defect.confidence.toFixed(2)
						: "-";
				const box =
					defect.box && defect.box.length === 4
						? `[${defect.box.map((coord) => coord.toFixed(2)).join(", ")}]`
						: "-";
				const areaPercent =
					defect.areaPercentOnApple !== undefined &&
					defect.areaPercentOnApple !== null
						? defect.areaPercentOnApple.toFixed(2) + "%"
						: "-";
				const imageUrl = defect.imageUrl || ""; // 이미지 URL이 없을 경우 빈 문자열

				row.innerHTML = `
                    <td class="py-2 px-4 border-b">${clazz}</td>
                    <td class="py-2 px-4 border-b">${reason}</td>
                    <td class="py-2 px-4 border-b">${confidence}</td>
                    <td class="py-2 px-4 border-b">${box}</td>
                    <td class="py-2 px-4 border-b">${areaPercent}</td>
                    <td class="py-2 px-4 border-b text-center">
                      ${
												imageUrl
													? `<img src="${imageUrl}" alt="Defect Snapshot" class="defect-image" onerror="this.onerror=null;this.src='https://placehold.co/100x100/e0e0e0/6b7280?text=No+Image';">`
													: "이미지 없음"
											}
                    </td>
                `;
				newTbody.appendChild(row);
			});
			// 기존 tbody를 새로운 tbody로 교체
			defectTableBody.parentNode.replaceChild(newTbody, defectTableBody);
		} else {
			// 불량 정보가 없으면 메시지 표시
			newTbody.innerHTML = `
                <tr>
                  <td colspan="6" class="py-4 px-4 text-center text-gray-500">
                    감지된 불량이 없습니다.
                  </td>
                </tr>
              `;
			defectTableBody.parentNode.replaceChild(newTbody, defectTableBody);
		}
	} catch (error) {
		console.error("불량 정보를 가져오는 중 오류 발생:", error);
		// 오류 발생 시 메시지 표시
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

// 감지 로그 데이터를 가져와서 차트를 업데이트하는 함수
async function updateChartsFromLogs() {
	try {
		const response = await fetch(DETECTION_LOGS_API_URL); // 감지 로그 API 호출

		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}

		const logs = await response.json(); // List<DetectionLog> 객체

		// 데이터가 없거나 비어있는 경우를 처리
		const hasData = logs && logs.length > 0;

		// 1. 전체 감지 상태 비율 차트 데이터 가공 (파이 차트)
		let detectionStatusData;
		if (hasData) {
			const statusCounts = logs.reduce((acc, log) => {
				acc[log.status] = (acc[log.status] || 0) + 1;
				return acc;
			}, {});

			// 항상 'Normal'과 'Defect Detected' 라벨을 포함하고 데이터가 없으면 0으로 설정
			const labels = ["Normal", "Defect Detected"];
			const data = [
				statusCounts["Normal"] || 0,
				statusCounts["Defect Detected"] || 0,
			];

			detectionStatusData = {
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
			// 데이터가 없을 때
			detectionStatusData = {
				labels: ["Normal", "Defect Detected"],
				datasets: [
					{
						label: "감지 상태",
						data: [0, 0],
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

		// 2. 주간 불량 감지 추이 차트 데이터 가공 (막대 차트 - 최근 7일)
		const sevenDaysAgo = new Date();
		sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6); // 오늘 포함 최근 7일
		sevenDaysAgo.setHours(0, 0, 0, 0);

		let weekStatusData;
		const dailyDefectCounts = {};
		const dateLabels = [];
		const defectCountsData = [];

		// 최근 7일간의 모든 날짜 초기화
		for (let i = 6; i >= 0; i--) {
			const date = new Date();
			date.setDate(date.getDate() - i);
			date.setHours(0, 0, 0, 0);
			const dateString = `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`;
			dailyDefectCounts[dateString] = 0; // 해당 날짜의 불량 개수 0으로 초기화
			dateLabels.push(`${date.getMonth() + 1}-${date.getDate()}`); // 라벨은 'MM-DD' 형식
		}

		if (hasData) {
			logs.forEach((log) => {
				const logDate = new Date(log.detectionTime);
				logDate.setHours(0, 0, 0, 0);
				const dateString = `${logDate.getFullYear()}-${(logDate.getMonth() + 1).toString().padStart(2, "0")}-${logDate.getDate().toString().padStart(2, "0")}`;

				// 최근 7일 이내의 'Defect Detected' 로그만 불량 개수에 포함
				if (logDate >= sevenDaysAgo && log.status === "Defect Detected") {
					dailyDefectCounts[dateString] =
						(dailyDefectCounts[dateString] || 0) + (log.defectCount || 0);
				}
			});
		}

		// 초기화된 dailyDefectCounts에서 데이터 추출 (날짜 순서대로)
		for (let i = 6; i >= 0; i--) {
			const date = new Date();
			date.setDate(date.getDate() - i);
			date.setHours(0, 0, 0, 0);
			const dateString = `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`;
			defectCountsData.push(dailyDefectCounts[dateString]);
		}

		weekStatusData = {
			labels: dateLabels, // 'MM-DD' 형식의 날짜 라벨 (최근 7일)
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

		// 3. 연간 불량 감지 추이 차트 데이터 가공 (선형 차트 - 최근 12개월)
		const twelveMonthsAgo = new Date();
		twelveMonthsAgo.setMonth(twelveMonthsAgo.getMonth() - 11); // 현재 월 포함 12개월
		twelveMonthsAgo.setDate(1); // 해당 월의 1일로 설정
		twelveMonthsAgo.setHours(0, 0, 0, 0);

		let yearStatusData;
		const monthlyDefectCounts = {};
		const monthLabels = [];
		const monthlyData = [];

		// 최근 12개월의 모든 월 초기화
		for (let i = 11; i >= 0; i--) {
			const date = new Date();
			date.setMonth(date.getMonth() - i);
			const yearMonthString = `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, "0")}`;
			monthlyDefectCounts[yearMonthString] = 0; // 해당 월의 불량 개수 0으로 초기화
			monthLabels.push(yearMonthString); // 라벨은 'YYYY-MM' 형식
		}

		if (hasData) {
			logs.forEach((log) => {
				const logDate = new Date(log.detectionTime);
				logDate.setDate(1);
				logDate.setHours(0, 0, 0, 0);
				const yearMonthString = `${logDate.getFullYear()}-${(logDate.getMonth() + 1).toString().padStart(2, "0")}`;

				// 최근 12개월 이내의 'Defect Detected' 로그만 불량 개수에 포함
				if (logDate >= twelveMonthsAgo && log.status === "Defect Detected") {
					monthlyDefectCounts[yearMonthString] =
						(monthlyDefectCounts[yearMonthString] || 0) +
						(log.defectCount || 0);
				}
			});
		}

		// 초기화된 monthlyDefectCounts에서 데이터 추출 (월 순서대로)
		for (let i = 11; i >= 0; i--) {
			const date = new Date();
			date.setMonth(date.getMonth() - i);
			const yearMonthString = `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, "0")}`;
			monthlyData.push(monthlyDefectCounts[yearMonthString]);
		}

		yearStatusData = {
			labels: monthLabels, // 'YYYY-MM' 형식의 월 라벨 (최근 12개월)
			datasets: [
				{
					label: "불량 개수",
					data: monthlyData, // 월별 불량 개수
					fill: false,
					borderColor: "rgba(255, 159, 64, 1)", // 주황색
					tension: 0.1,
				},
			],
		};

		// 4. 당일 감지 상태 비율 차트 데이터 가공 (파이 차트)
		let dayStatusData;
		const today = new Date();
		today.setHours(0, 0, 0, 0); // 오늘 날짜의 시작 시간

		if (hasData) {
			const todayLogs = logs.filter((log) => {
				try {
					const logDate = new Date(log.detectionTime);
					const logDateOnly = new Date(
						logDate.getFullYear(),
						logDate.getMonth(),
						logDate.getDate(),
					);
					return logDateOnly.getTime() === today.getTime();
				} catch (e) {
					console.error(
						"Error parsing log date for today's chart:",
						log.detectionTime,
						e,
					);
					return false;
				}
			});

			const todayStatusCounts = todayLogs.reduce((acc, log) => {
				acc[log.status] = (acc[log.status] || 0) + 1;
				return acc;
			}, {});

			// 항상 'Normal'과 'Defect Detected' 라벨을 포함하고 데이터가 없으면 0으로 설정
			const labels = ["Normal", "Defect Detected"];
			const data = [
				todayStatusCounts["Normal"] || 0,
				todayStatusCounts["Defect Detected"] || 0,
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
			// 데이터가 없을 때
			dayStatusData = {
				labels: ["Normal", "Defect Detected"],
				datasets: [
					{
						label: "감지 상태",
						data: [0, 0],
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
		// Overall Detection Status Chart (Pie Chart)
		const ctx1 = document
			.getElementById("detectionStatusChart")
			.getContext("2d");
		if (detectionStatusChart) {
			detectionStatusChart.data = detectionStatusData;
			detectionStatusChart.update();
		} else {
			detectionStatusChart = new Chart(ctx1, {
				type: "pie",
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
						// DataLabels 플러그인 설정 (차트 내부에 백분율 표시)
						datalabels: {
							formatter: (value, ctx) => {
								const total = ctx.chart.data.datasets[0].data.reduce(
									(sum, val) => sum + val,
									0,
								);
								if (total === 0) return ""; // 데이터가 없으면 빈 문자열 반환
								const percentage = `${((value / total) * 100).toFixed(1)}%`; // 소수점 첫째 자리까지 표시
								return percentage;
							},
							color: "#fff", // 라벨 색상
							textShadowBlur: 4,
							textShadowColor: "rgba(0, 0, 0, 0.5)",
						},
						// Tooltip 설정
						tooltip: {
							callbacks: {
								label: function (tooltipItem) {
									const dataset = tooltipItem.dataset;
									const total = dataset.data.reduce((sum, val) => sum + val, 0);
									const currentValue = dataset.data[tooltipItem.dataIndex];
									const percentage =
										total === 0
											? "0.0"
											: ((currentValue / total) * 100).toFixed(1); // 소수점 첫째 자리까지 표시
									return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
								},
							},
						},
					},
				},
				plugins: [noDataTextPlugin], // noDataText 플러그인 등록
			});
		}

		// Week Status Chart (Bar Chart)
		const ctx2 = document.getElementById("weekStatusChart").getContext("2d");
		if (weekStatusChart) {
			weekStatusChart.data = weekStatusData;
			weekStatusChart.update();
		} else {
			weekStatusChart = new Chart(ctx2, {
				type: "bar",
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
							// Y축 눈금을 정수로만 표시
							ticks: {
								callback: function (value) {
									if (Number.isInteger(value)) {
										return value;
									}
								},
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
						tooltip: {
							callbacks: {
								label: function (tooltipItem) {
									const dataset = tooltipItem.dataset;
									const currentValue = dataset.data[tooltipItem.dataIndex];
									return `${tooltipItem.label}: ${currentValue} 개`;
								},
							},
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
				type: "line",
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
							// Y축 눈금을 정수로만 표시
							ticks: {
								callback: function (value) {
									if (Number.isInteger(value)) {
										return value;
									}
								},
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
						tooltip: {
							callbacks: {
								label: function (tooltipItem) {
									const dataset = tooltipItem.dataset;
									const currentValue = dataset.data[tooltipItem.dataIndex];
									return `${tooltipItem.label}: ${currentValue} 개`;
								},
							},
						},
					},
				},
			});
		}

		// Day Status Chart (Pie Chart)
		const ctx4 = document.getElementById("dayStatusChart").getContext("2d");
		if (dayStatusChart) {
			dayStatusChart.data = dayStatusData;
			dayStatusChart.update();
		} else {
			dayStatusChart = new Chart(ctx4, {
				type: "pie",
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
						// DataLabels 플러그인 설정 (차트 내부에 백분율 표시)
						datalabels: {
							formatter: (value, ctx) => {
								const total = ctx.chart.data.datasets[0].data.reduce(
									(sum, val) => sum + val,
									0,
								);
								if (total === 0) return ""; // 데이터가 없으면 빈 문자열 반환
								const percentage = `${((value / total) * 100).toFixed(1)}%`; // 소수점 첫째 자리까지 표시
								return percentage;
							},
							color: "#fff", // 라벨 색상
							textShadowBlur: 4,
							textShadowColor: "rgba(0, 0, 0, 0.5)",
						},
						// Tooltip 설정
						tooltip: {
							callbacks: {
								label: function (tooltipItem) {
									const dataset = tooltipItem.dataset;
									const total = dataset.data.reduce((sum, val) => sum + val, 0);
									const currentValue = dataset.data[tooltipItem.dataIndex];
									const percentage =
										total === 0
											? "0.0"
											: ((currentValue / total) * 100).toFixed(1); // 소수점 첫째 자리까지 표시
									return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
								},
							},
						},
					},
				},
				plugins: [noDataTextPlugin], // noDataText 플러그인 등록
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

// 페이지 로드 시 및 주기적으로 데이터 가져오기
document.addEventListener("DOMContentLoaded", () => {
	// Chart.js DataLabels 플러그인 등록
	// CDN에서 로드하므로 별도의 import/require는 필요 없지만,
	// Chart.plugins.register() 또는 Chart.register()를 사용하여 명시적으로 등록해야 합니다.
	// Chart.js v3+에서는 Chart.register()를 사용합니다.
	if (typeof ChartDataLabels !== "undefined") {
		Chart.register(ChartDataLabels);
		console.log("ChartDataLabels plugin registered.");
	} else {
		console.warn(
			"ChartDataLabels plugin not found. Make sure the CDN is loaded correctly.",
		);
	}

	fetchAndDisplayLatestDefects(); // 페이지 로드 시 최신 불량 정보 즉시 가져오기
	updateChartsFromLogs(); // 페이지 로드 시 감지 로그 즉시 가져오기 (차트 업데이트 포함)

	// 5초마다 불량 정보 업데이트 (주기는 필요에 따라 조정)
	setInterval(fetchAndDisplayLatestDefects, 5000);
	// 10초마다 감지 로그 업데이트 (주기는 필요에 따라 조정) - 차트 업데이트만 수행
	setInterval(updateChartsFromLogs, 10000);
});
