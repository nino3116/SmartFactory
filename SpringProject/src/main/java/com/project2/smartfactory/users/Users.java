package com.project2.smartfactory.users;


import java.time.LocalDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;




@Entity
@Data
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    
    @Column(unique = true, nullable = false, length = 15) 
    private String userId;
    
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(updatable = false)
    private LocalDateTime createDate;

    @Column
    private LocalDateTime updateDate;

}
