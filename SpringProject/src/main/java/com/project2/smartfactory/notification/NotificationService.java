package com.project2.smartfactory.notification;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional; // Optional 임포트 추가
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * 알림을 관리하고 실시간으로 클라이언트에 푸시하는 서비스입니다.
 * - 알림 추가, 최근 알림 조회, 읽지 않은 알림 개수 조회 기능 제공.
 * - SSE(Server-Sent Events)를 통해 클라이언트에 실시간 알림을 전달.
 * - 알림 데이터를 데이터베이스에 저장하고 조회합니다.
 */
@Service
@RequiredArgsConstructor // Lombok: final 필드를 인자로 받는 생성자 자동 생성 (NotificationRepository 주입)
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository; // NotificationRepository 주입

    // SSE Emitter를 관리하는 맵 (클라이언트 ID -> SseEmitter)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // 주기적인 작업을 위한 스케줄러 (예: 연결 끊긴 Emitter 정리)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * NotificationService 초기화 시 호출되어 스케줄러를 시작합니다.
     */
    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanUpDisconnectedEmitters, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 새로운 알림을 생성하고 데이터베이스에 저장하며, 연결된 모든 SSE 클라이언트에 푸시합니다.
     *
     * @param type    알림 유형 (Notification.NotificationType Enum)
     * @param title   알림 제목
     * @param message 알림 내용
     * @return 저장된 Notification 엔티티
     */
    @Transactional // 트랜잭션 관리
    public Notification saveNotification(Notification.NotificationType type, String title, String message) {
        Notification newNotification = Notification.builder()
                .type(type) // Enum 값 사용
                .title(title)
                .message(message)
                .timestamp(LocalDateTime.now()) // 현재 시각
                .isRead(false) // 새로 추가된 알림은 읽지 않은 상태
                .display(true) // 새로 추가된 알림은 표시 상태
                .build();

        Notification savedNotification = notificationRepository.save(newNotification); // 데이터베이스에 저장
        logger.info("New notification is saved to DB: Type={}, Title='{}', ID={}", type, title, savedNotification.getId());
        sendNotificationToClients(savedNotification); // 모든 연결된 클라이언트에 알림 푸시
        return savedNotification;
    }

    /**
     * 최근 알림 목록을 데이터베이스에서 가져와 반환합니다.
     * display가 true인 알림만 조회합니다.
     *
     * @return 읽기 전용의 최근 알림 목록
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<Notification> getRecentNotifications() {
        logger.info("Get recent notifications from DB (to display).");
        // NotificationRepository에 정의된 메서드를 사용하여 display=true인 최신 알림을 가져옵니다.
        return notificationRepository.findTop20ByDisplayTrueOrderByTimestampDesc();
    }

    /**
     * 읽지 않은 알림의 개수를 데이터베이스에서 반환합니다.
     * display가 true인 알림 중에서 읽지 않은 알림만 카운트합니다.
     *
     * @return 읽지 않은 알림 개수
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public long getUnreadNotificationCount() {
        long count = notificationRepository.countByIsReadFalseAndDisplayTrue(); // 읽지 않았고 표시할 알림 개수 조회
        logger.debug("Unread notifications count (to display): {}", count);
        return count;
    }

    /**
     * 모든 알림을 읽음 상태로 표시하고 데이터베이스에 업데이트합니다.
     * display가 true인 알림 중에서 읽지 않은 알림만 업데이트합니다.
     */
    @Transactional // 트랜잭션 관리
    public void markAllNotificationsAsRead() {
        // display가 true인 읽지 않은 알림을 가져와 읽음 상태로 변경 후 저장합니다.
        List<Notification> unreadNotifications = notificationRepository.findByIsReadFalseAndDisplayTrue();
        unreadNotifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unreadNotifications); // 변경된 알림들을 한 번에 저장

        logger.info("All displayed notifications are set to read.");
        // 모든 알림을 읽음으로 표시했으므로, 클라이언트의 뱃지 카운트를 0으로 업데이트하기 위해
        // SSE로 'initialCount' 이벤트를 다시 보낼 수 있습니다.
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("initialCount") // 뱃지 카운트 업데이트를 위한 이벤트
                        .data(0L)); // 0으로 설정
            } catch (IOException e) {
                logger.error("Error sending initialCount event to Emitter {}: {}", id, e.getMessage());
                emitter.completeWithError(e);
                emitters.remove(id);
            }
        });
    }

    /**
     * 특정 알림을 알림 목록에서 숨김 상태로 변경합니다 (display = false).
     *
     * @param notificationId 숨길 알림의 ID
     * @return 업데이트된 Notification 객체 (Optional)
     */
    @Transactional // 트랜잭션 관리
    public Optional<Notification> hideNotification(Long notificationId) {
        Optional<Notification> notificationOptional = notificationRepository.findById(notificationId);
        if (notificationOptional.isPresent()) {
            Notification notification = notificationOptional.get();
            notification.setDisplay(false); // 표시 상태를 false로 변경
            notificationRepository.save(notification); // 데이터베이스에 업데이트
            logger.info("Notification ID {} is hidden from list.", notificationId);
            return Optional.of(notification);
        }
        logger.warn("Cannot hide Notification ID {} (Not Found).", notificationId);
        return Optional.empty();
    }


    /**
     * 새로운 SSE Emitter를 등록하고 관리합니다.
     * Emitter의 완료, 타임아웃, 에러 콜백을 설정하여 맵에서 제거되도록 합니다.
     *
     * @param emitterId Emitter의 고유 ID
     * @return 등록된 SseEmitter 객체
     */
    public SseEmitter addEmitter(String emitterId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃 설정

        // Emitter 완료 시 맵에서 제거
        emitter.onCompletion(() -> {
            logger.info("Emitter completed: {}", emitterId);
            emitters.remove(emitterId);
        });
        // Emitter 타임아웃 시 맵에서 제거
        emitter.onTimeout(() -> {
            logger.warn("Emitter timeout: {}", emitterId);
            emitter.complete(); // 타임아웃 시 Emitter를 완료 상태로 만듦
            emitters.remove(emitterId);
        });
        // Emitter 오류 발생 시 맵에서 제거
        emitter.onError(e -> {
            logger.error("Emitter error ({}): {}", emitterId, e.getMessage());
            emitters.remove(emitterId);
        });

        emitters.put(emitterId, emitter); // 맵에 Emitter 추가
        logger.info("New SSE Emitter added: {}", emitterId);
        return emitter;
    }

    /**
     * 연결된 모든 SSE 클라이언트에 새로운 알림을 푸시합니다.
     *
     * @param notification 푸시할 알림 객체
     */
    public void sendNotificationToClients(Notification notification) {
        // 동시성 문제를 피하기 위해 맵을 순회하는 동안 복사본을 사용하거나 ConcurrentHashMap의 이점을 활용
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId())) // 알림의 고유 ID (Long -> String 변환)
                        .name("newNotification") // 클라이언트에서 수신할 이벤트 이름
                        .data(notification)); // 전송할 알림 데이터 (JSON으로 자동 변환)
                logger.debug("To Emitter {}, Notification sent: {}", id, notification.getTitle());
            } catch (IOException e) {
                logger.error("To Emitter {}, error while sending notification: {}", id, e.getMessage());
                emitter.completeWithError(e); // 오류 발생 시 Emitter를 완료 상태로 만듦
                emitters.remove(id); // 맵에서 제거
            }
        });
    }

    /**
     * 주기적으로 연결 끊긴 Emitter를 정리합니다.
     */
    private void cleanUpDisconnectedEmitters() {
        logger.debug("Launching cleanup task for disconnected Emitters. Current Emitter count: {}", emitters.size());
    }

    /**
     * 애플리케이션 종료 시 스케줄러를 안전하게 종료합니다.
     */
    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("NotificationService Scheduler has been shut down.");
    }
}
