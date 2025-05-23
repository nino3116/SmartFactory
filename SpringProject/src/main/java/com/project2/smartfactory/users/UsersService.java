package com.project2.smartfactory.users;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
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
            adminUser.setCreateDate(LocalDateTime.now());
            usersRepository.save(adminUser);
            System.out.println("설정 파일로부터 초기 관리자 계정이 생성되었습니다.");
        }
    }
    // 비밀번호 변경 시 내부적으로 관리자 조회에 계속 사용
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
        Users adminUser = this.getAdminUser(id); 

        // 새 비밀번호로 덮어쓰기
        adminUser.setAdminPasswordHash(passwordEncoder.encode(newPassword));
        adminUser.setUpdateDate(LocalDateTime.now());

        this.usersRepository.save(adminUser); // 덮어쓰기 + 수정일 갱신
    }

    
    public Users getUserByUsername(String username) {
    return usersRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("관리자를 찾을 수 없습니다: " + username));
    }
    // 비밀번호 변경시 입력된 현재 비밀번호와 비교
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