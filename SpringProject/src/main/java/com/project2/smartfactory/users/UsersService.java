package com.project2.smartfactory.users;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.project2.smartfactory.DataNotFoundException;

import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UsersService {

    private final UsersRepository usersRepository;

    public List<Users> getList() {
        return this.usersRepository.findAll();
    }


    public void create(String userId, String password, String username, String email) {
        Users user = new Users();
        user.setUserId(userId);
        user.setPassword(password);
        user.setUsername(userId);
        user.setEmail(email);
        user.setCreateDate(LocalDateTime.now());
        this.usersRepository.save(user);

    }

    public Users getUser(Integer id) {
        Optional<Users> user = this.usersRepository.findById(id);
        if (user.isPresent()) {
            return user.get();
        } else {
            throw new DataNotFoundException("user not found");
        }
    }

    @Transactional
    public void updateUser(Integer id, UsersForm usersForm) {
        Optional<Users> optionalUser = this.usersRepository.findById(id);

        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();
            user.setUserId(usersForm.getUserId());
            user.setPassword(usersForm.getPassword());
            user.setUsername(usersForm.getUsername());
            user.setEmail(usersForm.getEmail());
            this.usersRepository.save(user);
        } else {
            throw new RuntimeException(String.format("User not found with id: %s", id));
        }

    }

    public void deleteUser(Integer id) {
        Optional<Users> optionalUser = this.usersRepository.findById(id);

        if (optionalUser.isPresent()) {
            Users user = optionalUser.get();
            this.usersRepository.delete(user);
        } else {
            throw new RuntimeException(String.format("User not exist with id: %s", id));
        }
        
    }

}
