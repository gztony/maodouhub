package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.auth.JwtTokenProvider;
import com.zhengmeng.hub.model.HubUser;
import com.zhengmeng.hub.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证控制器（模拟登录，后续接 OA SSO）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    /** 模拟注册 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUserId(req.getUserId()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "用户已存在"));
        }

        HubUser user = new HubUser();
        user.setUserId(req.getUserId());
        user.setUserName(req.getUserName());
        user.setDepartment(req.getDepartment());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("ok", true, "userId", user.getUserId()));
    }

    /** 模拟登录 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        HubUser user = userRepository.findByUserId(req.getUserId()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "用户名或密码错误"));
        }

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUserName(), user.getDepartment());
        user.setLastLoginAt(LocalDateTime.now());
        user.setMobileToken(token);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "token", token,
            "user", Map.of(
                "userId", user.getUserId(),
                "userName", user.getUserName(),
                "department", user.getDepartment() != null ? user.getDepartment() : ""
            )
        ));
    }

    @Data
    public static class RegisterRequest {
        private String userId;
        private String userName;
        private String department;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String userId;
        private String password;
    }
}
