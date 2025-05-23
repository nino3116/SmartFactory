// static/js/dashboard.js

// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정)
const CHART_DATA_API_URL = "/api/charts/data"; // 백엔드에서 가공된 모든 차트 데이터 API 엔드포인트
const LATEST_DEFECTS_API_URL = "/api/latest-defects"; // 최신 불량 정보 API 엔드포인트
const SET_DAILY_TOTAL_TASKS_API_URL = "/api/progress/set-total"; // 당일 총 작업량 설정 API
const GET_DAILY_CURRENT_PROGRESS_API_URL = "/api/progress/daily-current"; // 당일 작업량 조회 API

// Chart.js 인스턴스를 저장할 전역 변수
let weekStatusChart = null;
let detectionStatusChart = null; // 전체 감지 상태 비율 차트
let yearStatusChart = null;
let dayStatusChart = null; // 당일 감지 비율 차트 변수 (불량 유형별)
let dailyTaskCompletionChart = null; // 당일 작업 달성률 차트 변수 (가로 막대)
let monthStatusChart = null; // 월간 차트 변수 추가

// HTML 요소 참조를 위한 전역 변수 (DOMContentLoaded에서 초기화)
let dailyTotalTasksInput = null;
let dailyProgressBar = null;
let dailyProgressPercentageSpan = null;
let defectTableBody = null;

// Chart.js 플러그인: 데이터가 없을 때 텍스트 표시 (파이 차트 및 스택 막대 차트용)
const noDataTextPlugin = {
	id: "noDataText",
	beforeDraw: function (chart) {
		const dataset = chart.data && chart.data.datasets && chart.data.datasets[0];

		let totalSum = 0;
		if (chart.data && chart.data.datasets) {
			chart.data.datasets.forEach((ds) => {
				if (ds.data) {
					totalSum += ds.data.reduce((sum, val) => sum + val, 0);
				}
			});
		}

		if (
			!dataset ||
			!dataset.data ||
			dataset.data.length === 0 ||
			totalSum === 0
		) {
			const ctx = chart.ctx;
			const width = chart.width;
			const height = chart.height;
			chart.clear();

			ctx.save();
			ctx.textAlign = "center";
			ctx.textBaseline = "middle";
			ctx.font = "16px 'Helvetica Neue', Helvetica, Arial, sans-serif";
			ctx.fillStyle = "#6b7280";

			const centerX = width / 2;
			const centerY = height / 2;

			ctx.fillText("데이터 없음", centerX, centerY);
			ctx.restore();
		}
	},
};

// Chart.js 플러그인 등록
Chart.register(noDataTextPlugin);

document.addEventListener("DOMContentLoaded", () => {
	// HTML 요소 초기화
	dailyTotalTasksInput = document.getElementById("totalTasks");
	dailyProgressBar = document.getElementById("daily-progress-bar");
	dailyProgressPercentageSpan = document.getElementById(
		"daily-progress-percentage",
	);
	defectTableBody = document.querySelector("#defect-table tbody");

	// 초기 데이터 로드 및 차트 표시
	fetchAndDisplayDailyProgress(); // 당일 작업량 및 달성률 (별도 API)
	fetchAndDisplayCharts(); // 모든 차트 데이터 (단일 API)
	fetchAndDisplayLatestDefects(); // 최신 불량 정보 테이블 (별도 API)

	// --- 당일 총 작업량 입력 필드 값이 변경될 때마다 백엔드에 저장 및 UI 업데이트 ---
	if (dailyTotalTasksInput) {
		// Local Storage에서 총 작업량 값 불러오기 (초기 로드 시만)
		const savedTotalTasks = localStorage.getItem("totalTasksValue");
		if (
			savedTotalTasks !== null &&
			!isNaN(parseInt(savedTotalTasks)) &&
			parseInt(savedTotalTasks) >= 0
		) {
			dailyTotalTasksInput.value = savedTotalTasks;
			console.log(`Local Storage에서 총 작업량 값 불러옴: ${savedTotalTasks}`);
		} else {
			console.log(
				"Local Storage에 저장된 총 작업량 값이 없거나 유효하지 않습니다. 기본값 0 사용.",
			);
			dailyTotalTasksInput.value = "0";
			localStorage.setItem("totalTasksValue", "0");
		}

		dailyTotalTasksInput.addEventListener("input", () => {
			const currentValue = parseInt(dailyTotalTasksInput.value);
			if (!isNaN(currentValue) && currentValue >= 0) {
				localStorage.setItem("totalTasksValue", currentValue.toString());
				setDailyTotalTasks(currentValue);
			} else {
				console.warn(
					"유효하지 않은 당일 총 작업량 입력 값:",
					dailyTotalTasksInput.value,
				);
			}
		});

		dailyTotalTasksInput.closest("form").addEventListener("submit", (event) => {
			event.preventDefault(); // 폼 기본 제출 방지
			const currentValue = parseInt(dailyTotalTasksInput.value);
			if (!isNaN(currentValue) && currentValue >= 0) {
				localStorage.setItem("totalTasksValue", currentValue.toString());
				setDailyTotalTasks(currentValue);
			}
		});
	}

	// 주기적인 업데이트
	setInterval(fetchAndDisplayLatestDefects, 5000);
	setInterval(fetchAndDisplayCharts, 10000);
	setInterval(fetchAndDisplayDailyProgress, 5000);
});

/**
 * 당일의 총 작업량과 완료된 작업량을 백엔드에 저장합니다.
 * @param {number} totalTasksValue 설정할 당일 총 작업량
 */
async function setDailyTotalTasks(totalTasksValue) {
	try {
		const response = await fetch(SET_DAILY_TOTAL_TASKS_API_URL, {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
			},
			body: JSON.stringify({ dailyTotalTasks: totalTasksValue }),
		});

		if (response.ok) {
			console.log("당일 총 작업량 성공적으로 저장됨.");
			fetchAndDisplayDailyProgress();
			fetchAndDisplayCharts();
		} else {
			const errorData = await response.json();
			console.error("당일 총 작업량 저장 실패:", errorData.message);
		}
	} catch (error) {
		console.error("당일 총 작업량 저장 중 오류 발생:", error);
	}
}

/**
 * 당일의 총 작업량과 완료된 작업량을 백엔드에서 가져와 UI를 업데이트합니다.
 */
async function fetchAndDisplayDailyProgress() {
	try {
		const response = await fetch(GET_DAILY_CURRENT_PROGRESS_API_URL);
		if (response.ok) {
			const data = await response.json();
			console.log("당일 작업량 데이터:", data);

			const dailyTotalTasks = data.dailyTotalTasks || 0;
			const completedTasks = data.completedTasks || 0;

			if (dailyTotalTasksInput) {
				dailyTotalTasksInput.value = dailyTotalTasks;
			}

			let percentage = 0;
			if (dailyTotalTasks > 0) {
				percentage = (completedTasks / dailyTotalTasks) * 100;
			}
			if (dailyProgressBar) {
				dailyProgressBar.style.width = `${percentage.toFixed(0)}%`;
			}
			if (dailyProgressPercentageSpan) {
				dailyProgressPercentageSpan.textContent = `${percentage.toFixed(0)}%`;
			}

			// 당일 작업 달성률 차트 업데이트
			updateDailyTaskCompletionChart(completedTasks, dailyTotalTasks);
		} else {
			console.error("당일 작업량 조회 실패:", response.statusText);
			if (dailyTotalTasksInput) dailyTotalTasksInput.value = "0";
			if (dailyProgressBar) dailyProgressBar.style.width = "0%";
			if (dailyProgressPercentageSpan)
				dailyProgressPercentageSpan.textContent = "0%";
			updateDailyTaskCompletionChart(0, 0);
		}
	} catch (error) {
		console.error("당일 작업량 조회 중 오류 발생:", error);
		if (dailyTotalTasksInput) dailyTotalTasksInput.value = "0";
		if (dailyProgressBar) dailyProgressBar.style.width = "0%";
		if (dailyProgressPercentageSpan)
			dailyProgressPercentageSpan.textContent = "0%";
		updateDailyTaskCompletionChart(0, 0);
	}
}

/**
 * 당일 작업 달성률 차트 (스택 가로 막대 차트)를 업데이트합니다.
 * @param {number} completed 완료된 작업량
 * @param {number} total 총 작업량
 */
function updateDailyTaskCompletionChart(completed, total) {
	const remaining = Math.max(0, total - completed);
	const ctx = document
		.getElementById("dailyTaskCompletionChart")
		?.getContext("2d");

	if (!ctx) {
		console.error(
			"Error: Canvas element with id 'dailyTaskCompletionChart' not found.",
		);
		return;
	}

	const xAxisMax = total > 0 ? total : 1;

	const datasets = [
		{
			label: "완료",
			data: [completed],
			backgroundColor: "rgba(75, 192, 192, 0.8)",
			borderColor: "rgba(75, 192, 192, 1)",
			borderWidth: 1,
			barPercentage: 0.8,
			categoryPercentage: 0.8,
		},
		{
			label: "미완료",
			data: [remaining],
			backgroundColor: "rgba(255, 99, 132, 0.8)",
			borderColor: "rgba(255, 99, 132, 1)",
			borderWidth: 1,
			barPercentage: 0.8,
			categoryPercentage: 0.8,
		},
	];

	if (dailyTaskCompletionChart) {
		dailyTaskCompletionChart.data.datasets = datasets;
		dailyTaskCompletionChart.options.scales.x.max = xAxisMax;
		dailyTaskCompletionChart.update();
		console.log("dailyTaskCompletionChart 업데이트 완료");
	} else {
		dailyTaskCompletionChart = new Chart(ctx, {
			type: "bar",
			data: {
				labels: ["오늘 작업"],
				datasets: datasets,
			},
			options: {
				responsive: true,
				maintainAspectRatio: false,
				indexAxis: "y",
				scales: {
					x: {
						stacked: true,
						beginAtZero: true,
						title: { display: true, text: "개수" },
						max: xAxisMax,
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
						ticks: { display: false },
						grid: { display: false },
						border: { display: false },
					},
				},
				layout: { padding: 10 },
				plugins: {
					legend: { position: "top" },
					title: { display: true, text: "당일 공정 진척상황" },
					datalabels: {
						display: true,
						color: "#fff",
						textShadowBlur: 4,
						textShadowColor: "rgba(0, 0, 0, 0.5)",
						formatter: function (value) {
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
					noDataText: {},
				},
			},
			plugins: [ChartDataLabels, noDataTextPlugin],
		});
		console.log("dailyTaskCompletionChart 새로 생성 완료");
	}
}

/**
 * 모든 차트 데이터를 백엔드에서 가져와서 각 차트를 업데이트합니다.
 * 이 함수는 `/api/charts/data` 엔드포인트를 사용합니다.
 */
async function fetchAndDisplayCharts() {
	// 현재 총 작업량 입력 필드 값 가져오기 (dailyTotalTasksInput이 DOMContentLoaded에서 초기화됨)
	const totalTasks =
		parseInt(dailyTotalTasksInput ? dailyTotalTasksInput.value : "0") || 0;

	const apiUrl = `${CHART_DATA_API_URL}?totalTasks=${totalTasks}`;
	console.log(`Fetching chart data from: ${apiUrl}`);

	try {
		const response = await fetch(apiUrl);
		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}
		const chartData = await response.json();
		console.log("Received chart data:", chartData);

		// 각 차트 업데이트 함수 호출 (백엔드에서 받은 데이터 전달)
		updateOverallStatusChart(chartData.overallStatus);
		updateWeekStatusChart(chartData.weeklyDefectTrend);
		updateMonthStatusChart(chartData.monthlyDefectTrend); // 월간 차트 업데이트
		updateYearStatusChart(chartData.yearlyDefectTrend);
		updateDayStatusChart(chartData.dailyStatus); // 당일 불량 유형 비율 차트
		// dailyTaskCompletionChart는 fetchAndDisplayDailyProgress에서 별도로 업데이트되므로 여기서 호출하지 않음
	} catch (error) {
		console.error("차트 데이터를 가져오는 중 오류 발생:", error);
	}
}

/**
 * 전체 감지 상태 비율 차트를 업데이트합니다.
 * @param {Object} data 백엔드에서 받은 전체 감지 상태 데이터
 */
function updateOverallStatusChart(data) {
	const ctx = document.getElementById("detectionStatusChart")?.getContext("2d");
	if (!ctx) {
		console.error(
			"Error: Canvas element with id 'detectionStatusChart' not found.",
		);
		return;
	}

	if (detectionStatusChart) {
		detectionStatusChart.destroy();
	}

	detectionStatusChart = new Chart(ctx, {
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
						"rgb(250, 184, 113)", // Substandard (Yellowish)
					],
					borderColor: [
						"rgba(75, 192, 192, 1)",
						"rgba(255, 99, 132, 1)",
						"rgba(252, 133, 6, 1)",
					],
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
							const total = dataset.data.reduce((sum, val) => sum + val, 0);
							const currentValue = dataset.data[tooltipItem.dataIndex];
							const percentage =
								total === 0 ? "0.0" : ((currentValue / total) * 100).toFixed(1);
							return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
						},
					},
				},
				noDataText: {},
			},
		},
		plugins: [ChartDataLabels, noDataTextPlugin],
	});
}

/**
 * 주간 불량 감지 현황 차트를 업데이트합니다.
 * @param {Object} data 백엔드에서 받은 주간 불량 추이 데이터
 */
function updateWeekStatusChart(data) {
	const ctx = document.getElementById("weekStatusChart")?.getContext("2d");
	if (!ctx) {
		console.error("Error: Canvas element with id 'weekStatusChart' not found.");
		return;
	}

	if (weekStatusChart) {
		weekStatusChart.destroy();
	}

	weekStatusChart = new Chart(ctx, {
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
						formatter: function (value) {
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
				noDataText: {},
			},
		},
		plugins: [ChartDataLabels, noDataTextPlugin],
	});
}

/**
 * 월간 불량 감지 현황 차트를 업데이트합니다.
 * @param {Object} data 백엔드에서 받은 월간 불량 추이 데이터
 */
function updateMonthStatusChart(data) {
	const ctx = document.getElementById("monthStatusChart")?.getContext("2d");
	if (!ctx) {
		console.error(
			"Error: Canvas element with id 'monthStatusChart' not found.",
		);
		return;
	}

	if (monthStatusChart) {
		monthStatusChart.destroy();
	}

	monthStatusChart = new Chart(ctx, {
		type: "line",
		data: {
			labels: data.labels,
			datasets: [
				{
					label: "불량 률",
					data: data.data,
					backgroundColor: "rgba(255, 99, 132, 0.6)",
					borderColor: "rgba(255, 99, 132, 1)",
					borderWidth: 1,
					datalabels: {
						display: true,
						color: "black",
						anchor: "end",
						align: "top",
						formatter: function (value) {
							return value > 0 ? value + "%" : "";
						},
						font: { size: 12, weight: "bold" },
					},
				},
			],
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			plugins: {
				legend: {
					position: "top",
				},
				title: {
					display: true,
					text: "월간 불량 감지 추이 (%)",
				},
				tooltip: {
					callbacks: {
						label: function (tooltipItem) {
							const dataset = tooltipItem.dataset;
							const currentValue = dataset.data[tooltipItem.dataIndex];
							return `${tooltipItem.label}: ${currentValue} %`;
						},
					},
				},
				noDataText: {},
			},
			scales: {
				x: {
					stacked: false,
					title: { display: true, text: "월" },
				},
				y: {
					beginAtZero: true,
					stacked: false,
					title: { display: true, text: "%" },
					ticks: {
						callback: function (value) {
							if (Number.isInteger(value)) {
								return value;
							}
							return null;
						},
					},
				},
			},
			layout: { padding: 40 },
		},
		plugins: [ChartDataLabels, noDataTextPlugin],
	});
}

/**
 * 연간 불량률 추이 차트를 업데이트합니다.
 * @param {Object} data 백엔드에서 받은 연간 불량 추이 데이터 (불량률)
 */
function updateYearStatusChart(data) {
	const ctx = document.getElementById("yearStatusChart")?.getContext("2d");
	if (!ctx) {
		console.error("Error: Canvas element with id 'yearStatusChart' not found.");
		return;
	}

	if (yearStatusChart) {
		yearStatusChart.destroy();
	}

	yearStatusChart = new Chart(ctx, {
		type: "line",
		data: {
			labels: data.labels,
			datasets: [
				{
					label: "불량률",
					data: data.data,
					fill: false,
					borderColor: "rgba(255, 159, 64, 1)",
					tension: 0.1,
					datalabels: {
						display: true,
						color: "black",
						anchor: "end",
						align: "top",
						formatter: function (value) {
							return value.toFixed(1) + "%";
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
					title: { display: true, text: "불량률 (%)" },
					ticks: {
						callback: function (value) {
							return value.toFixed(1);
						},
					},
				},
				x: { title: { display: true, text: "년도" } },
			},
			layout: { padding: 40 },
			plugins: {
				legend: { display: true, position: "top" },
				title: { display: true, text: "연간 불량률 추이 (%)" },
				tooltip: {
					callbacks: {
						label: function (tooltipItem) {
							const dataset = tooltipItem.dataset;
							const currentValue = dataset.data[tooltipItem.dataIndex];
							return `${tooltipItem.label}: ${currentValue.toFixed(1)} %`;
						},
					},
				},
				noDataText: {},
			},
		},
		plugins: [ChartDataLabels, noDataTextPlugin],
	});
}

/**
 * 당일 감지 상태 비율 차트를 업데이트합니다.
 * @param {Object} data 백엔드에서 받은 당일 감지 상태 데이터
 */
function updateDayStatusChart(data) {
	const ctx = document.getElementById("dayStatusChart")?.getContext("2d");
	if (!ctx) {
		console.error("Error: Canvas element with id 'dayStatusChart' not found.");
		return;
	}

	if (dayStatusChart) {
		dayStatusChart.destroy();
	}

	const backgroundColors = [
		"#36A2EB",
		"#FF6384",
		"#FFCE56",
		"#4BC0C0",
		"#9966FF",
		"#FF9F40",
		"#E6E6FA",
		"#ADD8E6",
		"#F08080",
		"#90EE90",
	];

	dayStatusChart = new Chart(ctx, {
		type: "pie",
		data: {
			labels: data.labels,
			datasets: [
				{
					data: data.data,
					backgroundColor: backgroundColors.slice(0, data.labels.length),
					hoverOffset: 4,
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
					text: "당일 불량 유형별 비율",
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
							const total = dataset.data.reduce((sum, val) => sum + val, 0);
							const currentValue = dataset.data[tooltipItem.dataIndex];
							const percentage =
								total === 0 ? "0.0" : ((currentValue / total) * 100).toFixed(1);
							return `${tooltipItem.label}: ${currentValue} (${percentage}%)`;
						},
					},
				},
				noDataText: {},
			},
		},
		plugins: [ChartDataLabels, noDataTextPlugin],
	});
}

/**
 * 최신 불량 정보를 백엔드에서 가져와 테이블에 표시합니다.
 */
async function fetchAndDisplayLatestDefects() {
	if (!defectTableBody) {
		console.error(
			"Error: Element with id 'defect-table tbody' not found for latest defects table.",
		);
		return;
	}

	defectTableBody.innerHTML = `
      <tr>
       <td colspan="7" class="py-4 px-4 text-center text-gray-500">
       불량 정보 가져오는 중...
       </td>
      </tr>
    `;

	try {
		const response = await fetch(LATEST_DEFECTS_API_URL);

		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`);
		}

		const defects = await response.json();

		defectTableBody.innerHTML = "";

		if (defects && defects.length > 0) {
			defects.forEach((defect) => {
				const row = document.createElement("tr");
				row.classList.add("hover:bg-gray-50");

				const detectionTime = defect.detectionTime
					? moment
							.utc(defect.detectionTime)
							.tz("Asia/Seoul")
							.format("YYYY-MM-DD HH:mm:ss")
					: "-";

				const clazz = defect.clazz || "-";
				const reason = defect.reason || "-";
				const confidence =
					defect.confidence !== undefined && defect.confidence !== null
						? (defect.confidence * 100).toFixed(2) + "%"
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
				const imageUrl = defect.imageUrl || "";

				row.innerHTML = `
          <td class="py-2 px-4 border-b">${detectionTime}</td>
          <td class="py-2 px-4 border-b">${clazz}</td>
          <td class="py-2 px-4 border-b">${reason}</td>
          <td class="py-2 px-4 border-b">${confidence}</td>
          <td class="py-2 px-4 border-b">${box}</td>
          <td class="py-2 px-4 border-b">${areaPercent}</td>
          <td class="py-2 px-4 border-b text-center">
            ${
							imageUrl
								? `<img src="${imageUrl}" alt="Defect Snapshot" class="h-16 w-16 object-cover rounded-md mx-auto" onerror="this.onerror=null;this.src='https://placehold.co/64x64/E0E0E0/FFFFFF?text=No+Image';">`
								: "이미지 없음"
						}
          </td>
        `;
				defectTableBody.appendChild(row);
			});
		} else {
			defectTableBody.innerHTML = `
          <tr>
           <td colspan="7" class="py-4 px-4 text-center text-gray-500">
           감지된 불량이 없습니다.
           </td>
          </tr>
        `;
		}
	} catch (error) {
		console.error("불량 정보를 가져오는 중 오류 발생:", error);
		defectTableBody.innerHTML = `
        <tr>
         <td colspan="7" class="py-4 px-4 text-center text-red-500">
           오류 발생: ${error.message}
         </td>
        </tr>
      `;
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
	if (monthStatusChart) {
		monthStatusChart.resize();
	}
});
