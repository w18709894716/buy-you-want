package com.byw.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.user.entity.User;

public interface UserService extends IService<User> {
    User getByUsername(String username);
    User getByPhone(String phone);
    boolean checkUsernameExists(String username);

    /**
     * 按 ID 读缓存，仅供展示类只读链路使用。
     * 写链路（改状态前的读取）必须走 getById，否则会读到旧数据。
     */
    User getUserDetail(Long userId);
}
