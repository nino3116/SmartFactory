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
		user.setUserId("test");
		user.setPassword("test");
		user.setUsername("testuser");
		user.setEmail("test@test.com");
		user.setCreateDate(LocalDateTime.now());
		this.usersRepository.save(user);
	}

}
