package com.project2.smartfactory.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, Integer>{

    Optional<Users> findByUserId(String userId);

}
