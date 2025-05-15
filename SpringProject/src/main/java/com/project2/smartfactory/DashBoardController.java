package com.project2.smartfactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashBoardController {
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        
        model.addAttribute("title", "Dashboard" );
        return "pages/dashboard";

    }

}
