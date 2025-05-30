package com.project2.smartfactory.chart;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project2.smartfactory.defect.DetectionLogService;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/charts") // 차트 관련 API 엔드포인트 경로
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost", "http://192.168.0.122", "http://192.168.0.124"}, maxAge = 3600) // 컨트롤러의 모든 메서드에 적용
public class ChartDataController { 

    private final DetectionLogService detectionLogService;

    /**
     * 모든 차트에 필요한 데이터를 JSON 형태로 반환합니다.
     * @param totalTasks 당일 총 작업량 (프론트엔드에서 쿼리 파라미터로 전달받습니다)
     * @return 각 차트 데이터가 포함된 Map (JSON 변환)
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getAllChartData(@RequestParam(value = "totalTasks", defaultValue = "0") int totalTasks) {
        // totalTasks는 프론트엔드에서 전달받으며, 기본값은 0으로 설정합니다.
        Map<String, Object> chartData = detectionLogService.getChartData(totalTasks);
        return ResponseEntity.ok(chartData);
    }

    // 필요에 따라 특정 차트 데이터만 가져오는 엔드포인트를 추가할 수 있습니다.
    // 예: /api/charts/weekly-trend, /api/charts/daily-completion 등
}
