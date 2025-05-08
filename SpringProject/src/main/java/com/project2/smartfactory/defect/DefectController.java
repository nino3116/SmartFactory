package com.project2.smartfactory.defect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class DefectController {

    // 수신된 불량 정보를 저장할 리스트 (간단한 예제용 인메모리 저장)
    // 여러 스레드에서 접근할 수 있으므로 동기화된 리스트를 사용합니다.
    private List<DefectInfo> latestDefects = Collections.synchronizedList(new ArrayList<>());
    
    @PostMapping("/defect")
    public ResponseEntity<Map<String, String>> receiveDefectData(@RequestBody List<DefectInfo> defectList, HttpServletRequest request) {
        System.out.println("\n--- API 요청 수신 (불량 정보) ---");
        // 수신된 요청 정보 로깅
        System.out.println("요청 메소드: " + request.getMethod());
        System.out.println("요청 URI: " + request.getRequestURI());
        System.out.println("요청 소스 IP: " + request.getRemoteAddr());
        System.out.println("요청 헤더 Content-Type: " + request.getHeader("Content-Type"));
        System.out.println("요청 본문 길이: " + request.getContentLength());

        // 수신된 defectList가 null이 아닌지 확인하고 처리합니다.
        if (defectList != null) {
             System.out.println("수신된 불량 객체 개수: " + defectList.size());

             // 수신된 데이터의 첫 번째 객체 예시 로깅 (디버깅용)
             if (!defectList.isEmpty()) {
                 // DefectInfo 클래스에 toString() 메소드가 구현되어 있어야 유용합니다.
                 System.out.println("첫 번째 수신 불량 객체 예시: " + defectList.get(0).toString());
             }


             // 수신된 불량 정보 리스트를 인메모리 리스트에 저장 (기존 데이터는 덮어씀)
             // 실제 애플리케이션에서는 데이터베이스에 저장하는 등의 로직이 필요합니다.
             latestDefects.clear(); // 이전 데이터 삭제
             latestDefects.addAll(defectList); // 새 데이터 추가

             // 수신된 불량 정보 리스트를 순회하며 각 불량 정보를 출력하거나 다른 로직을 수행합니다.
             for (DefectInfo defect : defectList) {
                 System.out.println("  - 클래스: " + defect.getClazz() +
                                    ", 사유: " + defect.getReason() +
                                    ", 신뢰도: " + defect.getConfidence());
                 // 예: 수신된 불량 정보를 데이터베이스에 저장하거나, 다른 시스템으로 전달하는 로직 추가
             }

             System.out.println("요청 처리 성공. 응답 상태 코드: 200 OK"); // 성공 로그
             System.out.println("----------------------------------");
             // 클라이언트에게 성공 JSON 응답을 보냅니다.
             Map<String, String> responseBody = new HashMap<>();
             responseBody.put("status", "success");
             responseBody.put("message", "Defect data received successfully");

             return new ResponseEntity<>(responseBody, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환

        }  else {
            // defectList가 null인 경우 (요청 본문이 비어있거나 JSON 파싱 실패 등)
            System.out.println("수신된 불량 정보 리스트가 null입니다. 요청 본문이 비어있거나 JSON 형식이 잘못되었을 수 있습니다.");
            System.out.println("요청 처리 실패. 응답 상태 코드: 400 Bad Request"); // 실패 로그
            System.out.println("----------------------------------");
            // 클라이언트에게 오류 JSON 응답을 보냅니다.
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", "Invalid or empty request body or JSON parsing failed");
            return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST); // 상태 코드 400 Bad Request 반환
       }


    }

    // 최신 불량 정보를 제공하는 GET 엔드포인트를 정의합니다.
    // 웹 페이지에서 이 엔드포인트를 호출하여 불량 정보를 가져갑니다.
    @GetMapping("/latest-defects")
    public ResponseEntity<List<DefectInfo>> getLatestDefects() {
        System.out.println("--- API 요청 수신 (최신 불량 정보 요청) ---");
        // 저장된 최신 불량 정보 리스트를 반환합니다.
        // 동기화된 리스트이므로 안전하게 접근 가능합니다.
        return new ResponseEntity<>(new ArrayList<>(latestDefects), HttpStatus.OK); // 새로운 리스트로 복사하여 반환
    }


}
