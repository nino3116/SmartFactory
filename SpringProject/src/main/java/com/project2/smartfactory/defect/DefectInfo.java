package com.project2.smartfactory.defect;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Entity
@NoArgsConstructor
public class DefectInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // MQTT 페이로드의 'clazz' 필드를 이 필드에 매핑합니다.
    // 'class'는 Java 키워드이므로 필드 이름은 'clazz' 또는 다른 이름으로 사용합니다.
    @Column(name = "class_name") // 데이터베이스 컬럼 이름 지정
    @JsonProperty("clazz") // MQTT 페이로드의 필드 이름과 일치시킵니다.
    private String clazz; // 또는 className, defectClass 등으로 변경 가능

    @Column
    private double confidence;  // 감지 신뢰도

    @Column
    private String reason;  // 불량 판정 사유

    // 바운딩 박스 좌표 리스트 매핑
    @ElementCollection
    @CollectionTable(name = "defect_box_coordinates", joinColumns = @JoinColumn(name = "defect_id"))
    @Column(name = "coordinate")
    private List<Double> box; // 바운딩 박스 좌표 [x1, y1, x2, y2]

    @Transient // 데이터베이스에 저장하지 않는 필드
    private String snapshotPath; // 스냅샷 이미지 파일 경로 (Python 스크립트 실행 머신의 로컬 경로)

    // MQTT 페이로드의 'detailed_reason' 필드를 매핑합니다.
    @Column(name = "detailed_reason") // 데이터베이스 컬럼 이름 지정
    @JsonProperty("detailed_reason") // MQTT 페이로드의 필드 이름과 일치시킵니다.
    private String detailedReason; // 불량 상세 사유

    // MQTT 페이로드의 'areaPercentOnApple' 필드를 매핑합니다.
    @Column(name = "area_percent_on_apple") // 데이터베이스 컬럼 이름 지정
    @JsonProperty("areaPercentOnApple") // MQTT 페이로드의 필드 이름과 일치시킵니다.
    private Double areaPercentOnApple;  // 'unriped' 불량의 경우 사과 면적대비 비율 (Double 사용으로 Null 가능하게)

    // 이 필드는 MQTT 페이로드의 개별 불량 객체에는 없습니다.
    // 만약 개별 불량마다 이미지를 표시하고 싶다면,
    // 1. Python 스크립트에서 개별 불량 영역 이미지를 생성하고 저장 후,
    // 2. MQTT 페이로드의 각 불량 객체에 해당 이미지 URL을 추가해야 합니다.
    // 현재 구조에서는 DTO의 전체 이미지 URL을 각 DefectInfo에 복사하거나,
    // UI에서 DefectLog의 imageUrl을 사용하여 표시해야 합니다.
    @Column(name = "image_url") // 데이터베이스 컬럼 이름 지정
    private String imageUrl; // S3 등 웹에서 접근 가능한 이미지 URL

    // 이 필드는 MQTT 페이로드의 개별 불량 객체에는 없습니다.
    // DefectService에서 DetectionResultDto의 detectionTime을 복사해야 합니다.
    @Column(name = "detection_time") // 데이터베이스 컬럼 이름 지정
    private LocalDateTime detectionTime;

    // Lombok @Data 어노테이션이 Getter, Setter, NoArgsConstructor 등을 포함합니다.
    // @AllArgsConstructor는 모든 필드를 포함하는 생성자를 자동으로 생성합니다.
}
