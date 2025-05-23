package com.project2.smartfactory.control_panel;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlPanelController {
  
  @GetMapping("/control_panel")
  public String ControlPanel (Model model){

    model.addAttribute("title", "Control Panel" );
    model.addAttribute("activebutton", "control_panel");
    return "pages/control_panel";
  }

  
}
