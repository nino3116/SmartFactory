package com.project2.smartfactory;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project2.smartfactory.users.Users;
import com.project2.smartfactory.users.UsersRepository;
import com.project2.smartfactory.users.UsersService;

@SpringBootTest
class SmartfactoryApplicationTests {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UsersService usersService;

    @Test
    void testJpa() {
        Users user = new Users();
				user.setAdminPasswordHash("yourSecureStrongPassword"); // 🔐 이 한 줄이 꼭 필요!
        // userId와 password 필드가 제거되었으므로 해당 설정은 삭제합니다.
        // user.setUserId("test");
        // user.setPassword("test");
        user.setCreateDate(LocalDateTime.now());
        usersRepository.save(user);
    }
}