package com.byw.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.user.entity.User;

public interface UserService extends IService<User> {
    User getByUsername(String username);
    User getByPhone(String phone);
    boolean checkUsernameExists(String username);
}
