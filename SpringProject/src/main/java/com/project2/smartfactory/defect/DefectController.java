package com.project2.smartfactory.defect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService; // DefectService 주입

    /**
     * 파이썬 스크립트로부터 불량 정보를 수신하여 처리합니다.
     * 수신된 정보는 DefectService를 통해 데이터베이스에 저장됩니다.
     * @param defectList 파이썬 스크립트로부터 수신된 불량 정보 리스트
     * @param request HTTP 요청 정보
     * @return 처리 결과에 대한 응답 (성공 또는 실패)
     */
    @PostMapping("/defect")
    public ResponseEntity<Map<String, String>> receiveDefectData(@RequestBody List<DefectInfo> defectList, HttpServletRequest request) {
        System.out.println("\n--- API 요청 수신 (불량 정보) ---");
        // 수신된 요청 정보 로깅
        System.out.println("요청 메소드: " + request.getMethod());
        System.out.println("요청 URI: " + request.getRequestURI());
        System.out.println("요청 소스 IP: " + request.getRemoteAddr());
        System.out.println("요청 헤더 Content-Type: " + request.getHeader("Content-Type"));
        System.out.println("요청 본문 길이: " + request.getContentLength());

        // 수신된 defectList가 null이거나 비어있지 않은지 확인하고 처리합니다.
        // 불량 정보가 없을 경우에도 로그는 기록해야 하므로, 서비스 호출은 항상 수행합니다.
        // 서비스 내부에서 리스트의 비어있음 여부를 판단하여 불량 상세 정보 저장을 스킵합니다.

        try {
            // DefectService를 통해 불량 정보를 데이터베이스에 저장 및 처리 (로그 포함)
            defectService.processAndSaveDefects(defectList);
            System.out.println("불량 정보 DefectService로 전달 완료.");

            System.out.println("요청 처리 성공. 응답 상태 코드: 200 OK"); // 성공 로그
            System.out.println("----------------------------------");
            // 클라이언트에게 성공 JSON 응답을 보냅니다.
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("status", "success");
            responseBody.put("message", "Defect data received and processed successfully");

            return new ResponseEntity<>(responseBody, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환

        } catch (Exception e) {
            // DefectService 처리 중 오류 발생 시
            System.err.println("불량 정보 처리 중 오류 발생 (DefectService): " + e.getMessage());
            e.printStackTrace(); // 오류 스택 트레이스 출력

            System.out.println("요청 처리 실패. 응답 상태 코드: 500 Internal Server Error"); // 실패 로그
            System.out.println("----------------------------------");
            // 클라이언트에게 오류 JSON 응답을 보냅니다.
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", "Failed to process defect data: " + e.getMessage());
            return new ResponseEntity<>(errorBody, HttpStatus.INTERNAL_SERVER_ERROR); // 상태 코드 500 반환
        }
    }

    /**
     * 저장된 최신 불량 정보를 JSON 형태로 제공합니다.
     * 웹 페이지에서 이 엔드포인트를 호출하여 불량 정보를 가져갑니다.
     * 이 정보는 DefectService에서 관리하는 최신 불량 목록입니다.
     * @return 최신 불량 정보 리스트
     */
    @GetMapping("/latest-defects")
    public ResponseEntity<List<DefectInfo>> getLatestDefects() {
        System.out.println("--- API 요청 수신 (최신 불량 정보 요청) ---");
        // DefectService를 통해 최신 불량 정보 리스트를 가져와 반환합니다.
        List<DefectInfo> latestDefects = defectService.getLatestDefects();
        System.out.println("최신 불량 정보 " + latestDefects.size() + "건 조회 완료.");
        System.out.println("----------------------------------");
        return new ResponseEntity<>(latestDefects, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환
    }

    /**
     * 데이터베이스에 저장된 모든 감지 로그를 최신 순으로 JSON 형태로 제공합니다.
     * dashboard.html의 감지 결과 로그 테이블에서 이 엔드포인트를 호출합니다.
     * @return 감지 로그 리스트
     */
    @GetMapping("/detection-logs") // /api/detection-logs 경로로 GET 요청 처리
    public ResponseEntity<List<DetectionLog>> getAllDetectionLogs() {
        System.out.println("--- API 요청 수신 (감지 로그 요청) ---");
        // DefectService를 통해 모든 감지 로그를 가져와 반환합니다.
        List<DetectionLog> logs = defectService.getAllDetectionLogs();
        System.out.println("감지 로그 " + logs.size() + "건 조회 완료.");
        System.out.println("----------------------------------");
        return new ResponseEntity<>(logs, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환
    }

    // 필요에 따라 특정 기간의 불량 정보 조회 등 추가 API 엔드포인트를 구현할 수 있습니다.
    // 예를 들어 차트 데이터 제공을 위한 엔드포인트:
    // @GetMapping("/defects-by-date")
    // public ResponseEntity<List<DefectInfo>> getDefectsByDateRange(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
    //                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
    //     System.out.println("--- API 요청 수신 (기간별 불량 정보 요청) ---");
    //     List<DefectInfo> defects = defectService.getDefectsByDateRange(startDate, endDate);
    //     System.out.println("기간별 불량 정보 " + defects.size() + "건 조회 완료.");
    //     System.out.println("----------------------------------");
    //     return new ResponseEntity<>(defects, HttpStatus.OK);
    // }

}
