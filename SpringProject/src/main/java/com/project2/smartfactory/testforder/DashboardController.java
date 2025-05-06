package com.project2.smartfactory.testforder;

import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// import java.util.Arrays;
// import java.util.List;


@Controller

public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
    
    
}
