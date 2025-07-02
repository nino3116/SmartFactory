package com.project2.smartfactory.session;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpSession;


@Controller
public class SessionController {

  @GetMapping("/extendSession")
  @ResponseBody
  public ResponseEntity<String> extendSession(HttpSession session) {
    // 세션의 유효 시간을 연장합니다.
    session.setMaxInactiveInterval(5 * 60); // 30분으로 설정
    System.out.println("Sesstion extendfor ID:" + session.getId());
    return ResponseEntity.ok("세션이 연장되었습니다.");
  }
  
  
}
