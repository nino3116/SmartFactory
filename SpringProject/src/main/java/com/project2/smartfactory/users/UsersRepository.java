package com.project2.smartfactory.users;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, Integer>{

    Users findByUserId(String userId);

}
