package com.project2.smartfactory.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 알림 정보를 나타내는 DTO 클래스.
 * Lombok 어노테이션을 사용하여 Getter, Setter, 생성자 등을 자동으로 생성합니다.
*/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notifications")
@Builder
public class Notification {

    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 알림의 고유 ID

    @Column(name = "notification_type", nullable = false)
    private NotificationType type; // 알림 유형
    @Column(name = "title", nullable = false)
    private String title; // 알림 제목
    @Column(name = "message", nullable = false, length = 1000)
    private String message; // 알림 메시지
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp; // 알림 발생 시각
    @Column(name = "isRead", nullable = false)
    private Boolean isRead; // 알림 읽음 여부
    @Column(name = "icon_class")
    private String iconClass; // 알림 아이콘의 Tailwind CSS 클래스
    @Column(name = "svg_path")
    private String svgPath; // 알림 아이콘으로 사용할 SVG 패스 데이터

    @Column(nullable = false)
    @Builder.Default
    private Boolean display = true;    // 알림 목록에서 표시할지 말지 (기본값 = true)

    /**
     * 알림의 유형을 정의하는 Enum.
     * 각 유형에 따라 아이콘이나 색상 등을 다르게 표시할 수 있습니다.
     */
    public enum NotificationType {
        INFO,          // 일반 정보 (예: MQTT 연결 성공)
        WARNING,       // 경고 (예: 연결 끊김, 알 수 없는 메시지)
        ERROR,         // 오류 (예: JSON 파싱 오류, 데이터베이스 오류)
        SUCCESS,       // 성공 (예: 정상 감지)
        CONVEYOR_BELT, // 컨베이어 벨트 관련 (예: 동작 시작/정지)
        DEFECT_MODULE, // 불량 감지 모듈 관련 (예: 모듈 시작/정지)
        DEFECT_DETECTED, // 불량 감지 (예: 특정 불량 유형 감지)
        MQTT_CLIENT    // MQTT 클라이언트 상태 (예: 연결/해제)
    }

}
