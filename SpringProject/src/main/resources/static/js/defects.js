// DOMContentLoaded 이벤트 리스너를 사용하여 DOM이 완전히 로드된 후에 스크립트 실행
document.addEventListener("DOMContentLoaded", () => {
	// --- 상수 정의 ---
	// API 엔드포인트 URL (Spring Boot 서버의 IP와 포트, 컨트롤러 경로에 맞게 수정 필요)
	const SPRING_BOOT_BASE_URL = "http://localhost:80"; // Spring Boot 기본 URL (예: "http://localhost:8080")
	const LATEST_DEFECTS_API_URL = `${SPRING_BOOT_BASE_URL}/api/latest-defects`; // 최신 불량 정보 API 엔드포인트
	const DETECTION_LOGS_API_URL = `${SPRING_BOOT_BASE_URL}/api/detection-logs`; // 감지 로그 API 엔드포인트
	const SCRIPT_CONTROL_START_URL = `${SPRING_BOOT_BASE_URL}/api/control/start`; // 스크립트 시작 API 엔드포인트
	const SCRIPT_CONTROL_STOP_URL = `${SPRING_BOOT_BASE_URL}/api/control/stop`; // 스크립트 중지 API 엔드포인트
	const SCRIPT_STATUS_API_URL = `${SPRING_BOOT_BASE_URL}/api/status/script`; // 스크립트 상태 조회 API 엔드포인트

	// 스트림 이미지 URL (필요에 따라 수정)
	const PRIMARY_STREAM_URL = "http://localhost:8080";
	const FALLBACK_STREAM_URL = "http://192.168.0.124:8000/stream.mjpg";

	// 데이터 업데이트 주기 (밀리초) - S3 요청 줄이려면 이 값을 늘리세요.
	const DEFECTS_UPDATE_INTERVAL = 30000; // 예: 30초
	const LOGS_UPDATE_INTERVAL = 60000; // 예: 60초
	const STATUS_UPDATE_INTERVAL = 5000; // 스크립트 상태는 자주 확인해도 부담 적음

	// --- DOM 요소 가져오기 ---
	const scriptStatusSpan = document.getElementById("script-status");
	const startScriptBtn = document.getElementById("start-script-btn");
	const stopScriptBtn = document.getElementById("stop-script-btn");
	const mjpegStreamImg = document.getElementById("mjpeg-stream-img");
	const streamStatusMessage = document.getElementById("stream-status-message");
	const defectTableBody = document.querySelector("#defect-table tbody");
	const detectionLogTableBody = document.querySelector(
		"#detection-log-table tbody",
	);

	// 모달 관련 요소
	const imageModal = document.getElementById("imageModal");
	const modalImage = document.getElementById("modalImage");
	const modalLogDetails = document.getElementById("modalLogDetails");
	const closeButton = document.querySelector(".close-button");

	// --- 전역 변수 ---
	// 감지 로그 데이터를 저장할 배열 (모달에서 상세 정보를 찾기 위해 사용)
	let detectionLogsData = [];
	// 대체 스트림 URL 시도 여부를 추적하는 플래그
	let fallbackAttempted = false;

	// --- 유틸리티 함수 ---

	// HTML 이스케이프 함수 (보안 강화)
	// 사용자 입력 또는 외부에서 가져온 데이터를 HTML에 삽입할 때 사용
	function escapeHTML(str) {
		if (typeof str !== "string") return str; // 문자열이 아니면 그대로 반환
		const div = document.createElement("div");
		div.appendChild(document.createTextNode(str));
		return div.innerHTML;
	}

	// 사용자에게 메시지를 표시하는 함수 (alert 대체)
	function displayMessage(message, type = "info") {
		// 실제 웹 페이지에서는 모달, 토스트 알림 등 더 나은 UI 요소를 사용해야 합니다.
		// 여기서는 간단하게 콘솔에 로그합니다.
		console.log(`[${type.toUpperCase()}] ${message}`);
		// 예: 특정 div에 메시지 표시
		// const messageArea = document.getElementById('message-area');
		// if (messageArea) {
		//     messageArea.textContent = message;
		//     messageArea.style.color = type === 'error' ? 'red' : 'black';
		// }
	}

	// --- 스크립트 상태 관련 함수 ---

	// 스크립트 상태 업데이트 함수 (UI 표시)
	function updateScriptStatus(statusText, stateClass) {
		if (scriptStatusSpan) {
			scriptStatusSpan.textContent = statusText;
			// 기존 상태 클래스 제거
			scriptStatusSpan.classList.remove(
				"status-loading",
				"status-success",
				"status-error",
			);
			// 새 상태 클래스 추가 (stateClass 값에 따라)
			if (stateClass === "loading") {
				scriptStatusSpan.classList.add("status-loading");
			} else if (stateClass === "success") {
				scriptStatusSpan.classList.add("status-success");
			} else if (stateClass === "error") {
				scriptStatusSpan.classList.add("status-error");
			} else {
				// 기본 상태 (stateClass가 없을 경우)
				scriptStatusSpan.classList.add("status-loading"); // 기본적으로 로딩 상태로 표시
			}
		} else {
			console.error("Error: 스크립트 상태 표시 요소를 찾을 수 없습니다.");
		}
	}

	// 스크립트 상태를 가져와서 표시하는 함수 (Fetch API 사용)
	async function fetchAndDisplayScriptStatus() {
		if (!scriptStatusSpan) return;

		updateScriptStatus("상태 확인 중...", "loading"); // 상태 확인 시작 시 로딩 표시

		try {
			const response = await fetch(SCRIPT_STATUS_API_URL);
			if (!response.ok) {
				updateScriptStatus(`상태 오류: ${response.status}`, "error");
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			const status = await response.text(); // 상태는 단순 문자열로 예상

			// 서버 응답 문자열에 따라 상태 클래스 결정 (예시)
			let stateClass = "info"; // 기본 상태 (필요하다면 CSS 클래스 추가)
			if (status.includes("실행 중") || status.includes("Running")) {
				stateClass = "success";
			} else if (status.includes("중지됨") || status.includes("Stopped")) {
				stateClass = "error"; // 중지 상태를 오류로 표시하거나 다른 클래스 사용
			} else if (status.includes("오류") || status.includes("Error")) {
				stateClass = "error";
			} else if (status.includes("로딩") || status.includes("Loading")) {
				stateClass = "loading";
			}

			updateScriptStatus(status, stateClass); // 가져온 상태와 클래스로 업데이트
		} catch (error) {
			console.error("스크립트 상태를 가져오는 중 오류 발생:", error);
			updateScriptStatus(`상태 오류: ${escapeHTML(error.message)}`, "error"); // 오류 발생 시 오류 상태 표시
		}
	}

	// 스크립트 시작 명령을 보내는 함수 (Fetch API 사용)
	async function startScript() {
		updateScriptStatus("스크립트 시작 요청 중...", "loading"); // 요청 시작 시 로딩 표시
		try {
			const response = await fetch(SCRIPT_CONTROL_START_URL, {
				method: "POST", // POST 요청
				headers: {
					"Content-Type": "application/json", // 필요에 따라 Content-Type 설정
				},
				// body: JSON.stringify({}) // 필요한 경우 요청 본문 추가
			});
			if (!response.ok) {
				updateScriptStatus(`시작 요청 오류: ${response.status}`, "error");
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log("스크립트 시작 명령 전송 성공");
			// 명령 전송 후 상태를 즉시 업데이트 시도
			setTimeout(fetchAndDisplayScriptStatus, 1000); // 1초 후 상태 다시 가져오기
		} catch (error) {
			console.error("스크립트 시작 명령 전송 중 오류 발생:", error);
			displayMessage(`스크립트 시작 명령 전송 실패: ${error.message}`, "error");
			updateScriptStatus(
				`시작 요청 실패: ${escapeHTML(error.message)}`,
				"error",
			); // 오류 발생 시 오류 상태 표시
		}
	}

	// 스크립트 중지 명령을 보내는 함수 (Fetch API 사용)
	async function stopScript() {
		updateScriptStatus("스크립트 중지 요청 중...", "loading"); // 요청 시작 시 로딩 표시
		try {
			const response = await fetch(SCRIPT_CONTROL_STOP_URL, {
				method: "POST", // POST 요청
				headers: {
					"Content-Type": "application/json", // 필요에 따라 Content-Type 설정
				},
				// body: JSON.stringify({}) // 필요한 경우 요청 본문 추가
			});
			if (!response.ok) {
				updateScriptStatus(`중지 요청 오류: ${response.status}`, "error");
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log("스크립트 중지 명령 전송 성공");
			// 명령 전송 후 상태를 즉시 업데이트 시도
			setTimeout(fetchAndDisplayScriptStatus, 1000); // 1초 후 상태 다시 가져오기
		} catch (error) {
			console.error("스크립트 중지 명령 전송 중 오류 발생:", error);
			displayMessage(`스크립트 중지 명령 전송 실패: ${error.message}`, "error");
			updateScriptStatus(
				`중지 요청 실패: ${escapeHTML(error.message)}`,
				"error",
			); // 오류 발생 시 오류 상태 표시
		}
	}

	// --- 데이터 가져오기 및 표시 함수 ---

	// 불량 정보를 가져와서 화면에 표시하는 함수 (최신 불량 결과 테이블)
	async function fetchAndDisplayLatestDefects() {
		if (!defectTableBody) return;

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
					clazzCell.textContent = escapeHTML(defect.clazz) || "-";
					row.appendChild(clazzCell);

					// 사유 (reason)
					let reasonCell = document.createElement("td");
					reasonCell.classList.add("py-2", "px-4", "border-b");
					reasonCell.textContent = escapeHTML(defect.reason) || "-";
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
					imageCell.classList.add("py-2", "px-4", "border-b", "text-center");
					if (defect.imageUrl) {
						const img = document.createElement("img");
						img.src = escapeHTML(defect.imageUrl);
						img.alt = "불량 스냅샷";
						img.classList.add("defect-image"); // CSS 클래스 적용
						// 불량 정보 테이블의 이미지는 클릭 시 확대 모달을 사용
						img.addEventListener("click", function () {
							// 불량 정보는 상세 로그 정보가 없으므로 null 전달
							openImageModal(this.src, null);
						});
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
                        오류 발생: ${escapeHTML(error.message)}
                    </td>
                </tr>
            `;
		}
	}

	// 감지 로그를 가져와서 화면에 표시하는 함수 (감지 로그 테이블)
	async function fetchAndDisplayDetectionLogs() {
		// 스크롤 가능한 컨테이너 요소를 가져옵니다.
		const scrollContainer = document.querySelector(
			".detection-log-scroll-container",
		);
		if (!scrollContainer || !detectionLogTableBody) {
			console.error(
				"Error: 감지 로그 컨테이너 또는 테이블 본문 요소를 찾을 수 없습니다.",
			);
			return;
		}

		// 데이터를 새로 로드하기 전에 현재 스크롤 위치를 저장합니다.
		const currentScrollTop = scrollContainer.scrollTop;

		detectionLogTableBody.innerHTML = `
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

			// 감지 로그 데이터의 JSON 구조에 맞게 파싱 및 처리 (List<DetectionLog> 객체 예상)
			const logs = await response.json();

			// --- 디버그 로그: 데이터 가져온 직후 순서 확인 ---
			//console.log("--- Original Logs Order (after fetch) ---");
			//logs.forEach((log) =>
			//	console.log(log.id, new Date(log.detectionTime).toLocaleString()),
			//);
			//console.log("-----------------------------------------");

			// 가져온 로그 데이터를 전역 변수에 저장 (모달에서 사용)
			// 원본 순서 그대로 저장
			detectionLogsData = logs;

			detectionLogTableBody.innerHTML = ""; // 이전 내용 지우기

			if (logs && logs.length > 0) {
				// --- 서버에서 이미 최신순으로 보내준다고 가정하고 reverse() 제거 ---
				// 만약 서버에서 오래된 순서로 보내준다면 이 부분을 다시 logs.slice().reverse()로 변경해야 합니다.
				const orderedLogsForDisplay = logs; // <-- reverse() 제거

				// --- 디버그 로그: 테이블 표시용 순서 확인 ---
				//console.log("--- Logs Order for Display ---");
				//orderedLogsForDisplay.forEach((log) =>
				//	console.log(log.id, new Date(log.detectionTime).toLocaleString()),
				//);
				//console.log("-----------------------------------------");

				orderedLogsForDisplay.forEach((log) => {
					// <-- 테이블 표시용 배열 순회
					const row = document.createElement("tr");
					row.classList.add("hover:bg-gray-50");
					// 감지 로그 테이블 행에 클릭 이벤트 리스너 추가
					row.style.cursor = "pointer"; // 클릭 가능한 요소처럼 커서 변경
					row.addEventListener("click", function () {
						// 이 행에 해당하는 로그 데이터 찾기 (id 또는 다른 고유 값 사용)
						// 주의: find 메서드는 원본 배열(detectionLogsData)에서 찾습니다.
						const selectedLog = detectionLogsData.find(
							(item) => item.id === log.id,
						); // log.id가 고유하다고 가정

						if (selectedLog) {
							const logDetails = {
								시간: selectedLog.detectionTime
									? new Date(selectedLog.detectionTime).toLocaleString()
									: "-",
								상태: selectedLog.status || "-",
								"불량 유형 요약": selectedLog.defectSummary || "-",
								"불량 개수":
									selectedLog.defectCount !== undefined &&
									selectedLog.defectCount !== null
										? selectedLog.defectCount
										: "-",
							};
							openImageModal(selectedLog.imageUrl, logDetails);
						} else {
							console.error(`Selected log data not found for id: ${log.id}`);
							// 상세 정보를 찾을 수 없다는 메시지와 함께 이미지라도 표시
							openImageModal(log.imageUrl, {
								"상세 정보": "데이터를 찾을 수 없습니다.",
							});
						}
					});

					// 시간 (detectionTime)
					let timeCell = document.createElement("td");
					timeCell.classList.add("py-2", "px-4", "border-b");
					try {
						timeCell.textContent = log.detectionTime
							? new Date(log.detectionTime).toLocaleString()
							: "-";
					} catch (e) {
						console.error(
							"Error parsing detection time:",
							log.detectionTime,
							e,
						);
						timeCell.textContent = escapeHTML(log.detectionTime) || "-"; // 파싱 실패 시 원본 문자열 표시
					}
					row.appendChild(timeCell);

					// 상태 (status) - 예: "Normal", "Defect Detected"
					let statusCell = document.createElement("td");
					statusCell.classList.add("py-2", "px-4", "border-b");
					statusCell.textContent = escapeHTML(log.status) || "-";
					row.appendChild(statusCell);

					// 불량 유형 요약 (defectSummary) - 불량 감지 시에만 해당
					let summaryCell = document.createElement("td");
					summaryCell.classList.add("py-2", "px-4", "border-b");
					summaryCell.textContent = escapeHTML(log.defectSummary) || "-"; // defectSummary 필드 사용
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
					imageCell.classList.add("py-2", "px-4", "border-b", "text-center");
					if (log.imageUrl) {
						const img = document.createElement("img");
						img.src = escapeHTML(log.imageUrl);
						img.alt = "로그 스냅샷";
						img.classList.add("log-image-in-table"); // 이미지 자체는 클릭 안함, 클래스 이름 변경
						img.style.cssText = "width: 50px; height: auto;"; // 스타일 추가
						imageCell.appendChild(img);
					} else {
						imageCell.textContent = "이미지 없음";
					}
					row.appendChild(imageCell);

					detectionLogTableBody.appendChild(row);
				});
			} else {
				detectionLogTableBody.innerHTML = `
                        <tr>
                          <td colspan="5" class="py-4 px-4 text-center text-gray-500">
                                감지 로그가 없습니다.
                          </td>
                        </tr>
                      `;
			}

			// 테이블 업데이트가 완료된 후 저장된 스크롤 위치로 복원합니다.
			scrollContainer.scrollTop = currentScrollTop;
		} catch (error) {
			console.error("감지 로그를 가져오는 중 오류 발생:", error);
			detectionLogTableBody.innerHTML = `
                    <tr>
											<td colspan="5" class="py-4 px-4 text-center text-red-500">
													오류 발생: ${escapeHTML(error.message)}
											</td>
                    </tr>
                  `;
		}
	}

	// --- 상세 이미지 모달 관련 함수 ---

	// 이미지 모달 열기 함수
	// imageUrl: 모달에 표시할 이미지 URL
	// logDetails: 로그 상세 정보 객체 (시간, 상태 등) - 불량 정보 모달 시에는 null
	function openImageModal(imageUrl, logDetails) {
		if (!imageModal || !modalImage || !modalLogDetails) {
			console.error("Error: 모달 요소를 찾을 수 없습니다.");
			return;
		}

		modalImage.src = escapeHTML(imageUrl); // 이미지 URL도 이스케이프 처리

		modalLogDetails.innerHTML = ""; // 이전 상세 정보 초기화

		if (logDetails) {
			// 로그 상세 정보 표시
			for (const key in logDetails) {
				if (logDetails.hasOwnProperty(key)) {
					const p = document.createElement("p");
					// HTML 이스케이프 처리된 키와 값으로 설정
					p.innerHTML = `<strong>${escapeHTML(key)}:</strong> ${escapeHTML(logDetails[key])}`;
					modalLogDetails.appendChild(p);
				}
			}
			modalLogDetails.style.display = "block"; // 상세 정보 영역 표시
		} else {
			modalLogDetails.style.display = "none"; // 상세 정보 영역 숨김
		}

		// 모달을 flex 컨테이너로 설정하여 내부 요소를 중앙 정렬
		imageModal.style.display = "flex";
	}

	// 이미지 모달 닫기 함수
	function closeImageModal() {
		if (imageModal && modalImage && modalLogDetails) {
			imageModal.style.display = "none"; // 모달 숨김
			modalImage.src = ""; // 이미지 소스 초기화
			modalLogDetails.innerHTML = ""; // 상세 정보 초기화
		} else {
			console.error("Error: 모달 닫기 중 요소를 찾을 수 없습니다.");
		}
	}

	// --- MJPEG 스트림 로딩 함수 ---

	// MJPEG 스트림 이미지 로딩 처리
	function setupMjpegStream() {
		if (!mjpegStreamImg || !streamStatusMessage) {
			console.error(
				"Error: 스트림 이미지 또는 상태 메시지 요소를 찾을 수 없습니다.",
			);
			// Optionally set a status message if the elements are missing
			const container = document.querySelector(".card"); // 적절한 부모 요소 선택
			if (container) {
				const errorDiv = document.createElement("div");
				errorDiv.textContent = "스트림 표시 요소를 찾을 수 없습니다.";
				errorDiv.style.color = "red";
				errorDiv.style.textAlign = "center";
				container.appendChild(errorDiv);
			}
			return;
		}

		// 이미지 로드 성공 시 호출될 함수
		const handleStreamLoad = () => {
			console.log("스트림 로드 성공:", mjpegStreamImg.src);
			streamStatusMessage.textContent = "스트림 연결됨";
			streamStatusMessage.className = "stream-status status-success"; // 성공 상태 스타일 적용
			// 성공 후에는 오류 핸들러를 제거하여 불필요한 로직 실행 방지
			mjpegStreamImg.removeEventListener("error", handleStreamError);
			// 로드 핸들러는 계속 필요할 수 있습니다 (스트림이 끊어졌다가 다시 연결될 경우 등)
		};

		// 이미지 로드 실패 시 호출될 함수
		const handleStreamError = () => {
			console.error("스트림 로드 실패:", mjpegStreamImg.src);

			// 아직 대체 URL을 시도하지 않았다면 시도합니다.
			if (!fallbackAttempted) {
				console.log(
					"첫 번째 스트림 실패. 두 번째 스트림 시도:",
					FALLBACK_STREAM_URL,
				);
				mjpegStreamImg.src = FALLBACK_STREAM_URL; // 대체 URL로 변경
				streamStatusMessage.textContent =
					"첫 번째 스트림 연결 실패. 대체 스트림 시도 중...";
				streamStatusMessage.className = "stream-status status-loading"; // 로딩 상태 스타일 유지
				fallbackAttempted = true; // 대체 시도 플래그 설정

				// 대체 URL 로드 성공/실패에 대한 새로운 이벤트 리스너는 필요 없습니다.
				// 동일한 handleStreamLoad/handleStreamError 함수가 호출될 것입니다.
				// 단, 오류 발생 시 fallbackAttempted 플래그를 체크하여 무한 루프를 방지합니다.
			} else {
				// 이미 대체 URL을 시도했는데 또 오류가 발생했거나,
				// 첫 번째 URL 시도 중 플래그 설정 전에 다시 오류가 발생한 경우 (거의 발생하지 않음)
				console.error("두 번째 스트림도 실패:", mjpegStreamImg.src);
				streamStatusMessage.textContent = "스트림 연결 실패";
				streamStatusMessage.className = "stream-status status-error"; // 오류 상태 스타일 적용
				// 최종 실패 후에는 오류 이벤트 리스너 제거 (로드 성공 시 로드 리스너는 유지될 수 있음)
				mjpegStreamImg.removeEventListener("error", handleStreamError);
			}
		};

		// 이벤트 리스너 추가
		mjpegStreamImg.addEventListener("load", handleStreamLoad);
		mjpegStreamImg.addEventListener("error", handleStreamError);

		// Initial check in case the image loads from cache immediately
		// 이미지 로드가 이미 완료된 경우 (캐시 등) handleStreamLoad를 수동으로 호출
		// complete 속성은 이미지 로드가 완료되었는지, naturalHeight는 이미지 크기가 유효한지 확인
		if (mjpegStreamImg.complete && mjpegStreamImg.naturalHeight !== 0) {
			handleStreamLoad();
		} else {
			// If not complete, the error or load event will fire
			// Ensure initial status is set
			streamStatusMessage.textContent = "스트림 연결 중...";
			streamStatusMessage.className = "stream-status status-loading";
		}

		// 기본 스트림 URL로 이미지 로딩 시작
		mjpegStreamImg.src = PRIMARY_STREAM_URL;
	}

	// --- 이벤트 리스너 및 초기 로딩 설정 ---

	// 스크립트 제어 버튼 이벤트 리스너 추가
	if (startScriptBtn) {
		startScriptBtn.addEventListener("click", startScript);
	} else {
		console.error("Error: 'start-script-btn' 요소를 찾을 수 없습니다.");
	}

	if (stopScriptBtn) {
		stopScriptBtn.addEventListener("click", stopScript);
	} else {
		console.error("Error: 'stop-script-btn' 요소를 찾을 수 없습니다.");
	}

	// 모달 닫기 버튼 및 외부 클릭 이벤트 리스너 추가
	if (imageModal && closeButton) {
		// 닫기 버튼 클릭 시 모달 숨기기
		closeButton.addEventListener("click", function () {
			closeImageModal();
		});

		// 모달 외부 영역 클릭 시 모달 숨기기
		window.addEventListener("click", function (event) {
			if (event.target === imageModal) {
				closeImageModal();
			}
		});
	} else {
		console.error("Error: 모달 또는 닫기 버튼 요소를 찾을 수 없습니다.");
	}

	// 초기 데이터 로딩
	fetchAndDisplayLatestDefects();
	fetchAndDisplayDetectionLogs(); // 감지 로그 로딩 (최신순으로 표시됨)
	fetchAndDisplayScriptStatus();
	setupMjpegStream(); // MJPEG 스트림 로딩 시작

	// 주기적으로 데이터 업데이트 설정 - S3 요청을 줄이려면 이 간격을 늘리세요.
	setInterval(fetchAndDisplayLatestDefects, DEFECTS_UPDATE_INTERVAL);
	setInterval(fetchAndDisplayDetectionLogs, LOGS_UPDATE_INTERVAL); // 감지 로그 주기적 업데이트
	setInterval(fetchAndDisplayScriptStatus, STATUS_UPDATE_INTERVAL);
}); // DOMContentLoaded 끝
