package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.model.HubDevice;
import com.zhengmeng.hub.model.HubUser;
import com.zhengmeng.hub.repository.DeviceRepository;
import com.zhengmeng.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态查询控制器
 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    /** 查询我的 PC 是否在线 */
    @GetMapping("/pc")
    public ResponseEntity<?> pcStatus(Authentication auth) {
        String userId = auth.getName();
        HubDevice device = deviceRepository.findByUserIdAndOnlineTrue(userId).orElse(null);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("online", device != null);
        if (device != null) {
            result.put("deviceName", device.getDeviceName());
            result.put("channelType", device.getChannelType());
            result.put("lastHeartbeat", device.getLastHeartbeat() != null ? device.getLastHeartbeat().toString() : null);
        }
        return ResponseEntity.ok(result);
    }

    /** 查询我的账户信息 */
    @GetMapping("/me")
    public ResponseEntity<?> myStatus(Authentication auth) {
        String userId = auth.getName();
        HubUser user = userRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("ok", false, "error", "用户不存在"));
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "userId", user.getUserId(),
            "userName", user.getUserName(),
            "department", user.getDepartment() != null ? user.getDepartment() : "",
            "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : ""
        ));
    }
}
