package com.byw.api.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserDTO implements Serializable {

    private Long id;
    private String username;
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer gender;
    private Integer status;
    private Integer userLevel;
    private LocalDateTime createdAt;
}
