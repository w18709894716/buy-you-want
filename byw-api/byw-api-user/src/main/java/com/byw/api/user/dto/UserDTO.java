package com.byw.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserDTO implements Serializable {

    private Long id;
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名过长")
    private String username;
    @Size(max = 200, message = "密码过长")
    private String password;
    @Size(max = 20, message = "手机号过长")
    private String phone;
    @Size(max = 100, message = "邮箱过长")
    private String email;
    @Size(max = 50, message = "昵称过长")
    private String nickname;
    @Size(max = 500, message = "头像地址过长")
    private String avatar;
    @Min(value = 0, message = "性别只能是 0未知/1男/2女")
    @Max(value = 2, message = "性别只能是 0未知/1男/2女")
    private Integer gender;
    private Integer status;
    private Integer userLevel;
    private String role;
    private LocalDateTime createdAt;
}
