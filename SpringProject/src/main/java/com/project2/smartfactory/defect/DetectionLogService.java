package com.project2.smartfactory.defect;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DetectionLogService {

    private static final Logger logger = LoggerFactory.getLogger(DetectionLogService.class);

    private final DetectionLogRepository detectionLogRepository;

    public List<DetectionLog> getAllDetectionLogs() {
        logger.info("Fetching all detection logs from repository.");
        try {
            List<DetectionLog> logs = detectionLogRepository.findAll();
            logger.info("Successfully fetched {} detection logs.", logs.size());
            return logs;
        } catch (Exception e) {
            logger.error("Error fetching detection logs from repository", e);
            return List.of();
        }
    }

    public Map<String, Object> getChartData(int totalTasks) {
        logger.info("Generating chart data with totalTasks: {}", totalTasks);
        List<DetectionLog> logs = getAllDetectionLogs();

        if (logs.isEmpty()) {
            logger.warn("No detection logs found. Returning empty chart data.");
            return Map.of(
                 "overallStatus", Map.of("labels", List.of("Normal", "Defective","Substandard"), "data", List.of(0L, 0L, 0L)),
                 "weeklyDefectTrend", Map.of("labels", List.of(), "data", List.of()),
                 "yearlyDefectTrend", Map.of("labels", List.of(), "data", List.of()),
                 "monthlyDefectTrend", Map.of("labels", List.of(), "data", List.of()), // 월간 데이터 추가
                 "dailyStatus", Map.of("labels", List.of("Normal", "Defective","Substandard"), "data", List.of(0L, 0L, 0L)),
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
        logger.debug("Calculating overall status counts.");
        Map<String, Long> overallStatusCounts = logs.stream()
                .filter(log -> log.getStatus() != null)
                .collect(Collectors.groupingBy(DetectionLog::getStatus, Collectors.counting()));

        long normalCount = overallStatusCounts.getOrDefault("Normal", 0L);
        long defectCount = overallStatusCounts.getOrDefault("Defective", 0L);
        long substdCount = overallStatusCounts.getOrDefault("Substandard", 0L);
        chartData.put("overallStatus", Map.of(
                "labels", List.of("Normal", "Defective","Substandard"),
                "data", List.of(normalCount, defectCount, substdCount)
        ));
        logger.debug("Overall status: Normal={}, Defective={}, Substandard={}", normalCount, defectCount, substdCount);


        // 2. 주간 불량 감지 추이 (막대 차트 - 최근 7일 불량 개수)
        logger.debug("Calculating weekly defect trend.");
        LocalDate today = LocalDate.now();
        Map<String, Long> weeklyDefectCounts = new TreeMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            weeklyDefectCounts.put(date.format(keyFormatter), 0L);
        }

        logs.stream()
                .filter(log -> {
                    if (log.getDetectionTime() == null) {
                        logger.warn("Detection log with null detectionTime skipped for weekly trend.");
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    return !logDate.isBefore(today.minusDays(6)) && ("Defective".equals(log.getStatus()) || "Substandard".equals(log.getStatus()));
                })
                .forEach(log -> {
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    String dateKey = logDate.format(keyFormatter);
                    weeklyDefectCounts.compute(dateKey, (k, v) -> (v == null ? 0L : v) + (log.getDefectCount() != null ? log.getDefectCount() : 0L));
                });

        chartData.put("weeklyDefectTrend", Map.of(
                "labels", weeklyDefectCounts.keySet().stream().map(dateStr -> LocalDate.parse(dateStr, keyFormatter).format(dateFormatter)).collect(Collectors.toList()),
                "data", weeklyDefectCounts.values()
        ));
        logger.debug("Weekly defect trend: {}", weeklyDefectCounts);


        // 3. 연간 불량 감지 추이 (선형 차트 - 최근 5년 불량률)
        logger.debug("Calculating yearly defect trend (percentage).");
        Map<String, Long> yearlyDefectCounts = new TreeMap<>(); // 연도별 불량 개수
        Map<String, Long> yearlyTotalCounts = new TreeMap<>(); // 연도별 총 수량
        DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy");
        LocalDate fiveYearsAgo = today.minusYears(4).withDayOfYear(1); // 오늘 포함 최근 5년의 시작 연도

        // 최근 5년 초기화 (현재 연도 포함)
        for (int i = 4; i >= 0; i--) {
            LocalDate date = today.minusYears(i).withDayOfYear(1);
            String yearKey = date.format(yearFormatter);
            yearlyDefectCounts.put(yearKey, 0L);
            yearlyTotalCounts.put(yearKey, 0L);
        }

        logs.stream()
                .filter(log -> {
                    if (log.getDetectionTime() == null) {
                        logger.warn("Detection log with null detectionTime skipped for yearly trend.");
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    return !logDate.isBefore(fiveYearsAgo);
                })
                .forEach(log -> {
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    String yearKey = logDate.format(yearFormatter);

                    if ("Defective".equals(log.getStatus()) || "Substandard".equals(log.getStatus())) {
                         yearlyDefectCounts.compute(yearKey, (k, v) -> (v == null ? 0L : v) + (log.getDefectCount() != null ? log.getDefectCount() : 0L));
                    }
                    yearlyTotalCounts.compute(yearKey, (k, v) -> (v == null ? 0L : v) + 1L); // 각 로그를 1개로 간주, 실제 총 수량 로직 필요
                });

        Map<String, Double> yearlyDefectRates = new TreeMap<>();
        yearlyDefectCounts.forEach((yearKey, defectCountValue) -> {
            long totalItemsValue = yearlyTotalCounts.getOrDefault(yearKey, 0L);
            if (totalItemsValue > 0) {
                double defectRate = (double) defectCountValue * 100.0 / totalItemsValue;
                yearlyDefectRates.put(yearKey, Math.round(defectRate * 10.0) / 10.0); // 소수점 첫째 자리까지 반올림
            } else {
                yearlyDefectRates.put(yearKey, 0.0);
            }
        });

        chartData.put("yearlyDefectTrend", Map.of(
                "labels", yearlyDefectRates.keySet(),
                "data", yearlyDefectRates.values()
        ));
        logger.debug("Yearly defect trend (rates): {}", yearlyDefectRates);


        // 4. 월간 불량 감지 추이 (막대 차트 - 최근 12개월 불량률) - 수정됨
        logger.debug("Calculating monthly defect trend (percentage).");
        Map<String, Long> monthlyDefectCounts = new TreeMap<>(); // 월별 불량 개수
        Map<String, Long> monthlyTotalCounts = new TreeMap<>(); // 월별 총 감지 개수
        DateTimeFormatter monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter monthLabelFormatter = DateTimeFormatter.ofPattern("yy.MM");
        // 새로운 포맷터 추가: yyyy-MM-dd 형식의 문자열을 파싱하기 위함
        DateTimeFormatter fullMonthDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate twelveMonthsAgo = today.minusMonths(11).withDayOfMonth(1);

        // 최근 12개월 초기화
        for (int i = 11; i >= 0; i--) {
            LocalDate date = today.minusMonths(i).withDayOfMonth(1);
            String monthKey = date.format(monthKeyFormatter);
            monthlyDefectCounts.put(monthKey, 0L);
            monthlyTotalCounts.put(monthKey, 0L);
        }

        logs.stream()
                .filter(log -> {
                    if (log.getDetectionTime() == null) {
                        logger.warn("Detection log with null detectionTime skipped for monthly trend.");
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    return !logDate.isBefore(twelveMonthsAgo); // 최근 12개월 데이터만 필터링
                })
                .forEach(log -> {
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    String monthKey = logDate.format(monthKeyFormatter);

                    // 불량 항목만 카운트
                    if ("Defective".equals(log.getStatus()) || "Substandard".equals(log.getStatus())) {
                        monthlyDefectCounts.compute(monthKey, (k, v) -> (v == null ? 0L : v) + (log.getDefectCount() != null ? log.getDefectCount() : 0L));
                    }
                    // 총 감지 항목 수 카운트 (정상, 불량, 미흡 모두 포함)
                    monthlyTotalCounts.compute(monthKey, (k, v) -> (v == null ? 0L : v) + 1L);
                });

        Map<String, Double> monthlyDefectRates = new TreeMap<>();
        monthlyDefectCounts.forEach((monthKey, defectCountValue) -> {
            long totalItemsValue = monthlyTotalCounts.getOrDefault(monthKey, 0L);
            if (totalItemsValue > 0) {
                double defectRate = (double) defectCountValue * 100.0 / totalItemsValue;
                monthlyDefectRates.put(monthKey, Math.round(defectRate * 10.0) / 10.0); // 소수점 첫째 자리까지 반올림
            } else {
                monthlyDefectRates.put(monthKey, 0.0);
            }
        });

        chartData.put("monthlyDefectTrend", Map.of(
                "labels", monthlyDefectRates.keySet().stream()
                        // monthStr + "-01"을 파싱할 때 fullMonthDateFormatter를 사용
                        .map(monthStr -> LocalDate.parse(monthStr + "-01", fullMonthDateFormatter).format(monthLabelFormatter))
                        .collect(Collectors.toList()),
                "data", monthlyDefectRates.values()
        ));
        logger.debug("Monthly defect trend (rates): {}", monthlyDefectRates);


        // 5. 당일 감지 상태 비율 (파이 차트)
        logger.debug("Calculating daily status counts.");
        LocalDate todayOnly = LocalDate.now();
        Map<String, Long> dailyStatusCounts = logs.stream()
                .filter(log -> {
                    if (log.getDetectionTime() == null) {
                         logger.warn("Detection log with null detectionTime skipped for daily status.");
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    return logDate.isEqual(todayOnly);
                })
                .collect(Collectors.groupingBy(DetectionLog::getStatus, Collectors.counting()));

        long todayNormalCount = dailyStatusCounts.getOrDefault("Normal", 0L);
        long todayDefectCount = dailyStatusCounts.getOrDefault("Defective", 0L);
        long todaySubstdCount = dailyStatusCounts.getOrDefault("Substandard", 0L);
        chartData.put("dailyStatus", Map.of(
                "labels", List.of("Normal", "Defective", "Substandard"),
                "data", List.of(todayNormalCount, todayDefectCount, todaySubstdCount)
        ));
        logger.debug("Daily status: Normal={}, Defective={}, Substandard={}", todayNormalCount, todayDefectCount, todaySubstdCount);


        // 6. 당일 작업 완료/미완료 (스택 막대 차트)
        logger.debug("Calculating daily task completion.");
        long todaysTotalLogs = logs.stream()
                .filter(log -> {
                    if (log.getDetectionTime() == null) {
                          logger.warn("Detection log with null detectionTime skipped for daily task completion.");
                        return false;
                    }
                    LocalDate logDate = log.getDetectionTime().toLocalDate();
                    return logDate.isEqual(todayOnly);
                })
                .count();

        long completedTasks = todaysTotalLogs;
        long incompleteTasks = Math.max(0, totalTasks - completedTasks);

        chartData.put("dailyTaskCompletion", Map.of(
                "labels", List.of("오늘 작업"),
                "datasets", List.of(
                        Map.of("label", "완료", "data", List.of(completedTasks)),
                        Map.of("label", "미완료", "data", List.of(incompleteTasks))
                )
        ));
        logger.debug("Daily task completion: Completed={}, Incomplete={}", completedTasks, incompleteTasks);

        logger.info("Chart data generation complete.");
        return chartData;
    }
}