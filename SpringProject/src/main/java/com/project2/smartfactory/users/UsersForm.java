package com.project2.smartfactory.users;

import jakarta.validation.constraints.Email;
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

    @NotEmpty(message = "이름 입력은 필수 입니다.")
    private String username;

    @NotEmpty(message = "이메일은 필수 입니다.")
    @Email(message = "이메일 형식으로 입력해주세요.")
    private String email;


}
