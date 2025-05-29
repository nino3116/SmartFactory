package com.project2.smartfactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartfactoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartfactoryApplication.class, args);
	}

	// @Bean
  // public RestTemplate restTemplate() {
  //   return new RestTemplate();
  // }
}
