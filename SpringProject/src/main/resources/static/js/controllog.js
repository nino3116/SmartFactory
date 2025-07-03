// DOMContentLoaded 이벤트 리스너를 사용하여 DOM이 완전히 로드된 후에 스크립트 실행
document.addEventListener("DOMContentLoaded", () => {
	const SPRING_BOOT_BASE_URL = ""; // Spring Boot 기본 URL (예: "http://localhost:8080")
	const CONTROL_LOGS_API_URL = `${SPRING_BOOT_BASE_URL}/ctrl/logs`;

	const SCRIPT_CONTROL_START_URL = `${SPRING_BOOT_BASE_URL}/api/control/start`; // 스크립트 시작 API 엔드포인트
	const SCRIPT_CONTROL_STOP_URL = `${SPRING_BOOT_BASE_URL}/api/control/stop`; // 스크립트 중지 API 엔드포인트
	const SCRIPT_STATUS_API_URL = `${SPRING_BOOT_BASE_URL}/api/status/script`; // 스크립트 상태 조회 API 엔드포인트
	const SYSTEM_STATUS_API_URL = `${SPRING_BOOT_BASE_URL}/api/status/system`; // 시스템 상태 조회 API 엔드포인트 (추가)
	const STREAM_STATUS_API_URL = `${SPRING_BOOT_BASE_URL}/status/image_stream`; // 이미지 스트림 상태 조회 API 엔드포인트

	// 스트림 이미지 URL (필요에 따라 수정)
	const PRIMARY_STREAM_URL = "http://localhost:8080";
	const FALLBACK_STREAM_URL = "http://192.168.10.246:8000/stream.mjpg";

	const STATUS_UPDATE_INTERVAL = 5000; // 스크립트/시스템 상태는 자주 확인해도 부담 적음
	const LOGS_UPDATE_INTERVAL = 5000;

	const controlLogTableBody = document.querySelector(
		"#control-log-table tbody",
	);

	// === 상태 표시 엘리먼트와 버튼 그룹 ===
	const systemStatus = document.getElementById("system-status"); // 시스템 상태 표시 span
	const checkSystemStatus = document.getElementById("check-system-status"); // 시스템 상태 확인 span
	const scriptStatusSpan = document.getElementById("script-status"); // 스크립트 상태 표시 span
	const checkScriptStatus = document.getElementById("check-script-status"); // 스크립트 상태 확인 span
	const checkStreamStatus = document.getElementById("stream-status-message"); // 스트림 상태 메시지 span

	const startScriptBtn = document.getElementById("start-script-btn");
	const stopScriptBtn = document.getElementById("stop-script-btn");

	const startBtn = document.getElementById("btn-start"); // 시스템 시작 버튼
	const stopBtn = document.getElementById("btn-stop"); // 시스템 중지 버튼

	const toggleButtons = document.querySelectorAll(".toggle-button");

	// === 유틸: 상태 텍스트 + 색상 클래스 변경 (공통 함수) ===
	// 이 함수는 이제 updateSystemStatus와 updateScriptStatus 내부에서 더 세밀하게 관리됩니다.
	// async function updateStatus(el, isOn) {
	// 	el.textContent = isOn ? "ON" : "OFF";
	// 	el.classList.toggle("text-green-600", isOn);
	// 	el.classList.toggle("text-gray-600", !isOn);
	// }

	// === 유틸: 버튼 색상 상태 변경 (하이라이트) ===
	function setActiveButton(activeBtn, inactiveBtn) {
		activeBtn.classList.add(
			"ring-2",
			"ring-offset-2",
			"ring-green-300",
			"font-bold",
		);
		inactiveBtn.classList.remove(
			"ring-2",
			"ring-offset-2",
			"ring-green-300",
			"font-bold",
		);
	}

	function setInactiveButton(activeBtn, inactiveBtn) {
		activeBtn.classList.add(
			"ring-2",
			"ring-offset-2",
			"ring-red-300",
			"font-bold",
		);
		inactiveBtn.classList.remove(
			"ring-2",
			"ring-offset-2",
			"ring-red-300",
			"font-bold",
		);
	}

	// === 시스템 상태 업데이트 함수 (UI 표시) ===
	// updateScriptStatus와 유사하게 수정
	function updateSystemStatus(statusText, stateClass) {
		if (systemStatus && checkSystemStatus) {
			systemStatus.textContent = statusText;
			checkSystemStatus.textContent = statusText;

			// 기존 상태 클래스 제거
			systemStatus.classList.remove(
				"status-loading",
				"status-success",
				"status-error",
			);
			checkSystemStatus.classList.remove(
				"status-loading",
				"status-success",
				"status-error",
			);

			// 새 상태 클래스 추가 (stateClass 값에 따라)
			if (stateClass === "loading") {
				systemStatus.classList.add("status-loading");
				checkSystemStatus.classList.add("status-loading");
			} else if (stateClass === "success") {
				systemStatus.classList.add("status-success");
				checkSystemStatus.classList.add("status-success");
			} else if (stateClass === "error") {
				systemStatus.classList.add("status-error");
				checkSystemStatus.classList.add("status-error");
			} else {
				// 기본 상태 (stateClass가 없을 경우)
				systemStatus.classList.add("status-loading"); // 기본적으로 로딩 상태로 표시
				checkSystemStatus.classList.add("status-loading");
			}
		} else {
			console.error("Error: 시스템 상태 표시 요소를 찾을 수 없습니다.");
		}
	}

	// === 시스템 상태를 가져와서 표시하는 함수 (Fetch API 사용) ===
	// fetchAndDisplayScriptStatus와 유사하게 수정
	async function fetchAndDisplaySystemStatus() {
		if (!systemStatus || !checkSystemStatus) return;

		updateSystemStatus("상태 확인 중...", "loading"); // 상태 확인 시작 시 로딩 표시

		try {
			const response = await fetch(SYSTEM_STATUS_API_URL);
			if (!response.ok) {
				updateSystemStatus(`상태 오류: ${response.status}`, "error");
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			const status = await response.text(); // 상태는 단순 문자열로 예상 (running, stopped 등)

			if (status.includes("<!")) {
				window.location.reload(true);
			}

			// 서버 응답 문자열에 따라 상태 클래스 결정
			let stateClass = "loading"; // 기본 상태
			if (status.includes("stopped") || status.includes("Disconnected")) {
				stateClass = "error";
				setInactiveButton(stopBtn, startBtn); // 중지 상태면 stop 버튼 활성화
			} else if (status.includes("running")) {
				stateClass = "success";
				setActiveButton(startBtn, stopBtn); // 실행 중이면 start 버튼 활성화
			} else if (status.includes("Error") || status.includes("UNKNOWN")) {
				stateClass = "error";
			} else if (
				status.includes("Default") ||
				status.includes("Initializing")
			) {
				stateClass = "loading"; // 초기 상태는 로딩으로
			}

			updateSystemStatus(status, stateClass); // 가져온 상태와 클래스로 업데이트
		} catch (error) {
			console.error("시스템 상태를 가져오는 중 오류 발생:", error);
			updateSystemStatus(`상태 오류: ${escapeHTML(error.message)}`, "error"); // 오류 발생 시 오류 상태 표시
		}
	}

	// === 시스템 시작/중지 버튼 제어 ===
	startBtn.addEventListener("click", async () => {
		updateSystemStatus("시스템 시작 요청 중...", "loading"); // 요청 시작 시 로딩 표시
		try {
			const response = await fetch(
				`${SPRING_BOOT_BASE_URL}/api/control/system/start`,
				{
					method: "POST",
					headers: {
						"Content-Type": "application/json",
					},
				},
			);
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log("시스템 시작 명령 전송 성공");
			setTimeout(fetchAndDisplaySystemStatus, 1000); // 1초 후 상태 다시 가져오기
		} catch (error) {
			console.error("시스템 시작 명령 전송 중 오류 발생:", error);
			updateSystemStatus(
				`시작 요청 실패: ${escapeHTML(error.message)}`,
				"error",
			);
		}
	});

	stopBtn.addEventListener("click", async () => {
		updateSystemStatus("시스템 중지 요청 중...", "loading"); // 요청 시작 시 로딩 표시
		try {
			const response = await fetch(
				`${SPRING_BOOT_BASE_URL}/api/control/system/stop`,
				{
					method: "POST",
					headers: {
						"Content-Type": "application/json",
					},
				},
			);
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log("시스템 중지 명령 전송 성공");
			setTimeout(fetchAndDisplaySystemStatus, 1000); // 1초 후 상태 다시 가져오기
		} catch (error) {
			console.error("시스템 중지 명령 전송 중 오류 발생:", error);
			updateSystemStatus(
				`중지 요청 실패: ${escapeHTML(error.message)}`,
				"error",
			);
		}
	});

	// 스크립트 상태 업데이트 함수 (UI 표시)
	function updateScriptStatus(statusText, stateClass) {
		if (scriptStatusSpan && checkScriptStatus) {
			scriptStatusSpan.textContent = statusText;
			checkScriptStatus.textContent = statusText;
			// 기존 상태 클래스 제거
			scriptStatusSpan.classList.remove(
				"status-loading",
				"status-success",
				"status-error",
			);
			checkScriptStatus.classList.remove(
				"status-loading",
				"status-success",
				"status-error",
			);
			// 새 상태 클래스 추가 (stateClass 값에 따라)
			console.log(stateClass);
			if (stateClass === "loading") {
				scriptStatusSpan.classList.add("status-loading");
				checkScriptStatus.classList.add("status-loading");
			} else if (stateClass === "success") {
				scriptStatusSpan.classList.add("status-success");
				checkScriptStatus.classList.add("status-success");
			} else if (stateClass === "error") {
				scriptStatusSpan.classList.add("status-error");
				checkScriptStatus.classList.add("status-error");
			} else {
				// 기본 상태 (stateClass가 없을 경우)
				scriptStatusSpan.classList.add("status-loading"); // 기본적으로 로딩 상태로 표시
				checkScriptStatus.classList.add("status-loading");
			}
		} else {
			console.error("Error: 스크립트 상태 표시 요소를 찾을 수 없습니다.");
		}
	}

	// 카메라 스트림 상태를 확인하고 표시하는 함수
	async function fetchAndDisplayStreamStatus() {
		if (!checkStreamStatus) return;

		const response = await fetch(STREAM_STATUS_API_URL);
		// console.log(response);
		const status = await response.text();
		console.log(status);
		checkStreamStatus.textContent = status;
		if (status == "ALIVE") {
			checkStreamStatus.classList.remove("status-error");
			checkStreamStatus.classList.add("status-success");
		} else if (status == "ERROR") {
			checkStreamStatus.classList.remove("status-success");
			checkStreamStatus.classList.add("status-error");
		}
	}

	// 스크립트 상태를 가져와서 표시하는 함수 (Fetch API 사용)
	async function fetchAndDisplayScriptStatus() {
		if (!scriptStatusSpan || !checkScriptStatus) return;

		updateScriptStatus("상태 확인 중...", "loading"); // 상태 확인 시작 시 로딩 표시

		try {
			const response = await fetch(SCRIPT_STATUS_API_URL);
			if (!response.ok) {
				updateScriptStatus(`상태 오류: ${response.status}`, "error");
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			const status = await response.text(); // 상태는 단순 문자열로 예상

			if (status.includes("<!")) {
				window.location.reload(true);
			}

			// 서버 응답 문자열에 따라 상태 클래스 결정 (예시)
			let stateClass = "loading"; // 기본 상태
			if (
				status.includes("중지됨") ||
				status.includes("Stopped") ||
				status.includes("Not Running")
			) {
				stateClass = "error"; // 중지 상태를 오류로 표시하거나 다른 클래스 사용
			} else if (
				status.includes("실행 중") ||
				status.includes("Running") ||
				status.includes("Started")
			) {
				stateClass = "success";
			} else if (status.includes("오류") || status.includes("Error")) {
				stateClass = "error";
			} else if (status.includes("로딩") || status.includes("Loading")) {
				stateClass = "loading";
			} else if (
				status.includes("Default") ||
				status.includes("Initializing")
			) {
				stateClass = "loading"; // 초기 상태는 로딩으로
			} else {
				stateClass = "loading"; // 알 수 없는 상태도 로딩으로
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
			// displayMessage(`스크립트 시작 명령 전송 실패: ${error.message}`, "error"); // 이 함수는 정의되지 않았습니다.
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
			// displayMessage(`스크립트 중지 명령 전송 실패: ${error.message}`, "error"); // 이 함수는 정의되지 않았습니다.
			updateScriptStatus(
				`중지 요청 실패: ${escapeHTML(error.message)}`,
				"error",
			); // 오류 발생 시 오류 상태 표시
		}
	}

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

	// === 초기 상태 설정 ===
	// updateStatus(systemStatus, false); // 이제 updateSystemStatus가 처리
	setInactiveButton(stopBtn, startBtn); // 시스템 중지 버튼 기본 활성화

	// --- 유틸리티 함수 ---

	// HTML 이스케이프 함수 (보안 강화)
	// 사용자 입력 또는 외부에서 가져온 데이터를 HTML에 삽입할 때 사용
	function escapeHTML(str) {
		if (typeof str !== "string") return str; // 문자열이 아니면 그대로 반환
		const div = document.createElement("div");
		div.appendChild(document.createTextNode(str));
		return div.innerHTML;
	}

	// 감지 로그를 가져와서 화면에 표시하는 함수 (감지 로그 테이블)
	async function fetchAndDisplayControlLogs() {
		// 스크롤 가능한 컨테이너 요소를 가져옵니다.
		const scrollContainer = document.querySelector(
			".control-log-scroll-container",
		);
		if (!scrollContainer || !controlLogTableBody) {
			console.error(
				"Error: 감지 로그 컨테이너 또는 테이블 본문 요소를 찾을 수 없습니다.",
			);
			return;
		}

		// 데이터를 새로 로드하기 전에 현재 스크롤 위치를 저장합니다.
		const currentScrollTop = scrollContainer.scrollTop;

		controlLogTableBody.innerHTML = `
                  <tr>
                    <td colspan="5" class="py-4 px-4 text-center text-gray-500">
                          제어 로그 가져오는 중...
                    </td>
                  </tr>
                `; // 로딩 메시지

		try {
			const response = await fetch(CONTROL_LOGS_API_URL);

			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}

			// 감지 로그 데이터의 JSON 구조에 맞게 파싱 및 처리 (List<controlLog> 객체 예상)
			const logs = await response.json();

			// 가져온 로그 데이터를 전역 변수에 저장 (모달에서 사용)
			// 원본 순서 그대로 저장
			// controlLogsData = logs; // 이 변수가 어디에 정의되어 있는지 확인 필요

			controlLogTableBody.innerHTML = ""; // 이전 내용 지우기

			if (logs && logs.length > 0) {
				// --- 서버에서 이미 최신순으로 보내준다고 가정하고 reverse() 제거 ---
				// 만약 서버에서 오래된 순서로 보내준다면 이 부분을 다시 logs.slice().reverse()로 변경해야 합니다.
				const orderedLogsForDisplay = logs; // <-- reverse() 제거

				orderedLogsForDisplay.forEach((log) => {
					// <-- 테이블 표시용 배열 순회
					const row = document.createElement("tr");
					row.classList.add("hover:bg-gray-50");
					// 감지 로그 테이블 행에 클릭 이벤트 리스너 추가
					row.style.cursor = "pointer"; // 클릭 가능한 요소처럼 커서 변경

					// 시간 (controlTime)
					let timeCell = document.createElement("td");
					timeCell.classList.add("py-2", "px-4", "border-b");
					try {
						timeCell.textContent = log.controlTime
							? new Date(log.controlTime).toLocaleString()
							: "-";
					} catch (e) {
						console.error("Error parsing control time:", log.controlTime, e);
						timeCell.textContent = escapeHTML(log.controlTime) || "-"; // 파싱 실패 시 원본 문자열 표시
					}
					row.appendChild(timeCell);

					// 종류 (type)
					let typeCell = document.createElement("td");
					typeCell.classList.add("py-2", "px-4", "border-b");
					typeCell.textContent = escapeHTML(log.controlType) || "-";
					row.appendChild(typeCell);

					// 조작 내용(data)
					let dataCell = document.createElement("td");
					dataCell.classList.add("py-2", "px-4", "border-b");
					dataCell.textContent = escapeHTML(log.controlData) || "-";
					row.appendChild(dataCell);

					// 결과 상태 (result status)
					let rstatusCell = document.createElement("td");
					rstatusCell.classList.add("py-2", "px-4", "border-b");
					rstatusCell.textContent = escapeHTML(log.controlResultStatus) || "-";
					row.appendChild(rstatusCell);

					// 비고 (memo)
					let memoCell = document.createElement("td");
					memoCell.classList.add("py-2", "px-4", "border-b");
					memoCell.textContent = escapeHTML(log.controlMemo) || "-";
					row.appendChild(memoCell);

					controlLogTableBody.appendChild(row);
				});
			} else {
				controlLogTableBody.innerHTML = `
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
			console.error("제어 로그를 가져오는 중 오류 발생:", error);
			controlLogTableBody.innerHTML = `
                      <tr>
                        <td colspan="5" class="py-4 px-4 text-center text-red-500">
                              오류 발생: ${escapeHTML(error.message)}
                        </td>
                      </tr>
                    `;
		}
	}

	// 초기 데이터 로딩
	fetchAndDisplayControlLogs();
	fetchAndDisplaySystemStatus(); // 시스템 상태 초기 로딩
	fetchAndDisplayScriptStatus(); // 스크립트 상태 초기 로딩
	fetchAndDisplayStreamStatus(); // 스트림 상태 초기 로딩

	// 주기적으로 데이터 업데이트 설정
	setInterval(fetchAndDisplayControlLogs, LOGS_UPDATE_INTERVAL);
	setInterval(fetchAndDisplaySystemStatus, STATUS_UPDATE_INTERVAL); // 시스템 상태 주기적 업데이트
	setInterval(fetchAndDisplayScriptStatus, STATUS_UPDATE_INTERVAL); // 스크립트 상태 주기적 업데이트
	setInterval(fetchAndDisplayStreamStatus, STATUS_UPDATE_INTERVAL); // 스트림 상태 주기적 업데이트 (필요시)
}); // DOMContentLoaded 끝
