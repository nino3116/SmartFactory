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

    // Json 키 이름과 Java 필드 이름이 다를경우 @JsonProperty로 맵핑
    // class는 Java키워드이므로 'clazz'로 변경후 맵핑

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name") // 데이터베이스 컬럼 이름 지정 (필드 이름과 다를 경우)
    @JsonProperty(value = "class", access = Access.READ_WRITE)
    private String clazz;

    @Column
    private double confidence;  // 감지 신뢰도

    @Column
    private String reason;  // 불량 판정 사유

    // List<Double>와 같은 컬렉션 타입을 엔티티에 포함시키려면 별도의 매핑 전략이 필요합니다.
    // @ElementCollection 및 @CollectionTable을 사용하여 별도의 테이블에 저장하는 방식을 예시로 보여줍니다.
    // 필요에 따라 @Embeddable 또는 다른 엔티티와의 @OneToMany 관계로 매핑할 수도 있습니다.
    @ElementCollection // 기본 타입 컬렉션 매핑
    @CollectionTable(name = "defect_box_coordinates", joinColumns = @JoinColumn(name = "defect_id")) // 컬렉션 데이터를 저장할 테이블 및 조인 컬럼 지정
    @Column(name = "coordinate") // 컬렉션 요소(Double)를 저장할 컬럼 이름
    private List<Double> box; // 바운딩 박스 좌표 [x1, y1, x2, y2]
    
    // private String imageUrl;

    @Transient
    private String snapshotPath; // 스냅샷 이미지 파일 경로 (Python 스크립트 실행 머신의 경로)

    @Column(name = "detailed_reason")
    private String detailedReason; // 불량 상세 사유

    // 'unriped' 불량의 경우 사과 면적대비 비율
    @JsonProperty("area_percent_on_apple")
    private Double areaPercentOnApple;  // Double 사용으로 Null 가능하게

    @Column(name = "image_url")
    private String imageUrl; // S3 등 웹에서 접근 가능한 이미지 URL

    // 데이터 감지 시간 등을 추가하는 것이 유용합니다.
    @Column(name = "detection_time")
    private LocalDateTime detectionTime;
    

}
