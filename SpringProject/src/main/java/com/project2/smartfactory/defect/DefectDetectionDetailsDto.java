// src/main/java/com/project2/smartfactory/defect/DefectDetectionDetailsDto.java
package com.project2.smartfactory.defect;

import java.time.LocalDateTime; // detectionTime을 위해 추가

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 'defect_detection/details' 토픽에서 수신되는 JSON 메시지의 전체 구조를 나타내는 DTO.
 * 이 메시지는 개별 불량 정보(DefectInfo) 리스트를 포함합니다.
 */
@Data // Lombok: Getter, Setter, toString, equals, hashCode 자동 생성
@NoArgsConstructor // Lombok: 기본 생성자 자동 생성
@AllArgsConstructor // Lombok: 모든 필드를 인자로 받는 생성자 자동 생성
public class DefectDetectionDetailsDto {
    private LocalDateTime detectionTime; // JSON의 detectionTime 필드 (ISO 8601 문자열 자동 파싱)
    private String status;
    private int defectCount;
    private String defectSummary;
}
