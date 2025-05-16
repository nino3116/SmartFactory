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
        user.setCreateDate(LocalDateTime.now());
        usersRepository.save(user);
    }
}