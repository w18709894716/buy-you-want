package com.byw.auth.service.impl;

import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;
import com.byw.auth.service.AuthService;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.R;
import com.byw.common.redis.util.RedisUtil;
import com.byw.common.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserFeignClient userFeignClient;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // Store token in Redis for validation
        redisUtil.set("auth:token:" + token, user.getId(), 24, TimeUnit.HOURS);

        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar());
    }

    @Override
    public void register(RegisterRequest request) {
        // Check username existence
        R<Boolean> existsResult = userFeignClient.checkUsernameExists(request.getUsername());
        if (existsResult.isSuccess() && Boolean.TRUE.equals(existsResult.getData())) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
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

        // Delete old token
        redisUtil.delete("auth:token:" + token);

        // Generate new token
        String newToken = jwtUtil.generateToken(userId, username);
        redisUtil.set("auth:token:" + newToken, userId, 24, TimeUnit.HOURS);

        R<UserDTO> userResult = userFeignClient.getUserById(userId);
        UserDTO user = userResult.isSuccess() ? userResult.getData() : null;

        return new LoginResponse(newToken, userId, username,
                user != null ? user.getNickname() : null,
                user != null ? user.getAvatar() : null);
    }

    @Override
    public void logout(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        redisUtil.delete("auth:token:" + token);
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
