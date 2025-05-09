package com.project2.smartfactory.defect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime; // 감지 시간 기록을 위해 임포트

// 감지 로그 정보를 저장하는 JPA Entity
@Entity
@Table(name = "detection_log") // 매핑될 데이터베이스 테이블 이름
@Getter // Lombok: 모든 필드에 대한 Getter 자동 생성
@Setter // Lombok: 모든 필드에 대한 Setter 자동 생성
@NoArgsConstructor // Lombok: 인자 없는 기본 생성자 자동 생성 (JPA 필수)
@ToString // Lombok: toString() 메소드 자동 생성
public class DetectionLog {

    @Id // 기본 키 필드 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 데이터베이스의 자동 증가 기능 사용
    private Long id; // 기본 키

    @Column(name = "detection_time", nullable = false) // 감지 시간 (필수 필드)
    private LocalDateTime detectionTime;

    @Column(name = "status", nullable = false) // 감지 상태 (예: "Normal", "Defect Detected")
    private String status;

    @Column(name = "defect_count") // 감지된 불량 개수 (불량 감지 시)
    private Integer defectCount; // int 대신 Integer를 사용하여 null 허용

    @Column(name = "image_url", length = 512) // 감지 당시 스냅샷 이미지 URL (불량 감지 시)
    private String imageUrl; // S3 등 웹에서 접근 가능한 이미지 URL

    // 불량 유형 요약 (예: "Bruise, Unriped" 또는 "Normal")
    @Column(name = "defect_summary", length = 255)
    private String defectSummary;


    // 감지 상태와 불량 개수를 인자로 받는 생성자 (로그 기록 시 사용)
    public DetectionLog(String status, Integer defectCount, String imageUrl, String defectSummary) {
        this.detectionTime = LocalDateTime.now(); // 객체 생성 시 현재 시간 자동 설정
        this.status = status;
        this.defectCount = defectCount;
        this.imageUrl = imageUrl;
        this.defectSummary = defectSummary;
    }



}
