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
    void logout(String token);
    void sendSmsCode(String phone);
}
