package com.byw.common.security.service;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;

/**
 * 权限校验器：从 Redis 读取登录主体聚合后的权限标识集合（Set）。
 * key 结构 auth:perms:{userType}:{userId}，登录时写入、改授权时删除，实现即时生效。
 * 集合中含 {@code *} 通配即视为拥有全部权限。
 */
@RequiredArgsConstructor
public class PermissionChecker {

    private final RedisUtil redisUtil;

    public boolean hasPermission(String userType, Long userId, String perm) {
        if (userType == null || userId == null || perm == null) {
            return false;
        }
        String key = CommonConstants.AUTH_PERMS_KEY_PREFIX + userType + ":" + userId;
        if (Boolean.TRUE.equals(redisUtil.sIsMember(key, CommonConstants.PERM_ALL))) {
            return true;
        }
        return Boolean.TRUE.equals(redisUtil.sIsMember(key, perm));
    }
}
