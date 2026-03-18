package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.auth.DeviceAuthService;
import com.zhengmeng.hub.model.HubDevice;
import com.zhengmeng.hub.repository.DeviceRepository;
import com.zhengmeng.hub.service.MessageRouterService;
import com.zhengmeng.hub.config.HubProperties;
import com.zhengmeng.hub.model.HubFile;
import com.zhengmeng.hub.repository.FileRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC 毛豆设备管理 API
 * <p>
 * 不走 JWT 认证，使用 HMAC 签名验证。
 */
@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceAuthService deviceAuthService;
    private final MessageRouterService messageRouter;
    private final FileRepository fileRepository;
    private final HubProperties hubProperties;

    /** PC 首次注册设备 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDeviceRequest req) {
        if (req.getUserId() == null || req.getDeviceName() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "缺少 userId 或 deviceName"));
        }

        String deviceId = "device-" + UUID.randomUUID().toString().substring(0, 8);
        String deviceSecret = deviceAuthService.generateDeviceSecret();

        HubDevice device = new HubDevice();
        device.setDeviceId(deviceId);
        device.setUserId(req.getUserId());
        device.setDeviceName(req.getDeviceName());
        device.setDeviceSecret(deviceSecret);
        deviceRepository.save(device);

        log.info("设备注册成功: userId={}, deviceId={}", req.getUserId(), deviceId);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "deviceId", deviceId,
            "deviceSecret", deviceSecret
        ));
    }

    /** PC 连接（标记在线） */
    @PostMapping("/connect")
    public ResponseEntity<?> connect(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp,
            @RequestBody(required = false) String body) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, body, timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        device.setOnline(true);
        device.setChannelType("poll");
        device.setLastHeartbeat(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(device);

        log.info("设备上线: deviceId={}, userId={}", deviceId, device.getUserId());

        return ResponseEntity.ok(Map.of("ok", true, "status", "connected"));
    }

    /** PC 断开连接 */
    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp,
            @RequestBody(required = false) String body) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, body, timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        device.setOnline(false);
        device.setChannelType("none");
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(device);

        messageRouter.clearDeviceQueue(deviceId);
        log.info("设备离线: deviceId={}", deviceId);

        return ResponseEntity.ok(Map.of("ok", true, "status", "disconnected"));
    }

    /** PC 轮询拉取消息 */
    @GetMapping("/poll")
    public ResponseEntity<?> poll(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, "", timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        // 更新心跳
        device.setLastHeartbeat(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(device);

        // 拉取待处理消息
        List<MessageRouterService.PendingMessage> messages = messageRouter.pollForDevice(deviceId);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "messages", messages
        ));
    }

    /** PC 回传处理结果 */
    @PostMapping("/result")
    public ResponseEntity<?> result(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp,
            @RequestBody ResultRequest req) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, "", timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        messageRouter.receiveResult(
            req.getRequestId(), req.getContent(), req.getWidgetJson(),
            req.getStatus(), req.getAttachmentFileIds()
        );

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** PC 回传流式进度 */
    @PostMapping("/stream")
    public ResponseEntity<?> stream(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp,
            @RequestBody StreamRequest req) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, "", timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        messageRouter.receiveStream(req.getRequestId(), req.getPhase(), req.getContent());

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** PC 上传结果文件 */
    @PostMapping("/files/upload")
    public ResponseEntity<?> uploadFile(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp,
            @RequestParam("file") MultipartFile file) throws IOException {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, "", timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        String fileId = "file-" + UUID.randomUUID().toString().substring(0, 8);
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Path dir = Path.of(hubProperties.getFile().getStorageDir(), device.getUserId(), month);
        Files.createDirectories(dir);

        String safeFileName = file.getOriginalFilename() != null
            ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_")
            : "upload";
        Path filePath = dir.resolve(fileId + "_" + safeFileName);
        file.transferTo(filePath.toFile());

        HubFile hubFile = new HubFile();
        hubFile.setFileId(fileId);
        hubFile.setUserId(device.getUserId());
        hubFile.setFileName(file.getOriginalFilename());
        hubFile.setFileSize(file.getSize());
        hubFile.setContentType(file.getContentType());
        hubFile.setStoragePath(filePath.toString());
        hubFile.setDirection("result");
        fileRepository.save(hubFile);

        log.info("PC 文件上传: deviceId={}, fileId={}, name={}", deviceId, fileId, file.getOriginalFilename());

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "fileId", fileId,
            "fileName", hubFile.getFileName(),
            "fileSize", hubFile.getFileSize()
        ));
    }

    /** PC 下载附件文件（用户上传的图片/文档等） */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<?> downloadFile(
            @PathVariable String fileId,
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader("X-Device-Signature") String signature,
            @RequestHeader("X-Device-Timestamp") long timestamp) {

        HubDevice device = deviceAuthService.verifySignature(deviceId, signature, "", timestamp);
        if (device == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "签名验证失败"));
        }

        HubFile hubFile = fileRepository.findById(fileId).orElse(null);
        if (hubFile == null) {
            return ResponseEntity.notFound().build();
        }

        // 只允许下载属于同一用户的文件
        if (!hubFile.getUserId().equals(device.getUserId())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "无权访问该文件"));
        }

        java.io.File file = new java.io.File(hubFile.getStoragePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        log.info("PC 文件下载: deviceId={}, fileId={}, name={}", deviceId, fileId, hubFile.getFileName());

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + hubFile.getFileName() + "\"")
            .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(hubFile.getFileSize())
            .body(new org.springframework.core.io.FileSystemResource(file));
    }

    // ─── 请求类 ─────────────────────────────────────────

    @Data
    public static class RegisterDeviceRequest {
        private String userId;
        private String deviceName;
    }

    @Data
    public static class ResultRequest {
        private String requestId;
        private String content;
        private String widgetJson;
        private String status;
        private List<String> attachmentFileIds;
    }

    @Data
    public static class StreamRequest {
        private String requestId;
        private String phase;
        private String content;
    }
}
