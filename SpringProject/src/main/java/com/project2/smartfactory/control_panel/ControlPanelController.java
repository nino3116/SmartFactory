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

  @GetMapping("/control_panel")
    public String ControlPanel (Model model){
        

        model.addAttribute("title", "Control Panel");
        model.addAttribute("activebutton", "control_panel");
        return "pages/control_panel"; // 템플릿 경로 반환
    }
}
