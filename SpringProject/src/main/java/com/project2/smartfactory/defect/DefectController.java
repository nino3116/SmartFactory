package com.project2.smartfactory.defect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@CrossOrigin(origins = {"http://localhost", "http://192.168.0.122", "http://192.168.0.124"}, maxAge = 3600) // 컨트롤러의 모든 메서드에 적용
public class DefectController {

    private final DefectService defectService; // DefectService 주입

    /**
     * 파이썬 스크립트로부터 불량 감지 결과를 수신하여 처리합니다.
     * 수신된 정보는 DetectionResultDto 객체에 담겨 있으며, DefectService를 통해 데이터베이스에 저장됩니다.
     * @param detectionResultDto 파이썬 스크립트로부터 수신된 감지 결과 (로그 정보 및 불량 목록 포함)
     * @param request HTTP 요청 정보
     * @return 처리 결과에 대한 응답 (성공 또는 실패)
     */
    @PostMapping("/defect") // /api/defect 경로로 POST 요청 처리
    public ResponseEntity<Map<String, String>> receiveDetectionResult(@RequestBody DetectionResultDto detectionResultDto, HttpServletRequest request) { // 인자 타입 변경
        System.out.println("\n--- API 요청 수신 (감지 결과) ---");
        // 수신된 요청 정보 로깅
        System.out.println("요청 메소드: " + request.getMethod());
        System.out.println("요청 URI: " + request.getRequestURI());
        System.out.println("요청 소스 IP: " + request.getRemoteAddr());
        System.out.println("요청 헤더 Content-Type: " + request.getHeader("Content-Type"));
        // 요청 본문 길이는 @RequestBody 사용 시 직접 얻기 어려울 수 있습니다.

        // 수신된 detectionResultDto가 null이 아닌지 확인하고 처리합니다.
        if (detectionResultDto == null) {
            System.err.println("수신된 감지 결과 데이터가 null입니다.");
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", "Received detection result data is null");
            return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
        }

        System.out.println("수신된 감지 결과: " + detectionResultDto.toString()); // 수신된 DTO 로깅

        try {
            // DefectService를 통해 감지 결과를 데이터베이스에 저장 및 처리 (로그 및 상세 불량)
            // DefectService의 processAndSaveDefects 메소드를 DetectionResultDto를 받도록 수정해야 합니다.
            defectService.processAndSaveDetectionResult(detectionResultDto); // 메소드 이름 및 인자 변경 제안
            System.out.println("감지 결과 DefectService로 전달 완료.");

            System.out.println("요청 처리 성공. 응답 상태 코드: 200 OK"); // 성공 로그
            System.out.println("----------------------------------");
            // 클라이언트에게 성공 JSON 응답을 보냅니다.
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("status", "success");
            responseBody.put("message", "Detection result received and processed successfully");

            return new ResponseEntity<>(responseBody, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환

        } catch (Exception e) {
            // DefectService 처리 중 오류 발생 시
            System.err.println("감지 결과 처리 중 오류 발생 (DefectService): " + e.getMessage());
            e.printStackTrace(); // 오류 스택 트레이스 출력

            System.out.println("요청 처리 실패. 응답 상태 코드: 500 Internal Server Error"); // 실패 로그
            System.out.println("----------------------------------");
            // 클라이언트에게 오류 JSON 응답을 보냅니다.
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", "Failed to process detection result: " + e.getMessage());
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
        // DefectService의 getLatestDefects 메소드는 List<DefectInfo>를 반환하도록 유지합니다.
        
        List<DefectInfo> latestDefects = defectService.getLatestDefects();
        System.out.println("최신 불량 정보 " + (latestDefects != null ? latestDefects.size() : 0) + "건 조회 완료.");
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
        System.out.println("감지 로그 " + (logs != null ? logs.size() : 0) + "건 조회 완료.");
        System.out.println("----------------------------------");
        return new ResponseEntity<>(logs, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환
    }

    // 필요에 따라 특정 기간의 불량 정보 조회 등 추가 API 엔드포인트를 구현할 수 있습니다.

}
