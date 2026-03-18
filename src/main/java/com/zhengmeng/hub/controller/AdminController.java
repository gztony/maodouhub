package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.repository.DeviceRepository;
import com.zhengmeng.hub.repository.FileRepository;
import com.zhengmeng.hub.repository.MessageRepository;
import com.zhengmeng.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Hub 管理后台 — 监控、统计、运行状态
 */
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final MessageRepository messageRepository;
    private final FileRepository fileRepository;

    private static final LocalDateTime startTime = LocalDateTime.now();

    /** 管理后台首页 */
    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }

    /** 实时统计数据 API */
    @GetMapping("/admin/api/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        Map<String, Object> stats = new HashMap<>();

        // 用户统计
        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        // 设备统计
        long totalDevices = deviceRepository.count();
        long onlineDevices = deviceRepository.findAll().stream().filter(d -> d.isOnline()).count();
        stats.put("totalDevices", totalDevices);
        stats.put("onlineDevices", onlineDevices);

        // 消息统计
        long totalMessages = messageRepository.count();
        stats.put("totalMessages", totalMessages);

        // 文件统计
        long totalFiles = fileRepository.count();
        long totalFileSize = fileRepository.findAll().stream().mapToLong(f -> f.getFileSize()).sum();
        stats.put("totalFiles", totalFiles);
        stats.put("totalFileSizeMB", String.format("%.1f", totalFileSize / 1024.0 / 1024.0));

        // JVM 状态
        Runtime runtime = Runtime.getRuntime();
        stats.put("jvmMaxMemoryMB", runtime.maxMemory() / 1024 / 1024);
        stats.put("jvmTotalMemoryMB", runtime.totalMemory() / 1024 / 1024);
        stats.put("jvmFreeMemoryMB", runtime.freeMemory() / 1024 / 1024);
        stats.put("jvmUsedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        stats.put("availableProcessors", runtime.availableProcessors());

        // 运行时间
        Duration uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        stats.put("uptime", String.format("%dh %dm %ds", hours, minutes, seconds));
        stats.put("startTime", startTime.toString());

        // 线程数
        stats.put("threadCount", Thread.activeCount());

        return stats;
    }

    /** 在线设备列表 */
    @GetMapping("/admin/api/devices")
    @ResponseBody
    public Object devices() {
        return deviceRepository.findAll().stream().map(d -> {
            Map<String, Object> info = new HashMap<>();
            info.put("deviceId", d.getDeviceId());
            info.put("userId", d.getUserId());
            info.put("deviceName", d.getDeviceName());
            info.put("online", d.isOnline());
            info.put("channelType", d.getChannelType());
            info.put("lastHeartbeat", d.getLastHeartbeat() != null ? d.getLastHeartbeat().toString() : null);
            return info;
        }).toList();
    }

    /** 最近消息 */
    @GetMapping("/admin/api/recent-messages")
    @ResponseBody
    public Object recentMessages() {
        return messageRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(20)
            .map(m -> {
                Map<String, Object> info = new HashMap<>();
                info.put("messageId", m.getMessageId());
                info.put("userId", m.getUserId());
                info.put("role", m.getRole());
                info.put("content", m.getContent() != null && m.getContent().length() > 80
                    ? m.getContent().substring(0, 80) + "..."
                    : m.getContent());
                info.put("status", m.getStatus());
                info.put("channel", m.getChannel());
                info.put("createdAt", m.getCreatedAt().toString());
                return info;
            }).toList();
    }
}
