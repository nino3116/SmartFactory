package com.project2.smartfactory.defect;

// 필요한 임포트 문들을 확인하고 추가/수정하세요.
// 예: import com.project2.smartfactory.defect.dto.ChartDataDto;
// 예: import com.project2.smartfactory.defect.dto.DefectInfo;
// 예: import com.project2.smartfactory.defect.entity.DetectionLog;
// 예: import com.project2.smartfactory.defect.repository.DetectionLogRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger; // 로깅을 위한 임포트
import org.slf4j.LoggerFactory; // 로깅을 위한 임포트
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // Lombok을 사용하여 생성자 주입
public class DetectionLogService {

    // 로거 인스턴스 생성 (클래스 레벨에서 선언)
    private static final Logger logger = LoggerFactory.getLogger(DetectionLogService.class); // 변수 이름을 logger로 변경

    // @RequiredArgsConstructor 어노테이션이 final 필드를 자동으로 주입합니다.
    private final DetectionLogRepository detectionLogRepository; // 감지 로그 Repository

    // DefectInfo DTO 및 ChartDataDto DTO의 실제 패키지 경로에 맞게 임포트하세요.
    // 예: com.project2.smartfactory.defect.dto.DefectInfo
    // 예: com.project2.smartfactory.defect.dto.ChartDataDto


    // 모든 감지 로그를 가져오는 메서드 (기존에 있을 수 있음)
    // 이 메소드는 내부적으로만 사용하거나, 필요에 따라 public으로 유지할 수 있습니다.
    public List<DetectionLog> getAllDetectionLogs() {
        logger.info("Fetching all detection logs from repository."); // logger 사용
        try {
            // Repository에서 모든 로그를 조회합니다.
            // 필요에 따라 정렬 등의 로직을 추가할 수 있습니다.
            List<DetectionLog> logs = detectionLogRepository.findAll();
            logger.info("Successfully fetched {} detection logs.", logs.size()); // logger 사용
            return logs;
        } catch (Exception e) {
            logger.error("Error fetching detection logs from repository", e); // logger 사용
            // 데이터베이스 조회 실패 시 빈 리스트 반환 또는 예외 처리
            return List.of(); // 또는 throw new RuntimeException("Failed to fetch logs", e);
        }
    }

    /**
     * 차트 데이터에 필요한 통계 정보를 가공하여 반환합니다.
     * 연간 불량 추이 데이터는 각 월의 불량률(퍼센트)을 계산하여 제공합니다.
     *
     * @param totalTasks 당일 총 작업량 (프론트엔드에서 전달받거나 다른 곳에서 가져올 수 있습니다)
     * @return 각 차트에 필요한 데이터가 담긴 Map
     * 반환되는 연간 불량 추이 데이터 구조:
     * {
     * "yearlyDefectTrend": {
     * "labels": ["2020-01", "2020-02", ...], // 월별 라벨 (최근 12개월)
     * "data": [1.5, 1.2, ...]   // 각 월의 불량률 (double 또는 float)
     * },
     * // ... 다른 차트 데이터 ...
     * }
     */
    public Map<String, Object> getChartData(int totalTasks) {
        logger.info("Generating chart data with totalTasks: {}", totalTasks); // logger 사용
        // 모든 로그 데이터 가져오기
        List<DetectionLog> logs = getAllDetectionLogs();

        // 로그 데이터가 비어있을 경우 처리
        if (logs.isEmpty()) {
            logger.warn("No detection logs found. Returning empty chart data."); // logger 사용
            // 빈 데이터로 초기화된 Map 반환
             return Map.of(
                 "overallStatus", Map.of("labels", List.of("Normal", "Defective","Substandard"), "data", List.of(0L, 0L)),
                 "weeklyDefectTrend", Map.of("labels", List.of(), "data", List.of()),
                 // yearlyDefectTrend 구조 수정: data에 불량률 리스트 포함
                 "yearlyDefectTrend", Map.of("labels", List.of(), "data", List.of()),
                 "dailyStatus", Map.of("labels", List.of("Normal", "Defective","Substandard"), "data", List.of(0L, 0L)),
                 "dailyTaskCompletion", Map.of(
                     "labels", List.of("오늘 작업"),
                     "datasets", List.of(
                         Map.of("label", "완료", "data", List.of(0L)),
                         Map.of("label", "미완료", "data", List.of((long) totalTasks))
                     )
                 )
             );
        }

        Map<String, Object> chartData = new java.util.HashMap<>();

        // 1. 전체 감지 상태 비율 (파이 차트)
        logger.debug("Calculating overall status counts."); // logger 사용
        Map<String, Long> overallStatusCounts = logs.stream()
                 // status가 null인 경우를 대비하여 필터링 또는 기본값 설정 고려
                .filter(log -> log.getStatus() != null)
                .collect(Collectors.groupingBy(DetectionLog::getStatus, Collectors.counting()));

        // 항상 Normal, Defective 라벨 포함 및 데이터가 없을 경우 0으로 처리
        long normalCount = overallStatusCounts.getOrDefault("Normal", 0L);
        long defectCount = overallStatusCounts.getOrDefault("Defective", 0L);
        long substdCount = overallStatusCounts.getOrDefault("Substandard", 0L);
        chartData.put("overallStatus", Map.of(
                "labels", List.of("Normal", "Defective","Substandard"),
                "data", List.of(normalCount, defectCount,substdCount)
        ));
        logger.debug("Overall status: Normal={}, Defective={}, Substandard={}", normalCount, defectCount, substdCount); // logger 사용


        // 2. 주간 불량 감지 추이 (막대 차트 - 최근 7일 불량 개수)
        logger.debug("Calculating weekly defect trend."); // logger 사용
        LocalDate today = LocalDate.now();
        Map<String, Long> weeklyDefectCounts = new TreeMap<>(); // 날짜 순서 유지를 위해 TreeMap 사용
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd"); // 차트 라벨 형식
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Map 키 형식

        // 최근 7일 초기화 (오늘 포함)
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            weeklyDefectCounts.put(date.format(keyFormatter), 0L); // 불량 개수 0으로 초기화
        }

        // 최근 7일 불량 개수 계산
        logs.stream()
                .filter(log -> {
                    // detectionTime이 null이 아닌 경우를 대비하여 처리
                    if (log.getDetectionTime() == null) {
                        logger.warn("Detection log with null detectionTime skipped for weekly trend."); // logger 사용
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    // 최근 7일 이내의 로그 중 "Defective" 상태인 로그만 필터링
                    return !logDate.isBefore(today.minusDays(6)) && ("Defective".equals(log.getStatus()) || "Substandard".equals(log.getStatus()));
                })
                .forEach(log -> {
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    String dateKey = logDate.format(keyFormatter);
                    // 해당 날짜의 불량 개수를 합산 (defectCount가 null이면 0으로 처리)
                    weeklyDefectCounts.compute(dateKey, (k, v) -> (v == null ? 0L : v) + (log.getDefectCount() != null ? log.getDefectCount() : 0L));
                });

        // TreeMap의 키셋(날짜 문자열)을 순서대로 가져와 MM-dd 형식으로 변환하여 라벨로 사용
        // TreeMap의 값들(불량 개수)을 순서대로 가져와 데이터로 사용
        chartData.put("weeklyDefectTrend", Map.of(
                "labels", weeklyDefectCounts.keySet().stream().map(dateStr -> LocalDate.parse(dateStr, keyFormatter).format(dateFormatter)).collect(Collectors.toList()),
                "data", weeklyDefectCounts.values()
        ));
        logger.debug("Weekly defect trend: {}", weeklyDefectCounts); // logger 사용


        // 3. 연간 불량 감지 추이 (선형 차트 - 최근 12개월 불량률)
        logger.debug("Calculating yearly defect trend (percentage)."); // logger 사용
        Map<String, Long> monthlyDefectCounts = new TreeMap<>(); // 월 순서 유지를 위해 TreeMap 사용 - 불량 개수
        Map<String, Long> monthlyTotalCounts = new TreeMap<>(); // 월 순서 유지를 위해 TreeMap 사용 - 총 생산/검사량
        Map<String, Double> monthlyDefectRates = new TreeMap<>(); // 월 순서 유지를 위해 TreeMap 사용 - 불량률
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate twelveMonthsAgo = today.minusMonths(11).withDayOfMonth(1); // 오늘 포함 최근 12개월의 시작 월

        // 최근 12개월 초기화 (현재 월 포함)
        for (int i = 11; i >= 0; i--) {
            LocalDate date = today.minusMonths(i).withDayOfMonth(1); // 해당 월의 1일로 설정
            String monthKey = date.format(monthFormatter);
            monthlyDefectCounts.put(monthKey, 0L); // 불량 개수 0으로 초기화
            monthlyTotalCounts.put(monthKey, 0L); // 총 수량 0으로 초기화
            monthlyDefectRates.put(monthKey, 0.0); // 불량률 0.0으로 초기화
        }

        // 최근 12개월 불량 개수 계산 및 총 수량 합산
        logs.stream()
                .filter(log -> {
                     // detectionTime이 null이 아닌 경우를 대비하여 처리
                    if (log.getDetectionTime() == null) {
                        logger.warn("Detection log with null detectionTime skipped for yearly trend."); // logger 사용
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    // 최근 12개월 이내의 로그만 필터링 (상태 무관, 총 수량 계산을 위해)
                    return !logDate.isBefore(twelveMonthsAgo);
                })
                .forEach(log -> {
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    String monthKey = logDate.format(monthFormatter);

                    // 해당 월의 불량 개수를 합산 ("Defective" 상태인 경우만)
                    if ("Defective".equals(log.getStatus()) || "Substandard".equals(log.getStatus())) {
                         monthlyDefectCounts.compute(monthKey, (k, v) -> (v == null ? 0L : v) + (log.getDefectCount() != null ? log.getDefectCount() : 0L));
                    }

                    // TODO: 해당 월의 총 생산/검사량 데이터를 합산하는 로직 추가
                    // 이 부분은 DetectionLog 엔티티에 총 수량 정보가 있거나,
                    // 다른 데이터 소스에서 가져와야 합니다.
                    // 예시: DetectionLog에 batchSize 필드가 있다면
                    // monthlyTotalCounts.compute(monthKey, (k, v) -> (v == null ? 0L : v) + (log.getBatchSize() != null ? log.getBatchSize() : 0L));
                    // 또는 별도의 쿼리를 통해 해당 월의 총 생산량을 가져와야 합니다.
                    // 현재는 임시로 각 로그 발생 시 총 수량 1을 더하는 것으로 가정 (실제 로직 필요)
                    monthlyTotalCounts.compute(monthKey, (k, v) -> (v == null ? 0L : v) + 1L); // 실제 로직으로 변경 필요
                });

        // 월별 불량률 계산
        monthlyDefectCounts.forEach((monthKey, defectCountValue) -> {
            long totalItemsValue = monthlyTotalCounts.getOrDefault(monthKey, 0L);
            if (totalItemsValue > 0) {
                // 불량률 계산: (불량 개수 / 총 수량) * 100
                double defectRate = (double) defectCountValue * 100.0 / totalItemsValue;
                monthlyDefectRates.put(monthKey, defectRate);
            } else {
                // 총 수량이 0인 경우 불량률은 0
                monthlyDefectRates.put(monthKey, 0.0);
            }
        });


        // TreeMap의 키셋(월 문자열)을 순서대로 가져와 라벨로 사용
        // TreeMap의 값들(불량률)을 순서대로 가져와 데이터로 사용
        chartData.put("yearlyDefectTrend", Map.of(
                "labels", monthlyDefectRates.keySet(), // 월별 라벨 (불량률 맵의 키셋 사용)
                "data", monthlyDefectRates.values() // 월별 불량률
        ));
        logger.debug("Yearly defect trend (rates): {}", monthlyDefectRates); // logger 사용


        // 4. 당일 감지 상태 비율 (파이 차트)
        logger.debug("Calculating daily status counts."); // logger 사용
        LocalDate todayOnly = LocalDate.now();
        Map<String, Long> dailyStatusCounts = logs.stream()
                .filter(log -> {
                     // detectionTime이 null이 아닌 경우를 대비하여 처리
                    if (log.getDetectionTime() == null) {
                         logger.warn("Detection log with null detectionTime skipped for daily status."); // logger 사용
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    // 당일 로그만 필터링
                    return logDate.isEqual(todayOnly);
                })
                .collect(Collectors.groupingBy(DetectionLog::getStatus, Collectors.counting()));

        // 항상 Normal, Defective 라벨 포함 및 데이터가 없을 경우 0으로 처리
        long todayNormalCount = dailyStatusCounts.getOrDefault("Normal", 0L);
        long todayDefectCount = dailyStatusCounts.getOrDefault("Defective", 0L);
        long todaySubstdCount = dailyStatusCounts.getOrDefault("Substandard", 0L);
        chartData.put("dailyStatus", Map.of(
                "labels", List.of("Normal", "Defective", "Substandard"),
                "data", List.of(todayNormalCount, todayDefectCount, todaySubstdCount)
        ));
        logger.debug("Daily status: Normal={}, Defective={}, Substandard={}", todayNormalCount, todayDefectCount, todaySubstdCount); // logger 사용


        // 5. 당일 작업 완료/미완료 (스택 막대 차트)
        logger.debug("Calculating daily task completion."); // logger 사용
        // 당일 감지된 전체 로그 수 (정상 + 불량 모두 포함)
        long todaysTotalLogs = logs.stream()
                .filter(log -> {
                     // detectionTime이 null이 아닌 경우를 대비하여 처리
                    if (log.getDetectionTime() == null) {
                          logger.warn("Detection log with null detectionTime skipped for daily task completion."); // logger 사용
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    // 당일 로그만 카운트
                    return logDate.isEqual(todayOnly);
                })
                .count();

        long completedTasks = todaysTotalLogs;
        long incompleteTasks = Math.max(0, totalTasks - completedTasks); // 음수가 되지 않도록 처리

        chartData.put("dailyTaskCompletion", Map.of(
                "labels", List.of("오늘 작업"), // 단일 막대
                "datasets", List.of(
                        Map.of("label", "완료", "data", List.of(completedTasks)),
                        Map.of("label", "미완료", "data", List.of(incompleteTasks))
                )
        ));
        logger.debug("Daily task completion: Completed={}, Incomplete={}", completedTasks, incompleteTasks); // logger 사용

        logger.info("Chart data generation complete."); // logger 사용
        return chartData;
    }
}
