package com.project2.smartfactory.users;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/admin")
@RequiredArgsConstructor
@Controller
public class UsersController {

    private final UsersService usersService;


    @GetMapping("/login") // 기존 로그인 페이지 매핑 유지
    public String loginPage() {
        return "login";
    }


    // GET: 비밀번호 변경 페이지
    @GetMapping("/password/edit")
    public String showEditPasswordForm(Model model, AdminPasswordForm adminPasswordForm) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Users user = usersService.getUserByUsername(auth.getName());

        model.addAttribute("adminId", user.getId());
        return "admin_password_edit";
    }

    // POST: 비밀번호 변경 처리
    @PostMapping("/password/edit")
    public String updateAdminPassword(@Valid AdminPasswordForm adminPasswordForm,
                                      BindingResult bindingResult,
                                      Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Users user = usersService.getUserByUsername(auth.getName());

        if (!usersService.checkAdminPassword(adminPasswordForm.getCurrentPassword(), user.getAdminPasswordHash())) {
            bindingResult.rejectValue("currentPassword", "invalid", "현재 비밀번호가 일치하지 않습니다.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("adminId", user.getId());
            return "admin_password_edit";
        }

        usersService.updateAdminPassword(user.getId(), adminPasswordForm.getNewPassword());
        return "redirect:/admin/login";
    }
}
