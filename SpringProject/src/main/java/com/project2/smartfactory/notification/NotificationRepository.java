package com.project2.smartfactory.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Notification 엔티티를 위한 Spring Data JPA 리포지토리 인터페이스입니다.
 * 데이터베이스 CRUD 작업을 추상화하여 제공합니다.
 */
@Repository // Spring Bean으로 등록
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 최신 알림을 가져오기 위해 timestamp를 기준으로 내림차순 정렬하여 반환합니다.
     * display가 true인 알림만 가져옵니다.
     *
     * @return 최신 알림 리스트
     */
    List<Notification> findTop20ByDisplayTrueOrderByTimestampDesc(); // display가 true인 최신 20개 알림

    /**
     * 읽지 않은 알림의 개수를 반환합니다.
     * display가 true인 알림 중에서 읽지 않은 알림만 카운트합니다.
     *
     * @return 읽지 않은 알림 개수
     */
    long countByIsReadFalseAndDisplayTrue(); // 읽지 않았고 표시할 알림 개수

    /**
     * 읽지 않은 알림 목록을 반환합니다.
     * display가 true인 알림 중에서 읽지 않은 알림만 가져옵니다.
     *
     * @return 읽지 않은 알림 목록
     */
    List<Notification> findByIsReadFalseAndDisplayTrue(); // 읽지 않았고 표시할 알림 목록

    /**
     * 특정 ID의 알림을 조회합니다.
     *
     * @param id 알림 ID
     * @return Notification 객체 (Optional)
     */
    // JpaRepository에 findById가 이미 있으므로 추가하지 않아도 됩니다.
    // 하지만 명확성을 위해 여기에 주석으로 남겨둡니다.
    // Optional<Notification> findById(Long id);
}
