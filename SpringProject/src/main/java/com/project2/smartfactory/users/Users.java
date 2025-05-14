package com.project2.smartfactory.users;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(nullable = false, unique = true, name = "username")
    private String username;

    @Column(nullable = false, length = 255, name = "admin_password_hash")
    private String adminPasswordHash;

    @Column(updatable = false, name = "created_date")
    private LocalDateTime createDate;

    @Column(name = "updated_date")
    private LocalDateTime updateDate;
    @PrePersist
    public void onCreate() {
        this.createDate = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updateDate = LocalDateTime.now();
    }
}