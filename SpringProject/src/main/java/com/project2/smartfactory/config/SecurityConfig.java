package com.project2.smartfactory.config;

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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/api/defect", "POST")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/latest-defects", "GET")).permitAll()
                // AntPathRequstMatcher 는 개별적으로만 사용이 가능함
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/control/**").permitAll()
                .requestMatchers(new AntPathRequestMatcher("/admin/login")).permitAll() // 로그인 페이지 허용
                .requestMatchers(new AntPathRequestMatcher("/admin/logout")).permitAll() // 로그아웃 허용
                .requestMatchers(new AntPathRequestMatcher("/extendSession")).authenticated() // 세션 연장 허용
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/admin/login") // 사용자 지정 로그인 페이지
                .loginProcessingUrl("/admin/login") // 실제 로그인 처리를 담당할 경로 (폼 action과 일치해야 함)
                .usernameParameter("username") // <input name="username">
                .passwordParameter("password") // <input name="password">
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/admin/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout"))
                .logoutSuccessUrl("/admin/login")
                .invalidateHttpSession(true)
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/defect", "POST"))
                // /api/control/** (POST)에 대해 CSRF 보호 비활성화 추가
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/control/**"))
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/progress/set-total", "POST"))
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/notifications/**", "POST"))
            );
        return http.build();
    }

        
        
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
        
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();        
    }
}