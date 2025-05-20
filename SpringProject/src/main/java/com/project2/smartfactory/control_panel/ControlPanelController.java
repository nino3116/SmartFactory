package com.project2.smartfactory.control_panel;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class ControlPanelController {
 private static final String RASPBERRY_PI_IP = "192.168.0.125"; // 라즈베리 파이 IP 주소
    private static final String RASPBERRY_PI_USER = "pi"; // 라즈베리 파이 사용자 이름
    private static final String PYTHON_SCRIPT_PATH = "/home/nino/control_panel.py"; // Python 스크립트 경로

    @GetMapping("/control_panel")
    public String ControlPanel(Model model) {
        model.addAttribute("title", "Control Panel");
        model.addAttribute("activebutton", "control_panel");
        return "pages/control_panel"; // 템플릿 경로 반환
    }
    @PostMapping("/control/system")
    public ResponseEntity<String> controlSystem(@RequestParam("action") String action) {
        
        return sendCommandToRaspberryPi("system", action);
    }
    
    @PostMapping("/control/sensor")
    public ResponseEntity<String> controlSensor(@RequestParam("action") String action) {
        return sendCommandToRaspberryPi("sensor", action);
    }

    @PostMapping("/control/buzzer")
    public ResponseEntity<String> controlBuzzer(@RequestParam("action") String action) {
        return sendCommandToRaspberryPi("buzzer", action);
    }

    private ResponseEntity<String> sendCommandToRaspberryPi(String target, String action) {
        try {
            // 1. SSH 명령 생성
            String command = String.format("ssh %s@%s python %s %s %s",
                    RASPBERRY_PI_USER, RASPBERRY_PI_IP, PYTHON_SCRIPT_PATH, target, action);
            System.out.println("명령 실행중: " + command); // 로그 출력

            // 2. ProcessBuilder를 사용하여 프로세스 실행
            String[] cmdArray = {"ssh", RASPBERRY_PI_USER + "@" + RASPBERRY_PI_IP, "python", PYTHON_SCRIPT_PATH, target, action};
            ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // 3. 결과 처리
            if (exitCode == 0) {
                return ResponseEntity.ok(target + " " + action + " 제어 성공");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(target + " " + action + " 제어 실패");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error controlling " + target + ": " + e.getMessage());
        }
    }
}