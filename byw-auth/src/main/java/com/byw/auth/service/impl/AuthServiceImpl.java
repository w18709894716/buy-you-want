package com.byw.auth.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.api.user.dto.UserDTO;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;
import com.byw.auth.service.AuthService;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.R;
import com.byw.common.core.util.LoginIds;
import com.byw.common.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserFeignClient userFeignClient;
    private final RbacFeignClient rbacFeignClient;
    private final ShopFeignClient shopFeignClient;
    private final RedisUtil redisUtil;
    /** 登录失败计数用 String 模板：计数器由 Redis INCR 写入的是裸数字，避免 JSON 序列化歧义 */
    private final StringRedisTemplate stringRedisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ========== 登录安全阈值 ==========
    /** 连续凭据失败达到该次数即临时锁定 */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** 失败计数存活时长（分钟），同时作为锁定时长；计数不刷新 TTL，锁定时长固定 */
    private static final long LOGIN_FAILURE_TTL_MINUTES = 15;
    /** 同一手机号发送验证码的冷却时长（秒） */
    private static final long SMS_COOLDOWN_SECONDS = 60;

    /** user_type：平台员工 */
    private static final int USER_TYPE_SYS = 1;
    /** user_type：商家账号 */
    private static final int USER_TYPE_MERCHANT = 2;

    @Override
    public LoginResponse login(LoginRequest request) {
        String failureKey = loginFailureKey(CommonConstants.USER_TYPE_C, request.getUsername());
        assertNotLocked(failureKey);
        // Fetch user info (including password) via single Feign call
        R<UserDTO> result = userFeignClient.getUserByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        UserDTO user = result.getData();
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // Local password verification
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        clearLoginFailures(failureKey);

        // t_user 已回归纯 C 端会员（无 role 字段），固定角色 user、userType=c，不写权限集
        String role = CommonConstants.ROLE_USER;
        // loginId 加账号类型前缀：t_user / t_sys_user / t_merchant_account 主键空间重叠，
        // 直接用主键会让三端共用同一个 Sa-Token 会话（互相覆盖 + 旧 token 读到他人角色）。
        StpUtil.login(LoginIds.of(CommonConstants.USER_TYPE_C, user.getId()));
        writeAuthSession(user.getUsername(), role, null, CommonConstants.USER_TYPE_C);
        String token = StpUtil.getTokenValue();

        LoginResponse resp = new LoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), role, null);
        return resp;
    }

    @Override
    public LoginResponse adminLogin(LoginRequest request) {
        String failureKey = loginFailureKey(CommonConstants.USER_TYPE_SYS, request.getUsername());
        assertNotLocked(failureKey);
        // 平台员工登录主体为 t_sys_user（与 C 端会员彻底分离）
        R<SysUserDTO> result = rbacFeignClient.getSysUserByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        SysUserDTO user = result.getData();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        clearLoginFailures(failureKey);

        // role 统一为平台管理员（标识平台员工身份），细粒度权限由 @RequirePerm + userType 驱动
        StpUtil.login(LoginIds.of(CommonConstants.USER_TYPE_SYS, user.getId()));
        writeAuthSession(user.getUsername(), CommonConstants.ROLE_PLATFORM_ADMIN, null, CommonConstants.USER_TYPE_SYS);
        String token = StpUtil.getTokenValue();

        // 聚合权限标识写入 Redis（与 token 同 24h TTL）
        List<String> perms = rbacFeignClient.listPermCodes(USER_TYPE_SYS, user.getId()).getData();
        writePerms(CommonConstants.USER_TYPE_SYS, user.getId(), perms);

        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), CommonConstants.ROLE_PLATFORM_ADMIN, null);
    }

    @Override
    public LoginResponse merchantLogin(LoginRequest request) {
        String failureKey = loginFailureKey(CommonConstants.USER_TYPE_MERCHANT, request.getUsername());
        assertNotLocked(failureKey);
        R<MerchantAccountDTO> result = shopFeignClient.getMerchantByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        MerchantAccountDTO merchant = result.getData();

        // 密码校验
        if (!passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            recordLoginFailure(failureKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        clearLoginFailures(failureKey);

        // 入驻审核必须通过
        if (merchant.getAuditStatus() == null || merchant.getAuditStatus() != 1) {
            throw new BusinessException("商家入驻尚未审核通过");
        }
        // 账号必须启用
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (merchant.getShopId() == null) {
            throw new BusinessException("商家账号未绑定店铺");
        }

        String role = merchant.getRole() != null ? merchant.getRole() : CommonConstants.ROLE_MERCHANT_OWNER;
        StpUtil.login(LoginIds.of(CommonConstants.USER_TYPE_MERCHANT, merchant.getId()));
        writeAuthSession(merchant.getUsername(), role, merchant.getShopId(), CommonConstants.USER_TYPE_MERCHANT);
        String token = StpUtil.getTokenValue();

        // 主账号（parentId=NULL）拥有全部商家权限（通配 *）；子账号按角色聚合权限
        List<String> perms;
        if (merchant.getParentId() == null) {
            perms = Collections.singletonList(CommonConstants.PERM_ALL);
        } else {
            perms = rbacFeignClient.listPermCodes(USER_TYPE_MERCHANT, merchant.getId()).getData();
        }
        writePerms(CommonConstants.USER_TYPE_MERCHANT, merchant.getId(), perms);

        return new LoginResponse(token, merchant.getId(), merchant.getUsername(),
                merchant.getRealName(), null, role, merchant.getShopId());
    }

    @Override
    public void register(RegisterRequest request) {
        // Check username existence
        R<Boolean> existsResult = userFeignClient.checkUsernameExists(request.getUsername());
        if (existsResult.isSuccess() && Boolean.TRUE.equals(existsResult.getData())) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // Check phone existence
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            R<UserDTO> phoneResult = userFeignClient.getUserByPhone(request.getPhone());
            if (phoneResult.isSuccess() && phoneResult.getData() != null) {
                throw new BusinessException(ResultCode.PHONE_EXISTS);
            }
        }

        // Create user via Feign
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(request.getUsername());
        userDTO.setPassword(request.getPassword());
        userDTO.setPhone(request.getPhone());
        userDTO.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        userDTO.setStatus(1);
        userDTO.setUserLevel(0);

        R<Long> createResult = userFeignClient.createUser(userDTO);
        if (!createResult.isSuccess()) {
            throw new BusinessException("注册失败: " + createResult.getMessage());
        }

        log.info("用户注册成功: username={}", request.getUsername());
    }

    /** 与 Sa-Token 的 SaTokenDao.NOT_VALUE_EXPIRE 一致：Token 不存在或已失效 */
    private static final long TOKEN_INVALID = -2L;

    @Override
    public long getTokenTtl(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return TOKEN_INVALID;
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
        if (token.isEmpty() || StpUtil.getLoginIdByToken(token) == null) {
            // 已登出/已过期/伪造：明确告诉前端"需要重新登录"，而不是返回 0（0 会被误判为马上过期）
            return TOKEN_INVALID;
        }
        // 必须按"上面刚校验过的那个 token"取剩余时间。无参重载读的是当前请求上下文的 token，
        // 两者解析路径不同：上下文取不到值时会返回 -2，把仍然有效的登录判成失效，
        // 前端 ttl<=0 分支随即 clearToken()，表现为登录后第一个请求就被登出。
        return StpUtil.getTokenTimeout(token);
    }

    @Override
    public LoginResponse refreshToken(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        Object loginId = StpUtil.getLoginIdByToken(token);
        Long userId = LoginIds.userId(loginId);
        if (loginId == null || userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        if (session == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String username = session.getString(CommonConstants.SESSION_USERNAME);
        String role = session.getString(CommonConstants.SESSION_ROLE);
        // 注意：SaSession.getLong() 是原始类型，键不存在时返回 0 而不是 null。
        // 用它读 shopId 会把平台管理员/C 端会员"没有店铺"变成"店铺 0"，
        // 于是 refresh 之后所有店铺级校验都变成"0 != 资源店铺"而被拒绝（真实踩过）。
        Object rawShopId = session.get(CommonConstants.SESSION_SHOP_ID);
        Long shopId = rawShopId instanceof Number number ? number.longValue() : null;
        String userType = session.getString(CommonConstants.SESSION_USER_TYPE);

        // 旧 token 换新：注销旧 token 后按原 loginId 重新登录并写回会话
        StpUtil.logoutByTokenValue(token);
        StpUtil.login(loginId);
        writeAuthSession(username, role, shopId, userType);
        String newToken = StpUtil.getTokenValue();

        // 同步续期权限集 TTL（C 端会员无权限集）
        if (userType != null && !CommonConstants.USER_TYPE_C.equals(userType)) {
            redisUtil.expire(CommonConstants.AUTH_PERMS_KEY_PREFIX + userType + ":" + userId, 24, TimeUnit.HOURS);
        }

        R<UserDTO> userResult = userFeignClient.getUserById(userId);
        UserDTO user = userResult.isSuccess() ? userResult.getData() : null;

        return new LoginResponse(newToken, userId, username,
                user != null ? user.getNickname() : null,
                user != null ? user.getAvatar() : null,
                role, shopId);
    }

    @Override
    public void logout(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            // token 已失效（已登出/过期），无需重复处理
            return;
        }
        Long userId = LoginIds.userId(loginId);
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        String userType = session == null ? null : session.getString(CommonConstants.SESSION_USER_TYPE);

        // 登出：删除 Redis 会话，旧 token 立即失效
        StpUtil.logoutByTokenValue(token);

        // 同步删除权限集 key
        if (userType != null && !CommonConstants.USER_TYPE_C.equals(userType)) {
            redisUtil.delete(CommonConstants.AUTH_PERMS_KEY_PREFIX + userType + ":" + userId);
        }
    }

    /** 将身份属性写入账号 Session，供网关 / IM 读取后注入身份头 */
    private void writeAuthSession(String username, String role, Long shopId, String userType) {
        SaSession session = StpUtil.getSession();
        session.set(CommonConstants.SESSION_USERNAME, username);
        session.set(CommonConstants.SESSION_ROLE, role);
        if (shopId != null) {
            session.set(CommonConstants.SESSION_SHOP_ID, shopId);
        } else {
            // 缺省即"无店铺上下文"。必须显式删除：会话是复用的，
            // 历史遗留的 0 不清掉会一直被网关注入成 X-Shop-Id: 0。
            session.delete(CommonConstants.SESSION_SHOP_ID);
        }
        session.set(CommonConstants.SESSION_USER_TYPE, userType);
    }

    /** 将聚合后的权限标识写入 Redis Set（与 token 同 24h TTL） */
    private void writePerms(String userType, Long userId, List<String> perms) {
        String key = CommonConstants.AUTH_PERMS_KEY_PREFIX + userType + ":" + userId;
        redisUtil.delete(key);
        if (perms != null && !perms.isEmpty()) {
            redisUtil.sAdd(key, perms.toArray());
            redisUtil.expire(key, 24, TimeUnit.HOURS);
        }
    }

    @Override
    public void sendSmsCode(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        // 冷却：防止该接口被当成短信轰炸器（接入真实通道后尤其重要）
        String cooldownKey = "sms:cooldown:" + phone;
        if (!Boolean.TRUE.equals(redisUtil.setIfAbsent(cooldownKey, "1", SMS_COOLDOWN_SECONDS, TimeUnit.SECONDS))) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }

        // Generate 6-digit code
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // Store in Redis with 5-minute TTL
        redisUtil.set("sms:code:" + phone, code, 5, TimeUnit.MINUTES);

        // In production, integrate with SMS provider here.
        // 不在 info 级别打印验证码明文：日志会长期留存，等价于把凭据泄露给所有能看日志的人
        log.info("短信验证码已发送: phone={}", maskPhone(phone));
        log.debug("短信验证码（仅本地联调可见）: phone={}, code={}", maskPhone(phone), code);
    }

    // ==================== 登录安全辅助 ====================

    /** 失败计数 key：按"端 + 账号"隔离，同名账号不会互相锁定 */
    private String loginFailureKey(String userType, String username) {
        return "auth:login:fail:" + userType + ":" + username;
    }

    private void assertNotLocked(String failureKey) {
        String value = stringRedisTemplate.opsForValue().get(failureKey);
        if (value != null && parseCount(value) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException("登录失败次数过多，账号已临时锁定，请 "
                    + LOGIN_FAILURE_TTL_MINUTES + " 分钟后再试");
        }
    }

    /** 记录一次凭据失败；仅在首次写入时设置 TTL，使锁定时长固定而不是被持续刷新 */
    private void recordLoginFailure(String failureKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failureKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failureKey, LOGIN_FAILURE_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    private void clearLoginFailures(String failureKey) {
        stringRedisTemplate.delete(failureKey);
    }

    private long parseCount(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 日志脱敏：只保留前 3 位与后 4 位 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
