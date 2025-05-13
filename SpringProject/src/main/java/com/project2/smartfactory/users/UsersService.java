package com.project2.smartfactory.users;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project2.smartfactory.DataNotFoundException;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UsersService implements UserDetailsService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password}")
    private String defaultAdminPassword;

    @Value("${app.admin.initialize}") 
    private boolean initializeAdmin;

    @PostConstruct
    public void initializeAdminAccountFromConfig() {
        if (initializeAdmin && usersRepository.count() == 0) {
            Users adminUser = new Users();
            adminUser.setUsername("admin");
            adminUser.setAdminPasswordHash(passwordEncoder.encode(defaultAdminPassword));
            adminUser.setCreateDate(LocalDateTime.now()); // 이 줄은 주석 처리되어 있습니다.
            usersRepository.save(adminUser);
            System.out.println("설정 파일로부터 초기 관리자 계정이 생성되었습니다.");
        }
    }

    public Users getAdminUser(Integer id) {
        Optional<Users> adminUser = this.usersRepository.findById(id);
        if (adminUser.isPresent()) {
            return adminUser.get();
        } else {
            throw new DataNotFoundException("관리자 계정을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void updateAdminPassword(Integer id, String newPassword) {
        Optional<Users> optionalAdminUser = this.usersRepository.findById(id);

        if (optionalAdminUser.isPresent()) {
            Users adminUser = optionalAdminUser.get();
            adminUser.setAdminPasswordHash(passwordEncoder.encode(newPassword));
            adminUser.setUpdateDate(LocalDateTime.now());
            this.usersRepository.save(adminUser);
        } else {
            throw new DataNotFoundException(String.format("관리자 계정을 찾을 수 없습니다. ID: %s", id));
        }
    }

    public void deleteUser(Integer id) {
        Optional<Users> optionalAdminUser = this.usersRepository.findById(id);

        if (optionalAdminUser.isPresent()) {
            Users adminUser = optionalAdminUser.get();
            this.usersRepository.delete(adminUser);
        } else {
            throw new DataNotFoundException(String.format("관리자 계정을 찾을 수 없습니다. ID: %s", id));
        }
    }

    public Users getUserByUsername(String username) {
    return usersRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("관리자를 찾을 수 없습니다: " + username));
    }

    public boolean checkAdminPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users adminUser = usersRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("관리자 계정(" + username + ")을 찾을 수 없습니다."));

        return org.springframework.security.core.userdetails.User.builder()
            .username(adminUser.getUsername())
            .password(adminUser.getAdminPasswordHash())
            .roles("ADMIN")
            .build();
    }
}