package com.project2.smartfactory.users;

import lombok.Getter;

@Getter
public enum UsersRole {
    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER");

    UsersRole(String value) {
        this.value = value;
    }

    private String value;

}
