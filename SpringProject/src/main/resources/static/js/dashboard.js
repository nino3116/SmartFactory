// static/js/dashboard.js

// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정)
const LATEST_DEFECTS_API_URL = "/api/latest-defects"; // 최신 불량 정보 API 엔드포인트
const CHART_DATA_API_URL = "/api/charts/data"; // 백엔드에서 가공된 차트 데이터 API 엔드포인트

// Chart.js 인스턴스를 저장할 전역 변수
let weekStatusChart = null;
let detectionStatusChart = null;
let yearStatusChart = null;
let dayStatusChart = null; // 당일 감지 비율 차트 변수
let dailyTaskCompletionChart = null; // 당일 작업 달성률 차트 변수 추가

// Chart.js 플러그인: 데이터가 없을 때 텍스트 표시 (파이 차트 및 스택 막대 차트용)
const noDataTextPlugin = {
	id: "noDataText",
	beforeDraw: function (chart) {
		const dataset = chart.data && chart.data.datasets && chart.data.datasets[0];

		// 데이터셋이 없거나, 데이터가 비어있거나, 모든 값이 0인 경우
		// 스택 막대 차트의 경우 모든 데이터셋의 합이 0인지 확인
		let totalSum = 0;
		if (chart.data && chart.data.datasets) {
			chart.data.datasets.forEach((ds) => {
				if (ds.data) {
					// 스택 막대 차트의 경우, 각 데이터셋의 첫 번째 데이터 포인트만 합산 (단일 라벨이므로)
					if (chart.options.indexAxis === "y" && ds.data.length > 0) {
						totalSum += ds.data[0];
					} else {
						// 다른 차트 유형은 모든 데이터 합산
						totalSum += ds.data.reduce((sum, val) => sum + val, 0);
					}
				}
			});
		}

		if (
			!dataset ||
			!dataset.data ||
			dataset.data.length === 0 ||
			totalSum === 0 // 모든 데이터셋의 합이 0인지 확인
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
		console.error(
			"Error: Element with id 'defect-table tbody' not found for latest defects table.",
		);
		return; // 요소를 찾지 못하면 함수 종료
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

		const defects = await response.json(); // JSON 응답 파싱 (List<DefectInfo>)

		defectTableBody.innerHTML = ""; // 기존 내용을 모두 지웁니다.

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
				defectTableBody.appendChild(row); // 새로운 행 추가
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
		// 오류 발생 시 메시지 표시
		defectTableBody.innerHTML = `
       <tr>
        <td colspan="6" class="py-4 px-4 text-center text-red-500">
         오류 발생: ${error.message}
        </td>
       </tr>
      `;
	}
}

// 백엔드에서 가공된 차트 데이터를 가져와서 차트를 업데이트하는 함수
async function fetchAndDisplayCharts() {
	// 총 작업량 입력 필드 가져오기
	const totalTasksInput = document.getElementById("totalTasks");
	// 입력값이 없거나 유효하지 않으면 0으로 처리
	const totalTasks =
		parseInt(totalTasksInput ? totalTasksInput.value : "0") || 0;

	// 백엔드 API 호출 시 totalTasks 값을 쿼리 파라미터로 전달
	const apiUrl = `${CHART_DATA_API_URL}?totalTasks=${totalTasks}`;
	console.log(`Fetching chart data from: ${apiUrl}`); // API 호출 URL 로깅

	try {
		const response = await fetch(apiUrl);

		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}

		const chartData = await response.json(); // 백엔드에서 가공된 JSON 데이터 파싱
		console.log("Received chart data:", chartData); // 수신된 데이터 로깅

		// 백엔드에서 받은 데이터를 사용하여 각 차트 업데이트 또는 새로 생성

		// 1. 전체 감지 상태 비율 차트 (파이 차트)
		const ctx1 = document
			.getElementById("detectionStatusChart")
			.getContext("2d");
		if (!ctx1) {
			console.error(
				"Error: Canvas element with id 'detectionStatusChart' not found.",
			);
		} else {
			const data = chartData.overallStatus;
			if (detectionStatusChart) {
				detectionStatusChart.data.labels = data.labels;
				detectionStatusChart.data.datasets[0].data = data.data;
				detectionStatusChart.update();
			} else {
				detectionStatusChart = new Chart(ctx1, {
					type: "pie",
					data: {
						labels: data.labels,
						datasets: [
							{
								label: "감지 상태",
								data: data.data,
								backgroundColor: [
									"rgba(75, 192, 192, 0.5)", // Normal (Greenish)
									"rgba(255, 99, 132, 0.5)", // Defect Detected (Reddish)
								],
								borderColor: ["rgba(75, 192, 192, 1)", "rgba(255, 99, 132, 1)"],
								borderWidth: 1,
							},
						],
					},
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
							datalabels: {
								formatter: (value, ctx) => {
									const total = ctx.chart.data.datasets[0].data.reduce(
										(sum, val) => sum + val,
										0,
									);
									if (total === 0) return "";
									const percentage = `${((value / total) * 100).toFixed(1)}%`;
									return percentage;
								},
								color: "#fff",
								textShadowBlur: 4,
								textShadowColor: "rgba(0, 0, 0, 0.5)",
								font: { size: 20, weight: "bold" },
							},
							tooltip: {
								callbacks: {
									label: function (tooltipItem) {
										const dataset = tooltipItem.dataset;
										const total = dataset.data.reduce(
											(sum, val) => sum + val,
											0,
										);
										const currentValue = dataset.data[tooltipItem.dataIndex];
										const percentage =
											total === 0
												? "0.0"
												: ((currentValue / total) * 100).toFixed(1);
										return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
									},
								},
							},
						},
					},
					plugins: [noDataTextPlugin, ChartDataLabels],
				});
			}
		}

		// 2. 주간 불량 감지 추이 차트 (막대 차트)
		const ctx2 = document.getElementById("weekStatusChart").getContext("2d");
		if (!ctx2) {
			console.error(
				"Error: Canvas element with id 'weekStatusChart' not found.",
			);
		} else {
			const data = chartData.weeklyDefectTrend;
			if (weekStatusChart) {
				weekStatusChart.data.labels = data.labels;
				weekStatusChart.data.datasets[0].data = data.data;
				weekStatusChart.update();
			} else {
				weekStatusChart = new Chart(ctx2, {
					type: "bar",
					data: {
						labels: data.labels,
						datasets: [
							{
								label: "불량 개수",
								data: data.data,
								backgroundColor: "rgba(54, 162, 235, 0.5)",
								borderColor: "rgba(54, 162, 235, 1)",
								borderWidth: 1,
								datalabels: {
									display: true,
									color: "black",
									anchor: "end",
									align: "top",
									formatter: function (value, context) {
										return value + "개";
									},
									font: { size: 12, weight: "bold" },
								},
							},
						],
					},
					options: {
						responsive: true,
						maintainAspectRatio: false,
						scales: {
							y: {
								beginAtZero: true,
								title: { display: true, text: "불량 개수" },
								ticks: {
									callback: function (value) {
										if (Number.isInteger(value)) {
											return value;
										}
										return null;
									},
								},
							},
							x: { title: { display: true, text: "날짜" } },
						},
						layout: { padding: 40 },
						plugins: {
							legend: { display: true, position: "top" },
							title: { display: true, text: "주간 불량 감지 추이 (개수)" },
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
					plugins: [ChartDataLabels],
				});
			}
		}

		// 3. 연간 불량 감지 추이 차트 (선형 차트)
		const ctx3 = document.getElementById("yearStatusChart").getContext("2d");
		if (!ctx3) {
			console.error(
				"Error: Canvas element with id 'yearStatusChart' not found.",
			);
		} else {
			const data = chartData.yearlyDefectTrend;
			if (yearStatusChart) {
				yearStatusChart.data.labels = data.labels;
				yearStatusChart.data.datasets[0].data = data.data;
				yearStatusChart.update();
			} else {
				yearStatusChart = new Chart(ctx3, {
					type: "line",
					data: {
						labels: data.labels,
						datasets: [
							{
								label: "불량 개수",
								data: data.data,
								fill: false,
								borderColor: "rgba(255, 159, 64, 1)",
								tension: 0.1,
								datalabels: {
									display: true,
									color: "black",
									anchor: "end",
									align: "top",
									formatter: function (value, context) {
										return value + "개";
									},
									font: { size: 12, weight: "bold" },
								},
							},
						],
					},
					options: {
						responsive: true,
						maintainAspectRatio: false,
						scales: {
							y: {
								beginAtZero: true,
								title: { display: true, text: "불량 개수" },
								ticks: {
									callback: function (value) {
										if (Number.isInteger(value)) {
											return value;
										}
										return null;
									},
								},
							},
							x: { title: { display: true, text: "월" } },
						},
						layout: { padding: 40 },
						plugins: {
							legend: { display: true, position: "top" },
							title: { display: true, text: "연간 불량 감지 추이 (개수)" },
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
					plugins: [ChartDataLabels],
				});
			}
		}

		// 4. 당일 감지 상태 비율 차트 (파이 차트)
		const ctx4 = document.getElementById("dayStatusChart").getContext("2d");
		if (!ctx4) {
			console.error(
				"Error: Canvas element with id 'dayStatusChart' not found.",
			);
		} else {
			const data = chartData.dailyStatus;
			if (dayStatusChart) {
				dayStatusChart.data.labels = data.labels;
				dayStatusChart.data.datasets[0].data = data.data;
				dayStatusChart.update();
			} else {
				dayStatusChart = new Chart(ctx4, {
					type: "pie",
					data: {
						labels: data.labels,
						datasets: [
							{
								label: "감지 상태",
								data: data.data,
								backgroundColor: [
									"rgba(75, 192, 192, 0.5)", // Normal (Greenish)
									"rgba(255, 99, 132, 0.5)", // Defect Detected (Reddish)
								],
								borderColor: ["rgba(75, 192, 192, 1)", "rgba(255, 99, 132, 1)"],
								borderWidth: 1,
							},
						],
					},
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
							datalabels: {
								formatter: (value, ctx) => {
									const total = ctx.chart.data.datasets[0].data.reduce(
										(sum, val) => sum + val,
										0,
									);
									if (total === 0) return "";
									const percentage = `${((value / total) * 100).toFixed(1)}%`;
									return percentage;
								},
								color: "#fff",
								textShadowBlur: 4,
								textShadowColor: "rgba(0, 0, 0, 0.5)",
								font: { size: 28, weight: "bold" },
							},
							tooltip: {
								callbacks: {
									label: function (tooltipItem) {
										const dataset = tooltipItem.dataset;
										const total = dataset.data.reduce(
											(sum, val) => sum + val,
											0,
										);
										const currentValue = dataset.data[tooltipItem.dataIndex];
										const percentage =
											total === 0
												? "0.0"
												: ((currentValue / total) * 100).toFixed(1);
										return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
									},
								},
							},
						},
					},
					plugins: [noDataTextPlugin, ChartDataLabels],
				});
			}
		}

		// 5. 당일 작업 완료/미완료 차트 (스택 가로 막대 차트)
		const ctx5 = document
			.getElementById("dailyTaskCompletionChart")
			.getContext("2d");
		if (!ctx5) {
			console.error(
				"Error: Canvas element with id 'dailyTaskCompletionChart' not found.",
			);
		} else {
			const data = chartData.dailyTaskCompletion; // 백엔드에서 받은 당일 작업 완료/미완료 데이터
			const totalTasksForChart =
				parseInt(document.getElementById("totalTasks")?.value || "0") || 0; // 현재 총 작업량 값

			// 백엔드에서 유효한 데이터가 왔는지 확인 (labels와 datasets가 모두 있어야 함)
			if (data && data.labels && data.datasets) {
				// X축 최대값을 총 작업량으로 설정 (총 작업량이 0이면 최소 1)
				const xAxisMax = totalTasksForChart > 0 ? totalTasksForChart : 1;

				if (dailyTaskCompletionChart) {
					// 차트 데이터 및 옵션 업데이트
					dailyTaskCompletionChart.data.labels = data.labels;
					dailyTaskCompletionChart.data.datasets = data.datasets; // 데이터셋 전체를 업데이트
					dailyTaskCompletionChart.options.scales.x.max = xAxisMax; // X축 max 값 업데이트
					dailyTaskCompletionChart.update();
					console.log("dailyTaskCompletionChart 업데이트 완료", {
						data: dailyTaskCompletionChart.data,
						options: dailyTaskCompletionChart.options,
					});
				} else {
					// 차트 새로 생성
					dailyTaskCompletionChart = new Chart(ctx5, {
						type: "bar",
						data: {
							labels: data.labels,
							datasets: data.datasets, // 백엔드에서 받은 datasets 그대로 사용
						},
						options: {
							responsive: true,
							maintainAspectRatio: false,
							indexAxis: "y", // 가로 막대
							scales: {
								x: {
									stacked: true,
									beginAtZero: true,
									title: { display: true, text: "개수" },
									max: xAxisMax, // X축 최대값을 총 작업량으로 설정
									ticks: {
										callback: function (value) {
											if (Number.isInteger(value)) {
												return value;
											}
											return null;
										},
									},
								},
								y: {
									stacked: true,
									ticks: { display: false }, // Y축 라벨 숨김
									grid: { display: false }, // Y축 그리드 라인 숨김
									border: { display: false }, // Y축 경계선 숨김
								},
							},
							layout: { padding: 10 },
							plugins: {
								legend: { position: "top" },
								title: { display: true, text: "당일 작업 완료/미완료" },
								datalabels: {
									display: true,
									color: "#fff",
									textShadowBlur: 4,
									textShadowColor: "rgba(0, 0, 0, 0.5)",
									formatter: function (value, context) {
										return value > 0 ? value + "개" : "";
									},
									font: { size: 16, weight: "bold" },
									anchor: "center",
									align: "center",
								},
								tooltip: {
									callbacks: {
										label: function (tooltipItem) {
											const datasetLabel = tooltipItem.dataset.label || "";
											const value = tooltipItem.raw;
											return `${datasetLabel}: ${value} 개`;
										},
									},
								},
							},
						},
						plugins: [ChartDataLabels, noDataTextPlugin], // noDataText 플러그인 추가
					});
					console.log("dailyTaskCompletionChart 새로 생성 완료");
				}
			} else {
				console.warn(
					"백엔드로부터 당일 작업 완료/미완료 차트 데이터가 유효하지 않습니다.",
					data,
				);
				// 유효하지 않은 데이터 수신 시 차트를 초기화하거나 메시지 표시 로직 추가 가능
				if (dailyTaskCompletionChart) {
					dailyTaskCompletionChart.destroy();
					dailyTaskCompletionChart = null;
					// 또는 캔버스에 "데이터 없음" 메시지를 직접 표시
				}
			}
		}
	} catch (error) {
		console.error("차트 데이터를 가져오는 중 오류 발생:", error);
		// 오류 발생 시 차트를 초기화하거나 "데이터 없음" 메시지를 표시할 수 있습니다.
		// 필요하다면 각 차트 컨텍스트를 가져와서 차트를 파괴하고 메시지를 표시하는 로직을 추가하세요.
		// 예: dailyTaskCompletionChart가 존재하면 파괴
		if (dailyTaskCompletionChart) {
			dailyTaskCompletionChart.destroy();
			dailyTaskCompletionChart = null;
		}
	}
}

// 브라우저 창 크기 변경 시 차트 크기 조절
window.addEventListener("resize", () => {
	console.log("Window resized. Resizing charts...");
	if (weekStatusChart) {
		weekStatusChart.resize();
	}
	if (detectionStatusChart) {
		detectionStatusChart.resize();
	}
	if (yearStatusChart) {
		yearStatusChart.resize();
	}
	if (dayStatusChart) {
		dayStatusChart.resize();
	}
	if (dailyTaskCompletionChart) {
		dailyTaskCompletionChart.resize();
	}
});

// 페이지 로드 시 및 주기적으로 불량 정보 및 차트 데이터 가져오기
document.addEventListener("DOMContentLoaded", () => {
	// Chart.js DataLabels 플러그인 등록 (CDN에서 로드하므로 여기서 다시 등록할 필요 없음)
	if (typeof ChartDataLabels !== "undefined") {
		console.log("ChartDataLabels plugin is available.");
	} else {
		console.warn(
			"ChartDataLabels plugin not found. Make sure the CDN is loaded correctly.",
		);
	}

	// 총 작업량 입력 필드 가져오기
	const totalTasksInput = document.getElementById("totalTasks");

	// --- Local Storage에서 총 작업량 값 불러오기 ---
	if (totalTasksInput) {
		const savedTotalTasks = localStorage.getItem("totalTasksValue");
		// 저장된 값이 있고 유효한 숫자인지 확인
		if (
			savedTotalTasks !== null &&
			!isNaN(parseInt(savedTotalTasks)) &&
			parseInt(savedTotalTasks) >= 0
		) {
			// 저장된 값이 있다면 입력 필드에 설정
			totalTasksInput.value = savedTotalTasks;
			console.log(`Local Storage에서 총 작업량 값 불러옴: ${savedTotalTasks}`);
		} else {
			console.log(
				"Local Storage에 저장된 총 작업량 값이 없거나 유효하지 않습니다. 기본값 0 사용.",
			);
			totalTasksInput.value = "0"; // 기본값 설정
			localStorage.setItem("totalTasksValue", "0"); // 유효하지 않으면 0으로 저장
		}

		// --- 총 작업량 입력 필드 값이 변경될 때마다 Local Storage에 저장 및 차트 업데이트 ---
		totalTasksInput.addEventListener("input", () => {
			const currentValue = totalTasksInput.value;
			const parsedValue = parseInt(currentValue);
			// 입력 값이 유효한 숫자인 경우에만 저장 및 차트 업데이트
			if (!isNaN(parsedValue) && parsedValue >= 0) {
				localStorage.setItem("totalTasksValue", parsedValue.toString()); // 숫자로 변환 후 문자열로 저장
				console.log(
					`총 작업량 값 변경 감지 및 Local Storage에 저장: ${parsedValue}`,
				);
				// 입력 값이 변경될 때마다 차트 데이터 다시 가져오기 및 업데이트
				fetchAndDisplayCharts();
			} else {
				console.warn("유효하지 않은 총 작업량 입력 값:", currentValue);
				// 유효하지 않은 입력에 대한 처리 (예: 경고 메시지 표시 또는 이전 유효 값 유지)
				// 여기서는 콘솔 경고만 표시하고 Local Storage에는 저장하지 않습니다.
				// 필요하다면 사용자에게 피드백을 주는 UI를 추가할 수 있습니다.
			}
		});
	} else {
		console.error("Error: Element with id 'totalTasks' not found.");
	}

	fetchAndDisplayLatestDefects(); // 페이지 로드 시 최신 불량 정보 즉시 가져오기
	fetchAndDisplayCharts(); // 페이지 로드 시 차트 데이터 즉시 가져오기

	// 5초마다 불량 정보 업데이트 (주기는 필요에 따라 조정)
	setInterval(fetchAndDisplayLatestDefects, 5000);
	// 10초마다 차트 데이터 업데이트 (주기는 필요에 따라 조정)
	setInterval(fetchAndDisplayCharts, 10000);
});
