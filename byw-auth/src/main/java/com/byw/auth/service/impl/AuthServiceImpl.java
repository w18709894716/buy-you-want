package com.byw.auth.service.impl;

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
import com.byw.common.redis.util.RedisUtil;
import com.byw.common.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** user_type：平台员工 */
    private static final int USER_TYPE_SYS = 1;
    /** user_type：商家账号 */
    private static final int USER_TYPE_MERCHANT = 2;

    @Override
    public LoginResponse login(LoginRequest request) {
        // Fetch user info (including password) via single Feign call
        R<UserDTO> result = userFeignClient.getUserByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        UserDTO user = result.getData();
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // Local password verification
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        // t_user 已回归纯 C 端会员（无 role 字段），固定角色 user、userType=c，不写权限集
        String role = "user";
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role, null, CommonConstants.USER_TYPE_C);

        // Store token in Redis for validation
        redisUtil.set("auth:token:" + token, user.getId(), 24, TimeUnit.HOURS);

        LoginResponse resp = new LoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), role, null);
        return resp;
    }

    @Override
    public LoginResponse adminLogin(LoginRequest request) {
        // 平台员工登录主体为 t_sys_user（与 C 端会员彻底分离）
        R<SysUserDTO> result = rbacFeignClient.getSysUserByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        SysUserDTO user = result.getData();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        // role 统一为平台管理员（标识平台员工身份），细粒度权限由 @RequirePerm + userType 驱动
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(),
                CommonConstants.ROLE_PLATFORM_ADMIN, null, CommonConstants.USER_TYPE_SYS);
        redisUtil.set("auth:token:" + token, user.getId(), 24, TimeUnit.HOURS);

        // 聚合权限标识写入 Redis（与 token 同 24h TTL）
        List<String> perms = rbacFeignClient.listPermCodes(USER_TYPE_SYS, user.getId()).getData();
        writePerms(CommonConstants.USER_TYPE_SYS, user.getId(), perms);

        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), CommonConstants.ROLE_PLATFORM_ADMIN, null);
    }

    @Override
    public LoginResponse merchantLogin(LoginRequest request) {
        R<MerchantAccountDTO> result = shopFeignClient.getMerchantByUsername(request.getUsername());
        if (!result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        MerchantAccountDTO merchant = result.getData();

        // 密码校验
        if (!passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

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

        String role = merchant.getRole() != null ? merchant.getRole() : "merchant_owner";
        String token = jwtUtil.generateToken(merchant.getId(), merchant.getUsername(), role,
                merchant.getShopId(), CommonConstants.USER_TYPE_MERCHANT);

        redisUtil.set("auth:token:" + token, merchant.getId(), 24, TimeUnit.HOURS);

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

    @Override
    public LoginResponse refreshToken(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);
        String role = jwtUtil.getRole(token);
        Long shopId = jwtUtil.getShopId(token);
        String userType = jwtUtil.getUserType(token);

        // Delete old token
        redisUtil.delete("auth:token:" + token);

        // Generate new token
        String newToken = jwtUtil.generateToken(userId, username, role, shopId, userType);
        redisUtil.set("auth:token:" + newToken, userId, 24, TimeUnit.HOURS);

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
        redisUtil.delete("auth:token:" + token);
        // 同步删除权限集 key
        if (jwtUtil.validateToken(token)) {
            String userType = jwtUtil.getUserType(token);
            Long userId = jwtUtil.getUserId(token);
            if (userType != null && userId != null && !CommonConstants.USER_TYPE_C.equals(userType)) {
                redisUtil.delete(CommonConstants.AUTH_PERMS_KEY_PREFIX + userType + ":" + userId);
            }
        }
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
        // Generate 6-digit code
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // Store in Redis with 5-minute TTL
        redisUtil.set("sms:code:" + phone, code, 5, TimeUnit.MINUTES);

        // In production, integrate with SMS provider here
        log.info("短信验证码已发送: phone={}, code={}", phone, code);
    }
}
