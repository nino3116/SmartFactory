// src/main/resources/static/js/session-manager.js

document.addEventListener("DOMContentLoaded", function () {
    // Flowbite 모달 초기화
    const sessionModalEl = document.getElementById('session-modal');
    let sessionModal;
    if (sessionModalEl) {
        sessionModal = new Modal(sessionModalEl, {
            backdrop: 'static', // 외부 클릭 방지 (JS 옵션)
            closable: false     // ESC 키로 닫히는 것 방지 (JS 옵션)
        });
    }

    // 서버에 설정된 세션 타임아웃 시간 (밀리초) - Spring Boot 설정과 일치시켜야 함
    // Spring Boot의 기본 세션 타임아웃 30분 (1800초)
    const SESSION_TIMEOUT_SECONDS = 2 * 60; // 30분
    const WARNING_TIME_SECONDS = 60; // 만료 1분 전 경고

    let sessionRemainingTime = SESSION_TIMEOUT_SECONDS;
    let sessionTimerInterval;

    const sessionTimerDisplay = document.getElementById('session-timer');
    const modalTimerDisplay = document.getElementById('modal-timer');
    const extendSessionBtn = document.getElementById('extend-session-btn');
    const cancelSessionBtn = document.getElementById('cancel-session-btn');

    function updateSessionTimerDisplay() {
        const minutes = Math.floor(sessionRemainingTime / 60);
        const seconds = sessionRemainingTime % 60;
        const timeString = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        if (sessionTimerDisplay) {
            sessionTimerDisplay.textContent = timeString;
        }
        if (modalTimerDisplay) {
            modalTimerDisplay.textContent = timeString;
        }
    }

    function startSessionTimer() {
        // 기존 타이머가 있다면 클리어
        if (sessionTimerInterval) {
            clearInterval(sessionTimerInterval);
        }

        sessionRemainingTime = SESSION_TIMEOUT_SECONDS; // 타이머 초기화
        updateSessionTimerDisplay();

        sessionTimerInterval = setInterval(() => {
            sessionRemainingTime--;
            updateSessionTimerDisplay();

            if (sessionRemainingTime <= 0) {
                clearInterval(sessionTimerInterval);
                // 세션 만료 처리 (예: 강제 로그아웃 또는 로그인 페이지로 리다이렉트)
                alert("세션이 만료되었습니다. 다시 로그인해주세요.");
                window.location.href = "/admin/logout"; // 로그아웃 엔드포인트 호출
            } else if (sessionRemainingTime === WARNING_TIME_SECONDS) {
                // 1분 남았을 때 모달 띄우기
                if (sessionModal) {
                    sessionModal.show();
                } else {
                    // Flowbite 모달이 초기화되지 않은 경우 대비
                    console.warn("Flowbite session modal not initialized correctly.");
                    alert("세션 만료 1분 전입니다. 연장하시겠습니까?");
                }
            }
        }, 1000); // 1초마다 업데이트
    }

    // 페이지 로드 시 타이머 시작
    startSessionTimer();

    // 세션 연장 버튼 클릭 이벤트
    if (extendSessionBtn) {
        extendSessionBtn.addEventListener('click', function() {
            // 서버에 세션 연장 요청 보내기
            fetch('/extendSession')
                .then(response => {
                    if (response.ok) {
                        return response.text();
                    }
                    throw new Error('Network response was not ok.');
                })
                .then(data => {
                    console.log(data);
                    startSessionTimer(); // 성공 시 타이머 재시작
                    if (sessionModal) {
                        sessionModal.hide(); // 모달 숨기기
                    }
                })
                .catch(error => {
                    console.error('세션 연장 실패:', error);
                    alert('세션 연장에 실패했습니다. 다시 로그인해주세요.');
                    window.location.href = "/admin/logout"; // 실패 시 강제 로그아웃
                });
        });
    }

    // 취소 (로그아웃) 버튼 클릭 이벤트
    if (cancelSessionBtn) {
        cancelSessionBtn.addEventListener('click', function() {
            if (confirm("세션 연장을 취소하고 로그아웃 하시겠습니까?")) {
                window.location.href = "/admin/logout"; // 로그아웃 엔드포인트 호출
            }
        });
    }
});