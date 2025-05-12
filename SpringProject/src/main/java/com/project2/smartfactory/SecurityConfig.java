package com.project2.smartfactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests
                .requestMatchers(new AntPathRequestMatcher("/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/defect","POST")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/latest-defects","GET")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/ui/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/js/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/css/**")).permitAll()
                // /api/control/** 경로에 대한 모든 요청 허용
                // 인증되지 않은 사용자도 접근 가능하게 합니다.
                .requestMatchers("/api/control/**").permitAll()
                // /api/status/script 경로에 대한 모든 요청 허용
                .requestMatchers("/api/status/script").permitAll()
            )
            .formLogin((formLogin) -> formLogin
                .loginPage("/users/login")
                .defaultSuccessUrl("/"))
            .csrf((csrf) -> csrf
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/defect","POST"))
                // /api/control/** 경로에 대한 CSRF 보호 비활성화
                // 이 경로로 들어오는 POST 요청에 대해 CSRF 토큰 검사를 하지 않습니다.
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/control/**"))
            )
            .logout((logout) -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/users/logout"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true))
            
                
            ;
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


}
