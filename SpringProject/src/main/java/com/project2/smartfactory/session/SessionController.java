package com.project2.smartfactory.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpSession;


@Controller
public class SessionController {

  private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

  @GetMapping("/extendSession")
  @ResponseBody
  public ResponseEntity<String> extendSession(HttpSession session) {
    
    session.setMaxInactiveInterval(60 * 60); // 세션 유효 시간을 1시간으로 설정
    logger.info(">>> Received /세션연장 세션ID: {}", session.getId()); // 로그에 세션 ID 기록
    try {
      logger.info("<<< Successfully processed /세션연장 세션ID: {}", session.getId());
      System.out.println("Sesstion extend for ID:" + session.getId()); // 세션 연장 로그 출력
      return ResponseEntity.ok("세션연장에 성공했습니다."); // 세션 연장 성공 메시지
    }catch (Exception e) { 
      logger.error("!!! Error processing /세션연장 세션ID: {}: {}", session.getId(), e.getMessage(), e); // 에러 로그
      return ResponseEntity.internalServerError().body("세션연장에 실패했습니다."); // 세션 연장 실패 메시지
    }
  }
}
