package com.byw.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.redis.util.RedisUtil;
import com.byw.user.entity.User;
import com.byw.user.mapper.UserMapper;
import com.byw.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final RedisUtil redisUtil;

    @Override
    public User getByUsername(String username) {
        // Try cache first
        String cacheKey = "user:info:username:" + username;
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof User) {
            return (User) cached;
        }

        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user != null) {
            redisUtil.set(cacheKey, user, 1, TimeUnit.HOURS);
        }
        return user;
    }

    @Override
    public User getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    @Override
    public boolean checkUsernameExists(String username) {
        return count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }
}
