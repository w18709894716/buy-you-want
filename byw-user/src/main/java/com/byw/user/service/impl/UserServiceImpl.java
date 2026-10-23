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

    /**
     * 用户展示信息（**不含密码哈希**）。
     *
     * <p>缓存的是这个方法的返回值本身，所以哈希必须在这里就抹掉：Redis 目前没有认证，
     * 把带 BCrypt 串的实体写进缓存，等于把全体用户的密码摘要摆在一个谁都能连的 6379 里；
     * 而调用方拿到的对象来自反序列化，接口层再置空也只是遮住出口，遮不住缓存。
     *
     * <p>需要哈希的只有登录校验，走 {@link #getByUsername(String)}（无缓存）。
     *
     * <p>{@code cacheNullValue = true}：不存在的 id 也缓存 30 分钟，
     * 否则伪造 userId 反复打 /user/{userId} 每次都穿透到 DB。用户 id 自增，不会出现
     * "先查不存在、后来被创建"的情况，缓存 null 没有脏读风险。
     */
    @Override
    @Cached(name = CacheNames.USER_INFO, key = "#userId", expire = 30, timeUnit = TimeUnit.MINUTES,
            cacheNullValue = true)
    public User getUserDetail(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
