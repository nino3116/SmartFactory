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
        //TODO: 실제 데이터 로직 구현

        // 예시 데이터
        List<Map<String,String>> dataList = new ArrayList<>();
        for(int i=0; i<5; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("col1", "Cell text " + (i+1));
            row.put("col2", "Cell text " + (i+1));
            row.put("col3", "Cell text " + (i+1));
            row.put("col4", "Cell text " + (i+1));
            row.put("col5", "Cell text " + (i+1));
            dataList.add(row);
        }
        model.addAttribute("dataList", dataList);

        return "pages/dashboard";

    }

}
