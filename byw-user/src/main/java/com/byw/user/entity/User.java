package com.byw.user.entity;

import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    @Size(max = 20, message = "手机号过长")
    private String phone;
    @Size(max = 100, message = "邮箱过长")
    private String email;
    @Size(max = 50, message = "昵称过长")
    private String nickname;
    @Size(max = 500, message = "头像地址过长")
    private String avatar;
    private Integer gender;
    private Integer status;
    private Integer userLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
