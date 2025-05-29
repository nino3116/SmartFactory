// src/main/resources/static/js/main.js

document.addEventListener("DOMContentLoaded", function () {
	// Variables to hold DOM elements
	let notificationButton = null;
	let notificationContainer = null;
	let notificationList = null;
	let notificationCountBadge = null;
	let closeBtn = null;

	/**
	 * 알림 기능에 필요한 DOM 요소들을 초기화하고 존재 여부를 확인합니다.
	 * 모든 필수 요소가 발견되면 true를 반환하고, 그렇지 않으면 false를 반환합니다.
	 */
	function initializeDOMElements() {
		notificationButton = document.getElementById("notification-button");
		notificationContainer = document.getElementById("notification-container");
		notificationList = document.getElementById("notification-list");
		notificationCountBadge = document.getElementById(
			"notification-count-badge",
		);
		closeBtn = document.getElementById("notification-close");

		if (
			!notificationButton ||
			!notificationContainer ||
			!notificationList ||
			!notificationCountBadge ||
			!closeBtn
		) {
			console.error(
				"알림 기능에 필요한 DOM 요소 중 일부가 발견되지 않았습니다. 'fragments/header.html' (notification-button, notification-count-badge) 및 'fragments/notification.html' (notification-container, notification-list, notification-close) 파일에 ID가 올바르게 설정되어 있는지 확인해주세요.",
			);
			return false; // Initialization failed
		}
		return true; // Initialization successful
	}

	// DOMContentLoaded 이벤트 발생 시 요소 초기화 시도
	if (!initializeDOMElements()) {
		// 필수 요소가 없으면 알림 관련 JavaScript 실행을 중단합니다.
		return;
	}

	let unreadCount = 0; // 읽지 않은 알림 개수

	/**
	 * 알림 유형에 따라 아이콘 클래스와 SVG 경로를 반환합니다.
	 * @param {string} type 알림 유형 (예: "INFO", "ERROR", "DEFECT_DETECTED")
	 * @returns {{iconClass: string, svgPath: string}} 아이콘 클래스와 SVG 경로 객체
	 */
	function getNotificationIcon(type) {
		let iconClass = "bg-gray-100 text-gray-500"; // 기본값
		let svgPath = `<path d="M12 9v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>`; // 기본 정보 아이콘

		switch (type) {
			case "INFO":
				iconClass = "bg-blue-100 text-blue-500";
				svgPath = `<path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
            />;`; // 정보 아이콘
				break;
			case "WARNING":
				iconClass = "bg-yellow-100 text-yellow-500";
				svgPath = `<path d="M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0"></path><path d="M12 8v4"></path><path d="M12 16h.01"></path>`; // 경고 아이콘
				break;
			case "ERROR":
				iconClass = "bg-red-100 text-red-500";
				svgPath = `<path d="M12 9v4"></path><path d="M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0z"></path><path d="M12 16h.01"></path>`; // 오류 아이콘 (경고와 동일)
				break;
			case "SUCCESS":
				iconClass = "bg-green-100 text-green-500";
				svgPath = `<path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
            />`; // 성공 아이콘 (체크마크)
				break;
			case "CONVEYOR_BELT":
				iconClass = "bg-purple-100 text-purple-500";
				svgPath = `<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>`; // 컨베이어 벨트 (쉴드 아이콘)
				break;
			case "DEFECT_MODULE":
				iconClass = "bg-orange-100 text-orange-500";
				svgPath = `<path d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>`; // 불량 모듈 (코드 아이콘)
				break;
			case "DEFECT_DETECTED":
				iconClass = "bg-red-100 text-red-500";
				svgPath = `<path d="M12 9v4m0 4h.01M10.363 3.591l-8.106 13.5A1.99 1.99 0 003.953 21h16.094a1.99 1.99 0 001.696-3.909l-8.106-13.5a1.99 1.99 0 00-3.392 0z"></path>`; // 불량 감지 (경고 아이콘)
				break;
			case "MQTT_CLIENT":
				iconClass = "bg-indigo-100 text-indigo-500";
				svgPath = `<path d="M12 18.7l-6.5-3.8V9.3l6.5-3.8 6.5 3.8v5.6l-6.5 3.8zM12 12a3 3 0 100-6 3 3 0 000 6z"></path>`; // MQTT 클라이언트 (육각형 아이콘)
				break;
			default:
				// 기본값은 이미 설정되어 있음
				break;
		}
		return { iconClass, svgPath };
	}

	// 알림 아이템을 생성하는 함수
	function createNotificationItem(notification) {
		const li = document.createElement("li");
		// display가 false인 알림은 숨김 처리
		if (!notification.display) {
			li.style.display = "none";
		}

		// Use flexbox to align content and button
		li.className =
			"px-4 py-3 flex items-center justify-between bg-white hover:bg-gray-50 transition duration-150 ease-in-out";
		if (!notification.read) {
			li.classList.add("font-bold");
		}

		const timeAgo = getTimeAgo(notification.timestamp);
		const { iconClass, svgPath } = getNotificationIcon(notification.type);

		li.innerHTML = `
      <div class="flex items-start space-x-4 flex-1"> <div class="flex-shrink-0">
          <span class="inline-flex items-center justify-center h-10 w-10 rounded-full ${iconClass}">
            <svg
              class="h-6 w-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              stroke-width="1.5"
            >
              ${svgPath}
            </svg>
          </span>
        </div>
        <div class="flex-1"> <p class="font-semibold text-gray-900">${notification.title}</p>
          <p class="text-gray-500 text-sm">${notification.message}</p>
          <span class="text-xs text-gray-400 mt-1 block">${timeAgo}</span>
        </div>
      </div>
      <button class="hide-notification-btn text-gray-400 hover:text-gray-600 focus:outline-none ml-2"> <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    `;

		// X 버튼 클릭 이벤트 리스너 추가
		const hideButton = li.querySelector(".hide-notification-btn");
		if (hideButton) {
			hideButton.addEventListener("click", async (event) => {
				const notificationId = notification.id; // Use notification.id directly as it's available
				if (notificationId) {
					await hideNotification(notificationId);
					// 숨김 처리 후 해당 알림 항목을 DOM에서 제거
					li.remove();
					// 뱃지 카운트 다시 계산
					updateBadgeCount(await fetchUnreadCount());
				}
			});
		}

		return li;
	}

	// 시간 포맷팅 함수
	function getTimeAgo(timestamp) {
		const now = new Date();
		const notificationTime = new Date(timestamp);
		const diffSeconds = Math.floor((now - notificationTime) / 1000);

		if (diffSeconds < 60) {
			return `${diffSeconds}초 전`;
		} else if (diffSeconds < 3600) {
			return `${Math.floor(diffSeconds / 60)}분 전`;
		} else if (diffSeconds < 86400) {
			return `${Math.floor(diffSeconds / 3600)}시간 전`;
		} else if (diffSeconds < 86400 * 7) {
			return `${Math.floor(diffSeconds / 86400)}일 전`;
		} else {
			return notificationTime
				.toLocaleDateString("ko-KR", {
					year: "numeric",
					month: "2-digit",
					day: "2-digit",
				})
				.replace(/\. /g, ".")
				.replace(/\.$/, "");
		}
	}

	// 뱃지 카운트 업데이트 함수
	function updateBadgeCount(count) {
		unreadCount = count;
		if (notificationCountBadge) {
			if (unreadCount > 0) {
				notificationCountBadge.textContent = unreadCount;
				notificationCountBadge.classList.remove("hidden");
			} else {
				notificationCountBadge.classList.add("hidden");
			}
		} else {
			console.warn(
				"notificationCountBadge 요소가 없어 뱃지 카운트를 업데이트할 수 없습니다.",
			);
		}
	}

	// 읽지 않은 알림 개수를 서버에서 가져오는 함수
	async function fetchUnreadCount() {
		try {
			const response = await fetch("/api/notifications/unread-count");
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			return await response.json();
		} catch (error) {
			console.error("읽지 않은 알림 개수를 가져오는 중 오류 발생:", error);
			return 0; // 오류 발생 시 0 반환
		}
	}

	// 알림 목록 새로고침 함수
	async function fetchRecentNotifications() {
		if (!notificationList) {
			console.error(
				"notificationList 요소가 없어 최근 알림을 가져올 수 없습니다.",
			);
			return;
		}
		try {
			const response = await fetch("/api/notifications/recent");
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			const notifications = await response.json();

			notificationList.innerHTML = ""; // 기존 목록 비우기
			if (notifications.length === 0) {
				notificationList.innerHTML = `
          <li class="px-4 py-3 text-center text-gray-500 text-sm">
            새로운 알림이 없습니다.
          </li>
        `;
			} else {
				let hasVisibleNotifications = false;
				notifications.forEach((notification) => {
					// display가 true인 알림만 추가
					if (notification.display) {
						notificationList.appendChild(createNotificationItem(notification));
						hasVisibleNotifications = true;
					}
				});
				// 만약 display=true인 알림이 하나도 없으면 "새로운 알림이 없습니다" 메시지 표시
				if (!hasVisibleNotifications) {
					notificationList.innerHTML = `
              <li class="px-4 py-3 text-center text-gray-500 text-sm">
                새로운 알림이 없습니다.
              </li>
          `;
				}
			}
		} catch (error) {
			console.error("최근 알림을 가져오는 중 오류 발생:", error);
			notificationList.innerHTML = `
        <li class="px-4 py-3 text-center text-red-500 text-sm">
          알림을 불러오는 데 실패했습니다.
        </li>
      `;
		}
	}

	// 모든 알림을 읽음으로 표시하는 함수
	async function markAllAsRead() {
		try {
			const response = await fetch("/api/notifications/mark-as-read", {
				method: "POST",
			});
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log("모든 알림이 읽음으로 표시되었습니다.");
			updateBadgeCount(0); // 뱃지 카운트 0으로 업데이트
			fetchRecentNotifications(); // 목록 새로고침
		} catch (error) {
			console.error("알림을 읽음으로 표시하는 중 오류 발생:", error);
		}
	}

	// 특정 알림을 숨김 상태로 변경하는 함수
	async function hideNotification(id) {
		try {
			const response = await fetch(`/api/notifications/hide/${id}`, {
				method: "POST",
			});
			if (!response.ok) {
				throw new Error(`HTTP error! status: ${response.status}`);
			}
			console.log(`알림 ID ${id}가 숨겨졌습니다.`);
			// 뱃지 카운트 업데이트는 fetchUnreadCount() 호출 후 updateBadgeCount()에서 처리
		} catch (error) {
			console.error(`알림 ID ${id}를 숨기는 중 오류 발생:`, error);
		}
	}

	// --- 이벤트 리스너 설정 ---

	// 알림 버튼 클릭 시
	if (notificationButton && notificationContainer) {
		notificationButton.addEventListener("click", function () {
			notificationContainer.classList.toggle("hidden");
			if (!notificationContainer.classList.contains("hidden")) {
				fetchRecentNotifications();
				markAllAsRead();
			}
		});
	}

	// 알림 닫기 버튼 클릭 시
	if (closeBtn && notificationContainer) {
		closeBtn.addEventListener("click", function () {
			notificationContainer.classList.add("hidden");
		});
	}

	// --- SSE (Server-Sent Events)를 통한 실시간 알림 수신 ---
	const eventSource = new EventSource("/api/notifications/stream");

	eventSource.onopen = function () {
		console.log("SSE 연결이 열렸습니다.");
	};

	eventSource.onmessage = function (event) {
		console.log("Received a generic SSE message:", event.data);
	};

	eventSource.addEventListener("initialCount", function (event) {
		const count = parseInt(event.data);
		updateBadgeCount(count);
		console.log("초기 알림 개수:", count);
	});

	eventSource.addEventListener("newNotification", function (event) {
		const newNotification = JSON.parse(event.data);
		console.log("새로운 알림 수신:", newNotification);

		// 알림 창이 열려있지 않을 때만 뱃지 카운트 증가
		if (notificationContainer.classList.contains("hidden")) {
			updateBadgeCount(unreadCount + 1);
		} else {
			// 알림 창이 열려있으면 즉시 읽음 처리 및 목록 업데이트 (새로고침)
			markAllAsRead();
		}

		if (notificationContainer && notificationList) {
			if (notificationContainer.classList.contains("hidden")) {
				console.log("새 알림이 도착했습니다! (알림 창 닫힘 상태)");
			} else {
				const noNotificationMessage =
					notificationList.querySelector("li.text-center");
				if (
					noNotificationMessage &&
					noNotificationMessage.textContent.includes("새로운 알림이 없습니다.")
				) {
					notificationList.innerHTML = "";
				}
				// display가 true인 경우에만 목록에 추가
				if (newNotification.display) {
					notificationList.prepend(createNotificationItem(newNotification));
				}
			}
		}
	});

	eventSource.onerror = function (event) {
		console.error("SSE 오류 발생:", event);
		if (event.eventPhase === EventSource.CLOSED) {
			console.log("SSE 연결이 닫혔습니다. 재연결 시도...");
		}
	};

	// 햄버거 버튼 동작 처리 (기존 코드 유지)
	const sidebarToggleBtn = document.getElementById("sidebar-toggle");
	if (sidebarToggleBtn) {
		document.addEventListener("drawer:show", (event) => {
			if (event.target.id === "sidebar-drawer") {
				sidebarToggleBtn.classList.add("hidden");
			}
		});
		document.addEventListener("drawer:hide", (event) => {
			if (event.target.id === "sidebar-drawer") {
				sidebarToggleBtn.classList.remove("hidden");
			}
		});
	} else {
		console.warn("sidebarToggleBtn 요소가 발견되지 않았습니다.");
	}

	// 초기 알림 개수 가져오기 (페이지 로드 시)
	fetchUnreadCount().then((count) => updateBadgeCount(count));
});
