package com.project2.smartfactory.users;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/users")
@RequiredArgsConstructor
@Controller
public class UsersController {

    private final UsersService usersService;


    @GetMapping("/signup")
    public String singUpUser(UsersForm usersForm) {
        return "users_form";
    }

    @PostMapping("/signup")
    public String signUpUser(@Valid UsersForm usersForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "users_form";
        }

        this.usersService.create(usersForm.getUserId(), usersForm.getPassword());
        return "index";
    }
    
    @GetMapping("/list")
    public String usersList(Model model) { 
        List<Users> usersList = this.usersService.getList();
        model.addAttribute("usersList", usersList);
        return "users_list";
    }

    @GetMapping(value = "/list/{id}")
    public String showEditForm(Model model, @PathVariable("id") Integer id) {
        Users user = this.usersService.getUser(id);     // id로 Users 엔티티 조회

        // Users 엔티티 정보를 가지고 UsersForm 객체를 생성후 채우기
        UsersForm usersForm = new UsersForm();
        usersForm.setUserId(user.getUserId());
        
        
        // 수정 대상 사용자의 데이터베이스 ID를 Model에 별도로 담습니다.
        // 템플릿의 th:action URL 생성에 사용됩니다
        model.addAttribute("userIdForUrl", id); // Model에 "userIdForUrl" 이라는 이름으로 데이터베이스 ID를 담습니다.

        // UsersForm 객체를 "usersForm" 이라는 이름으로 Model에 담습니다.
        // 템플릿의 th:object="${usersForm}"과 매핑됩니다.
        model.addAttribute("usersForm", usersForm); // <-- Model에 담는 객체 이름을 통일
        return "user_edit";

    }

    @PutMapping("/list/{id}")
    public String updateUser(@PathVariable("id") Integer id, @Valid UsersForm usersForm, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return "user_edit";
        }
        this.usersService.updateUser(id,usersForm);
        return "redirect:/users/list";
    }

    @DeleteMapping("/delete/{id}")
    public String deleteUser(@PathVariable("id") Integer id) {

        this.usersService.deleteUser(id);

        return "redirect:/users/list";
        
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login_form";
    }



}
