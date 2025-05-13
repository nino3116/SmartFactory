package com.project2.smartfactory.users;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication; // Correct Authentication import
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;



@RequestMapping("/admin")
@RequiredArgsConstructor
@Controller
public class UsersController {

    private final UsersService usersService;

    // ✅ 로그인된 관리자 ID 감지 → 리다이렉트
    @GetMapping("/settings")
    public String redirectToEditPassword() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName(); // 현재 로그인한 사용자 이름 (admin)
        Users user = usersService.getUserByUsername(username); // UsersService에서 구현 필요

        return "redirect:/admin/password/edit/" + user.getId();
    }

    @GetMapping("/password/edit/{id}")
    public String showEditPasswordForm(Model model, @PathVariable("id") Integer id, AdminPasswordForm adminPasswordForm) {
        // 관리자 정보 조회 (현재는 ID만 사용하므로 간단하게 처리)
        // 실제 프로젝트에서는 인증/인가를 통해 접근 권한을 확인해야 합니다.
        model.addAttribute("adminId", id); // 템플릿에서 ID를 사용할 수 있도록 Model에 추가
        return "admin_password_edit"; 
    }

    @PutMapping("/password/edit/{id}")
    public String updateAdminPassword(@PathVariable("id") Integer id, @Valid AdminPasswordForm adminPasswordForm, BindingResult bindingResult, Model model) {
        System.err.println("값을 받는지");
       
        // ID로 관리자 조회
        Users admin = usersService.getAdminUser(id);

        // 현재 비밀번호 검증
        if (!usersService.checkAdminPassword(
                adminPasswordForm.getCurrentPassword(),
                admin.getAdminPasswordHash())) {
            bindingResult.rejectValue("currentPassword", "invalid", "현재 비밀번호가 일치하지 않습니다.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("adminId", id);
            return "admin_password_edit";
        }

        usersService.updateAdminPassword(id, adminPasswordForm.getNewPassword());
        return "redirect:/login";
    }

    @DeleteMapping("/delete/{id}")
    public String deleteAdmin(@PathVariable("id") Integer id) {
        // 관리자 삭제 기능 (주의: 신중하게 구현해야 함)
        this.usersService.deleteUser(id);
        return "redirect:/login";
    }

    @GetMapping("/login") // 기존 로그인 페이지 매핑 유지
    public String loginPage() {
        return "login";
    }
    
}