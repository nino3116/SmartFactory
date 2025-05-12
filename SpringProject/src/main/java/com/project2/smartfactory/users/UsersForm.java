package com.project2.smartfactory.users;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsersForm {
    @NotEmpty(message = "아이디는 필수 입니다.")
    @Size(max = 15)
    private String userId;

    @NotEmpty(message = "비밀번호 입력은 필수 입니다.")
    @Size(max = 20)
    private String password;



}
