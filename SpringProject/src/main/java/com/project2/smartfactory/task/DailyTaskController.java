package com.project2.smartfactory.task;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DailyTaskController { // 클래스 이름 변경

    private final DailyTaskProgressRepository dailyTaskProgressRepository;

    private static final String ADMIN_USER_ID = "admin";

    /**
     * 당일의 총 작업량을 설정하고 저장하는 API 엔드포인트.
     * 클라이언트에서 POST 요청으로 dailyTotalTasks 값을 전송합니다.
     *
     * @param requestBody dailyTotalTasks 값을 포함하는 JSON 요청 본문
     * @return 성공 또는 실패 메시지를 포함하는 응답
     */
    @PostMapping("/settings/daily-total-tasks") // 엔드포인트 이름 변경
    public ResponseEntity<Map<String, String>> setDailyTotalTasks(
            @RequestBody DailyTaskProgressDto requestBody) { // DTO 타입 변경
        Map<String, String> response = new HashMap<>();
        LocalDate today = LocalDate.now();

        if (requestBody.getDailyTotalTasks() < 0) {
            response.put("message", "총 작업량은 0 이상이어야 합니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            // 오늘 날짜의 DailyTaskProgress 엔티티를 찾거나 새로 생성합니다.
            DailyTaskProgress dailyProgress = dailyTaskProgressRepository.findByUserIdAndRecordDate(ADMIN_USER_ID, today)
                    .orElse(new DailyTaskProgress(ADMIN_USER_ID, today, 0, 0)); // 없으면 새 엔티티 생성, 완료량은 0

            dailyProgress.setDailyTotalTasks(requestBody.getDailyTotalTasks());
            dailyTaskProgressRepository.save(dailyProgress); // 저장 또는 업데이트

            response.put("message", "당일 총 작업량이 성공적으로 저장되었습니다.");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("당일 총 작업량 저장 중 오류 발생: " + e.getMessage());
            response.put("message", "당일 총 작업량 저장에 실패했습니다.");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 당일의 총 작업량과 완료된 작업량을 조회하는 API 엔드포인트.
     *
     * @return dailyTotalTasks와 completedTasks를 포함하는 응답
     */
    @GetMapping("/progress/daily-current") // 엔드포인트 이름 변경
    public ResponseEntity<DailyTaskProgressDto> getDailyCurrentProgress() {
        LocalDate today = LocalDate.now();

        // 오늘 날짜의 DailyTaskProgress 엔티티를 조회합니다.
        Optional<DailyTaskProgress> dailyProgressOptional = dailyTaskProgressRepository.findByUserIdAndRecordDate(ADMIN_USER_ID, today);

        if (dailyProgressOptional.isPresent()) {
            DailyTaskProgress dailyProgress = dailyProgressOptional.get();
            DailyTaskProgressDto responseDto = new DailyTaskProgressDto(
                    dailyProgress.getRecordDate(), // 날짜 정보도 포함
                    dailyProgress.getDailyTotalTasks(),
                    dailyProgress.getCompletedTasks()
            );
            return new ResponseEntity<>(responseDto, HttpStatus.OK);
        } else {
            // 해당 userId의 오늘 날짜 데이터가 없으면 기본값 (0, 0)을 반환합니다.
            // 클라이언트가 이 정보를 바탕으로 초기 UI를 구성할 수 있습니다.
            return new ResponseEntity<>(new DailyTaskProgressDto(today, 0, 0), HttpStatus.OK);
        }
    }

    /**
     * 당일 완료된 작업량을 1 증가시키는 API 엔드포인트.
     * 이 엔드포인트는 파이썬 스크립트에서 불량 감지 완료 시 호출될 수 있습니다.
     *
     * @return 성공 또는 실패 메시지를 포함하는 응답
     */
    @PostMapping("/progress/increment")
    public ResponseEntity<Map<String, String>> incrementCompletedTasks() {
        Map<String, String> response = new HashMap<>();
        LocalDate today = LocalDate.now(); // 오늘 날짜 가져오기

        try {
            // 오늘 날짜의 DailyTaskProgress 엔티티를 찾거나 새로 생성합니다.
            // 만약 총 작업량이 설정되지 않았다면 0으로 초기화됩니다.
            DailyTaskProgress dailyProgress = dailyTaskProgressRepository.findByUserIdAndRecordDate(ADMIN_USER_ID, today)
                    .orElse(new DailyTaskProgress(ADMIN_USER_ID, today, 0, 0)); // 없으면 새로 생성

            // 현재 완료된 작업량을 1 증가시킵니다.
            dailyProgress.setCompletedTasks(dailyProgress.getCompletedTasks() + 1);
            dailyTaskProgressRepository.save(dailyProgress);

            response.put("message", "완료된 작업량이 성공적으로 증가되었습니다.");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("완료된 작업량 증가 중 오류 발생: " + e.getMessage());
            response.put("message", "완료된 작업량 증가에 실패했습니다.");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 특정 기간 동안의 일자별 작업량 데이터를 조회하는 API 엔드포인트.
     *
     * @param startDate 조회 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate 조회 종료 날짜 (YYYY-MM-DD 형식)
     * @return 해당 기간의 일자별 작업량 리스트
     */
    @GetMapping("/progress/daily-range") // 엔드포인트 이름 변경
    public ResponseEntity<List<DailyTaskProgress>> getDailyProgressRange(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            // 시작 날짜가 종료 날짜보다 늦으면 오류
            if (start.isAfter(end)) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            List<DailyTaskProgress> dailyProgressList =
                    dailyTaskProgressRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(ADMIN_USER_ID, start, end);

            return new ResponseEntity<>(dailyProgressList, HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("일자별 작업량 조회 중 오류 발생: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



}
