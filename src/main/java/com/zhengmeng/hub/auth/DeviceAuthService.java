package com.zhengmeng.hub.auth;

import com.zhengmeng.hub.config.HubProperties;
import com.zhengmeng.hub.model.HubDevice;
import com.zhengmeng.hub.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * PC 设备认证服务
 * <p>
 * 使用 HMAC-SHA256 签名验证，deviceSecret 不在网络上传输。
 */
@Service
@RequiredArgsConstructor
public class DeviceAuthService {

    private final DeviceRepository deviceRepository;
    private final HubProperties properties;

    /** 生成设备密钥 */
    public String generateDeviceSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 验证设备签名
     *
     * @param deviceId  设备 ID
     * @param signature 客户端签名（HMAC-SHA256(deviceSecret, body + timestamp)）
     * @param body      请求体
     * @param timestamp 时间戳（秒）
     * @return 验证通过返回 HubDevice，失败返回 null
     */
    public HubDevice verifySignature(String deviceId, String signature, String body, long timestamp) {
        // 检查时间窗口
        long now = System.currentTimeMillis() / 1000;
        int window = properties.getDevice().getSignatureTimeWindowSeconds();
        if (Math.abs(now - timestamp) > window) {
            return null;
        }

        HubDevice device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return null;
        }

        // 重算 HMAC
        String data = (body != null ? body : "") + timestamp;
        String expected = hmacSha256(device.getDeviceSecret(), data);

        if (expected != null && expected.equals(signature)) {
            return device;
        }
        return null;
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }
}
