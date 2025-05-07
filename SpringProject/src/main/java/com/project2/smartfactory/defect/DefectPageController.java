package com.project2.smartfactory.defect;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui")
public class DefectPageController {

    // "/defects" 경로로 들어오는 GET 요청을 처리하는 메소드를 정의합니다.
    // 예: http://localhost:8080/ui/defects 로 접근 시 이 메소드가 호출됩니다.
    @GetMapping("/defects")
    public String showDefectsPage() {
        // "pages/defects"라는 뷰 이름(템플릿 파일 경로)을 반환합니다.
        // Spring Boot와 Thymeleaf는 src/main/resources/templates/ 디렉토리에서
        // "pages/defects.html" 파일을 찾아 렌더링합니다.
        System.out.println("--- 웹 페이지 요청 수신: /ui/defects ---");
        return "pages/defects";
    }

    // 필요한 경우 다른 웹 페이지 제공 메소드를 여기에 추가할 수 있습니다.


}
