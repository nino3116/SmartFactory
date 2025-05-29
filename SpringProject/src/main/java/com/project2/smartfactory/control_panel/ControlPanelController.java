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
    // private static final String RASPBERRY_PI_IP = "192.168.0.125"; // 라즈베리 파이 IP 주소
    // private static final String RASPBERRY_PI_USER = "nino"; // 라즈베리 파이 사용자 이름
    // private static final String PYTHON_SCRIPT_PATH = "/home/nino/projects/gpio.py"; // Python 스크립트 경로

  @GetMapping("/control_panel")
    public String ControlPanel (Model model){
        

        model.addAttribute("title", "Control Panel");
        model.addAttribute("activebutton", "control_panel");
        return "pages/control_panel"; // 템플릿 경로 반환
    }
}
