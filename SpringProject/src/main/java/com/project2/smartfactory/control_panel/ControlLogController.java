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

  @GetMapping("/system/{action}/{status}/{result}")
  String getControlSystem(@PathVariable("action") String action, @PathVariable("status") String status, @PathVariable("result") String result,  Model model){
    // String controlType = "";
    // String controlData = "";
    // String controlResultStatus = "";
    // String controlMemo = "";
    
    // if(result.equals("Unknown")){
    //   controlMemo = "상태 확인 불가";
    // }

    // if(action.equals("check")){
    //   controlType = "System Check";

    //   if(status.equals("detect_error")){
    //     controlData = "Error Detected";
    //     controlResultStatus = result;
    //   }else{
    //     controlData = "Something Wrong";
    //   }
    // }else if(action.equals("detect_change")){
    //   controlType = "System Check";
    //   controlData = "Change Detected";
    //   controlResultStatus = status + "→" + result;
    // }else{
    //   controlType = "User Request";
    //   controlData = "System "+action;
    //   if(result.equals(status)){
    //     controlResultStatus = "변화 없음("+result+")";
    //   }else{
    //     controlResultStatus = status + "→" + result;
    //   }

    //   if((action.equals("on") && status.equals("running"))||(action.equals("off") && status.equals("stopped"))){
    //     controlMemo += "기존 상태로 요청";
    //   }else if((action.equals("on")&&result.equals("running"))||(action.equals("off")&&result.equals("off"))){
    //     controlMemo += "정상 작동";
    //   }else{
    //     controlMemo += (controlMemo.equals(""))?"":" / ";
    //     controlMemo += "오작동: 상태 확인 요망";
    //   }
    // }

    // ControlLog controlLog = new ControlLog(controlType, controlData, controlResultStatus, controlMemo);
    // controlLogRepository.save(controlLog);
    return "pages/control_panel";
  }

  public boolean createControlLog(ControlLog controlLog){
    try{
      controlLogRepository.save(controlLog);
      return true;
    }catch(Exception e){
      return false;
    }
  }

  @GetMapping("/logs") // /ctrl/logs 경로로 GET 요청 처리
  public ResponseEntity<List<ControlLog>> getAllControlLogs() {
    System.out.println("--- API 요청 수신 (감지 로그 요청) ---");
    List<ControlLog> logs = this.controlLogRepository.findAllByOrderByControlTimeDesc();
    System.out.println("제어 로그 " + (logs != null ? logs.size() : 0) + "건 조회 완료.");
    System.out.println("----------------------------------");
    return new ResponseEntity<>(logs, HttpStatus.OK); // JSON 형태의 응답 본문과 상태 코드 200 OK 반환
  }

}
