package com.byw.user.service.impl;

import com.alicp.jetcache.anno.Cached;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.core.constant.CacheNames;
import com.byw.user.entity.User;
import com.byw.user.mapper.UserMapper;
import com.byw.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getByUsername(String username) {
        // 登录鉴权（UserFeignImpl.validatePassword）读的就是这个方法，不能走缓存：
        // 否则改密或封号后，旧密码/旧状态在缓存有效期内仍然有效。
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
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

    @Override
    @Cached(name = CacheNames.USER_INFO, key = "#userId", expire = 30, timeUnit = TimeUnit.MINUTES)
    public User getUserDetail(Long userId) {
        return baseMapper.selectById(userId);
    }
}
