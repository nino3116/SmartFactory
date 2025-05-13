package com.project2.smartfactory.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, Integer>{

    // 더 이상 userId로 검색하는 기능은 필요하지 않습니다.
    // 사용자 이름으로 관리자 계정 조회
    Optional<Users> findByUsername(String username);
}