package com.zhengmeng.hub.auth;

import com.zhengmeng.hub.config.HubProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与验证
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final HubProperties properties;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 JWT */
    public String generateToken(String userId, String userName, String department) {
        long now = System.currentTimeMillis();
        long expiry = now + properties.getJwt().getExpirationHours() * 3600_000L;

        return Jwts.builder()
                .subject(userId)
                .claim("name", userName)
                .claim("department", department)
                .issuedAt(new Date(now))
                .expiration(new Date(expiry))
                .signWith(getKey())
                .compact();
    }

    /** 验证并解析 JWT，返回 userId；无效返回 null */
    public String validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
