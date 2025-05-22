package com.project2.smartfactory.control_panel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ctrl")
public class ControlLogController {
  private final ControlLogRepository controlLogRepository;

  public ControlLogController(ControlLogRepository controlLogRepository){
    this.controlLogRepository=controlLogRepository;
  }

  @GetMapping
  Iterable<ControlLog> getControlLogs(){
    return controlLogRepository.findAll();
  }

  @PostMapping
  ControlLog postControlLog(@RequestBody ControlLog controlLog){
    return controlLogRepository.save(controlLog);
  }

  @GetMapping("/system/{action}/{status}")
  String postControlSystem(@PathVariable("action") String action, @PathVariable("status") String status,  Model model){
    String controlResultStatus;
    String controlMemo;
    if(status.equals(action)){
      controlResultStatus = "변화 없음";
      controlMemo = "기존 상태로 요청";
    }else{
      controlResultStatus = status + "→" + ((action=="on")?"running":"stopped");
      controlMemo = "";
    }

    ControlLog controlLog = new ControlLog("User Request", "System "+action, controlResultStatus, controlMemo);
    controlLogRepository.save(controlLog);
    return "pages/control_panel";
  }

  @GetMapping("/logs") // /ctrl/logs 경로로 GET 요청 처리
    public ResponseEntity<List<ControlLog>> getAllControlLogs() {
        System.out.println("--- API 요청 수신 (감지 로그 요청) ---");
        // DefectService를 통해 모든 감지 로그를 가져와 반환합니다.
        List<ControlLog> logs = this.controlLogRepository.findAllByOrderByControlTimeDesc();
        System.out.println("제어 로그 " + (logs != null ? logs.size() : 0) + "건 조회 완료.");
        System.out.println("----------------------------------");
        return new ResponseEntity<>(logs, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환
    }

}
