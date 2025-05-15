package com.project2.smartfactory.monitoring;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class monitoringController {
  
  @GetMapping("/monitoring")
  public String monitoringPage(Model model) {
    model.addAttribute("title", "Monitoring");
    return "monitoring"; // 위 HTML 파일
  }

}
