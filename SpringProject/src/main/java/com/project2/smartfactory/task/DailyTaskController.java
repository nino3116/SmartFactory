package com.project2.smartfactory.task; // DailyTaskProgress, DailyTaskProgressDto와 동일한 패키지 사용

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project2.smartfactory.task.DailyTaskProgressDto;
import com.project2.smartfactory.task.DailyTaskProgress;
import com.project2.smartfactory.task.DailyTaskProgressRepository; // 리포지토리 import
import com.project2.smartfactory.defect.DetectionLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 당일 작업 진척 상황을 관리하는 REST 컨트롤러입니다.
 * 총 작업량 설정 및 현재 진척 상황 조회를 제공합니다.
 */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost", "http://192.168.0.122", "http://192.168.0.124"}, maxAge = 3600)
@Slf4j
public class DailyTaskController {

    private final DailyTaskProgressRepository dailyTaskProgressRepository;
    private final DetectionLogService detectionLogService;

    // 현재는 단일 사용자 'admin'을 가정합니다.
    // 실제 애플리케이션에서는 사용자 인증을 통해 동적으로 userId를 가져와야 합니다.
    private static final String DEFAULT_USER_ID = "admin";

    /**
     * 당일 총 작업량을 설정합니다.
     * 기존에 해당 날짜와 사용자의 진척 상황이 있다면 총 작업량을 업데이트하고,
     * 없다면 새로운 진척 상황 엔티티를 생성하여 저장합니다.
     *
     * @param requestDto 총 작업량 정보를 담은 DTO
     * @return 설정된 총 작업량 정보가 포함된 응답 (DailyTaskProgressDto)
     */
    @PostMapping("/set-total")
    public ResponseEntity<DailyTaskProgressDto> setDailyTotalTasks(@RequestBody DailyTaskProgressDto requestDto) {
        log.info("Setting daily total tasks for user '{}' to: {}", DEFAULT_USER_ID, requestDto.getDailyTotalTasks());
        LocalDate today = LocalDate.now();

        // userId와 recordDate로 기존 진행 상황 조회
        Optional<DailyTaskProgress> existingProgress = dailyTaskProgressRepository.findByUserIdAndRecordDate(DEFAULT_USER_ID, today);

        DailyTaskProgress progress;
        if (existingProgress.isPresent()) {
            // 기존 진척 상황이 있다면 업데이트
            progress = existingProgress.get();
            progress.setDailyTotalTasks(requestDto.getDailyTotalTasks());
            log.debug("Updating existing daily task progress for user '{}' on {}: new total tasks = {}", DEFAULT_USER_ID, today, requestDto.getDailyTotalTasks());
        } else {
            // 기존 진척 상황이 없다면 새로 생성
            progress = new DailyTaskProgress();
            progress.setUserId(DEFAULT_USER_ID);
            progress.setRecordDate(today);
            progress.setDailyTotalTasks(requestDto.getDailyTotalTasks());
            progress.setCompletedTasks(0); // 초기값 0으로 설정
            log.debug("Creating new daily task progress for user '{}' on {}: total tasks = {}", DEFAULT_USER_ID, today, requestDto.getDailyTotalTasks());
        }
        
        DailyTaskProgress savedProgress = dailyTaskProgressRepository.save(progress);
        log.info("Daily total tasks saved successfully: {}", savedProgress.getDailyTotalTasks());

        // 저장된 엔티티를 DTO로 변환하여 반환
        DailyTaskProgressDto responseDto = new DailyTaskProgressDto();
        responseDto.setRecordDate(savedProgress.getRecordDate());
        responseDto.setDailyTotalTasks(savedProgress.getDailyTotalTasks());
        responseDto.setCompletedTasks(savedProgress.getCompletedTasks()); // DB에 저장된 completedTasks 사용
        
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 당일 공정 진척상황을 조회합니다.
     * 완료된 작업 개수는 DetectionLogService에서 당일 감지된 총 개수를 가져와 동적으로 계산됩니다.
     *
     * @return 당일 공정 진척상황 정보가 포함된 응답 (DailyTaskProgressDto)
     */
    @GetMapping("/daily-current")
    public ResponseEntity<DailyTaskProgressDto> getDailyCurrentProgress() {
        LocalDate today = LocalDate.now();
        // userId와 recordDate로 기존 진행 상황 조회
        Optional<DailyTaskProgress> existingProgress = dailyTaskProgressRepository.findByUserIdAndRecordDate(DEFAULT_USER_ID, today);

        long dailyTotalTasks = 0L;
        if (existingProgress.isPresent()) {
            // 기존 진척 상황이 있다면 총 작업량 가져오기
            dailyTotalTasks = existingProgress.get().getDailyTotalTasks();
            log.debug("Retrieved existing daily total tasks for user '{}' on {}: {}", DEFAULT_USER_ID, today, dailyTotalTasks);
        } else {
            log.debug("No existing daily total tasks found for user '{}' on {}. Defaulting to 0.", DEFAULT_USER_ID, today);
        }

        // DetectionLogService에서 모든 차트 데이터를 가져옵니다.
        // 이때, dailyTotalTasks 값을 전달하여 dailyTaskCompletion 차트 계산에 사용되도록 합니다.
        Map<String, Object> allChartData = detectionLogService.getChartData((int) dailyTotalTasks);
        
        long completedTasksFromChart = 0L;
        // 차트 데이터에서 "dailyTaskCompletion" 섹션의 "완료"된 작업 개수를 추출
        if (allChartData.containsKey("dailyTaskCompletion")) {
            Map<String, Object> dailyTaskCompletion = (Map<String, Object>) allChartData.get("dailyTaskCompletion");
            if (dailyTaskCompletion.containsKey("datasets")) {
                List<Map<String, Object>> datasets = (List<Map<String, Object>>) dailyTaskCompletion.get("datasets");
                for (Map<String, Object> dataset : datasets) {
                    if ("완료".equals(dataset.get("label"))) {
                        List<Long> dataList = (List<Long>) dataset.get("data");
                        if (!dataList.isEmpty()) {
                            completedTasksFromChart = dataList.get(0); // "완료" 데이터셋의 첫 번째 값 (오늘 작업)
                            break;
                        }
                    }
                }
            }
        }
        log.debug("Todays completed tasks calculated from chart data: {}", completedTasksFromChart);

        // DTO를 생성하여 반환
        DailyTaskProgressDto responseDto = new DailyTaskProgressDto();
        responseDto.setRecordDate(today);
        responseDto.setDailyTotalTasks((int) dailyTotalTasks); // long을 int로 캐스팅
        responseDto.setCompletedTasks((int) completedTasksFromChart); // long을 int로 캐스팅
        
        log.info("Returning daily progress: RecordDate={}, TotalTasks={}, CompletedTasks={}", 
                responseDto.getRecordDate(), responseDto.getDailyTotalTasks(), responseDto.getCompletedTasks());
        return ResponseEntity.ok(responseDto);
    }
}
