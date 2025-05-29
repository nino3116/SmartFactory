package com.project2.smartfactory.notification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 알림 관련 API 요청을 처리하는 REST 컨트롤러.
 * - SSE를 통한 실시간 알림 스트림 제공.
 * - 읽지 않은 알림 개수 조회.
 * - 최근 알림 목록 조회.
 * - 모든 알림을 읽음 상태로 표시.
 * - 테스트용 알림 추가 (개발/디버깅용).
 * - 특정 알림을 숨김 상태로 변경 (알림 목록에서 표시 안 함).
 */
@RestController
@RequestMapping("/api/notifications") // 알림 관련 API의 기본 경로
@RequiredArgsConstructor // Lombok을 사용하여 NotificationService를 자동 주입
@CrossOrigin(origins = {"http://localhost", "http://192.168.0.122", "http://192.168.0.124"}, maxAge = 3600) // CORS 설정
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    /**
     * 실시간 알림을 위한 SSE (Server-Sent Events) 엔드포인트.
     * 클라이언트가 이 엔드포인트에 연결하면 새로운 알림이 발생할 때마다 푸시됩니다.
     *
     * @return SseEmitter 객체
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        String emitterId = UUID.randomUUID().toString(); // 각 Emitter에 고유 ID 부여
        SseEmitter emitter = notificationService.addEmitter(emitterId); // NotificationService에 Emitter 등록

        // 클라이언트 연결 시, 초기 읽지 않은 알림 개수를 먼저 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("initialCount") // 클라이언트에서 'initialCount' 이벤트로 수신
                    .data(notificationService.getUnreadNotificationCount()));
        } catch (IOException e) {
            logger.error("Error while sending SSE initialCount : {}", e.getMessage());
            emitter.completeWithError(e); // 전송 중 오류 발생 시 Emitter 완료 처리
        }

        return emitter;
    }

    /**
     * 읽지 않은 알림의 개수를 반환합니다.
     * 프론트엔드에서 뱃지 카운트를 업데이트하는 데 사용됩니다.
     *
     * @return 읽지 않은 알림 개수
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadNotificationCount() {
        logger.info("UnreadNotificationCount API Requested.");
        long count = notificationService.getUnreadNotificationCount();
        return ResponseEntity.ok(count); // HTTP 200 OK와 함께 개수 반환
    }

    /**
     * 최근 알림 목록을 반환합니다.
     * 알림 창이 열렸을 때 표시될 알림들입니다.
     *
     * @return 최근 알림 목록 (JSON 형태)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Notification>> getRecentNotifications() {
        logger.info("RecentNotifications API Requested.");
        List<Notification> notifications = notificationService.getRecentNotifications();
        return ResponseEntity.ok(notifications); // HTTP 200 OK와 함께 목록 반환
    }

    /**
     * 모든 알림을 읽음 상태로 표시합니다.
     * 알림 창이 열렸을 때 이 API를 호출하여 모든 알림을 읽음 처리할 수 있습니다.
     *
     * @return 성공 응답 (HTTP 200 OK)
     */
    @PostMapping("/mark-as-read")
    public ResponseEntity<Void> markAllAsRead() {
        logger.info("MarkAllNotificationAsRead API Requested.");
        notificationService.markAllNotificationsAsRead();
        return ResponseEntity.ok().build(); // HTTP 200 OK 응답
    }

    /**
     * 특정 알림을 알림 목록에서 숨김 상태로 변경합니다 (display = false).
     *
     * @param id 숨길 알림의 ID
     * @return 성공 응답 또는 오류 응답
     */
    @PostMapping("/hide/{id}")
    public ResponseEntity<Void> hideNotification(@PathVariable("id") Long id) {
        logger.info("API Request received for hiding: ID={}", id);
        notificationService.hideNotification(id);
        return ResponseEntity.ok().build(); // HTTP 200 OK 응답
    }


    /**
     * 테스트용 알림을 추가하는 엔드포인트 (개발/디버깅 목적으로만 사용).
     *
     * @param type    알림 유형 (예: "INFO", "WARNING", "ERROR", "SUCCESS", "CONVEYOR_BELT", "DEFECT_MODULE", "DEFECT_DETECTED", "MQTT_CLIENT")
     * @param title   알림 제목
     * @param message 알림 내용
     * @return 성공 메시지 또는 오류 메시지
     */
    @PostMapping("/add-test")
    public ResponseEntity<String> addTestNotification(@RequestParam String type, @RequestParam String title, @RequestParam String message) {
        logger.info("테스트 알림 추가 API 요청 수신: Type={}, Title='{}', Message='{}'", type, title, message);
        try {
            // String으로 받은 type을 NotificationType Enum으로 변환
            Notification.NotificationType notificationType = Notification.NotificationType.valueOf(type.toUpperCase());
            notificationService.saveNotification(notificationType, title, message);
            return ResponseEntity.ok("테스트 알림이 성공적으로 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            // 유효하지 않은 알림 유형 문자열이 전달되었을 때
            logger.error("유효하지 않은 알림 유형입니다: {}. 에러: {}", type, e.getMessage());
            return ResponseEntity.badRequest().body("유효하지 않은 알림 유형입니다: " + type + ". 허용되는 유형: INFO, WARNING, ERROR, SUCCESS, CONVEYOR_BELT, DEFECT_MODULE, DEFECT_DETECTED, MQTT_CLIENT");
        } catch (Exception e) {
            logger.error("테스트 알림 추가 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("테스트 알림 추가 중 오류 발생: " + e.getMessage());
        }
    }
}
