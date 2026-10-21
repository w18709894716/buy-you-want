package com.byw.auth.service;

import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse merchantLogin(LoginRequest request);
    LoginResponse adminLogin(LoginRequest request);
    void register(RegisterRequest request);
    LoginResponse refreshToken(String token);

    /**
     * 当前 Token 剩余有效秒数，供前端在过期前主动续期。
     *
     * @param token Authorization 请求头（形如 {@code Bearer xxx}），可为空
     * @return 剩余秒数；{@code -1} 表示永不过期，{@code -2} 表示 Token 无效/不存在
     */
    long getTokenTtl(String token);

    void logout(String token);
    void sendSmsCode(String phone);
}
