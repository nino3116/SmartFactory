package com.project2.smartfactory.defect;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;



@NoArgsConstructor
@Data
public class DetectionResultDto {

    private LocalDateTime detectionTime; // 감지 시간
    private String status; // 감지 상태 (예: "Normal", "Defect Detected")
    private Integer defectCount; // 감지된 불량 개수
    private String imageUrl; // 감지 당시 스냅샷 이미지 URL
    private String defectSummary; // 불량 유형 요약
    private List<DefectInfo> defects; // 감지된 상세 불량 목록 (DefectInfo 객체 리스트)


}
