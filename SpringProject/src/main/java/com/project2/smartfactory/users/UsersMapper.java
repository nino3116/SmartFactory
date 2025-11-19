package com.project2.smartfactory.users;

import org.apache.ibatis.annotations.Mapper;
import java.util.Optional;

@Mapper
public interface UsersMapper {
    Optional<Users> findByUsername(String username);
    void save(Users user);
    Optional<Users> findById(Integer id);
    long count();
    void update(Users user);
}
