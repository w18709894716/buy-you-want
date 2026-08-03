package com.byw.common.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    @Value("${byw.jwt.secret:buyyouwant-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String secret;

    @Value("${byw.jwt.expiration:86400000}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null, null);
    }

    /**
     * 签发带角色与店铺维度的 Token。
     * @param role   角色（user / platform_admin / merchant_owner / merchant_staff）
     * @param shopId 店铺ID（商家账号有值，平台/普通用户为 null）
     */
    public String generateToken(Long userId, String username, String role, Long shopId) {
        return generateToken(userId, username, role, shopId, null);
    }

    /**
     * 签发带角色、店铺维度与用户主体类型的 Token。
     * @param userType 主体类型（c / sys / merchant）
     */
    public String generateToken(Long userId, String username, String role, Long shopId, String userType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        if (role != null) {
            claims.put("role", role);
        }
        if (shopId != null) {
            claims.put("shopId", shopId);
        }
        if (userType != null) {
            claims.put("userType", userType);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }


    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    public Long getShopId(String token) {
        Claims claims = parseToken(token);
        Object v = claims.get("shopId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String getUserType(String token) {
        Claims claims = parseToken(token);
        return claims.get("userType", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
