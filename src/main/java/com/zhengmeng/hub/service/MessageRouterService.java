package com.zhengmeng.hub.service;

import com.zhengmeng.hub.model.HubDevice;
import com.zhengmeng.hub.model.HubFile;
import com.zhengmeng.hub.model.HubMessage;
import com.zhengmeng.hub.repository.DeviceRepository;
import com.zhengmeng.hub.repository.FileRepository;
import com.zhengmeng.hub.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 消息路由核心服务
 * <p>
 * 负责：
 * 1. 接收手机消息，入队等待 PC 拉取
 * 2. 接收 PC 回复，存储并供手机拉取
 * 3. 管理 PC 待处理消息队列（内存，PC 断线时丢弃）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRouterService {

    private final MessageRepository messageRepository;
    private final DeviceRepository deviceRepository;
    private final FileRepository fileRepository;

    /** PC 待处理消息队列：deviceId → 消息队列 */
    private final Map<String, Queue<PendingMessage>> deviceQueues = new ConcurrentHashMap<>();

    /**
     * 手机发消息 → 入库 + 入 PC 队列
     *
     * @return messageId
     */
    public String routeFromMobile(String userId, String content, String channel, List<String> attachmentFileIds) {
        // 1. 存消息到数据库
        String messageId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        HubMessage msg = new HubMessage();
        msg.setMessageId(messageId);
        msg.setUserId(userId);
        msg.setRequestId("req-" + UUID.randomUUID().toString().substring(0, 8));
        msg.setChannel(channel);
        msg.setRole("user");
        msg.setContent(content);
        msg.setStatus("delivering");
        messageRepository.save(msg);

        // 2. 找到用户的在线 PC
        HubDevice device = deviceRepository.findByUserIdAndOnlineTrue(userId).orElse(null);
        if (device == null) {
            msg.setStatus("failed");
            msg.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(msg);
            return null; // PC 不在线
        }

        // 3. 放入 PC 的待处理队列
        PendingMessage pending = new PendingMessage();
        pending.setRequestId(msg.getRequestId());
        pending.setMessageId(messageId);
        pending.setUserId(userId);
        pending.setContent(content);
        pending.setChannel(channel);
        pending.setAttachmentFileIds(attachmentFileIds != null ? attachmentFileIds : List.of());
        pending.setCreatedAt(LocalDateTime.now());

        deviceQueues.computeIfAbsent(device.getDeviceId(), k -> new ConcurrentLinkedQueue<>()).add(pending);

        log.info("消息已入队: userId={}, messageId={}, deviceId={}", userId, messageId, device.getDeviceId());
        return messageId;
    }

    /**
     * PC 拉取待处理消息
     */
    public List<PendingMessage> pollForDevice(String deviceId) {
        Queue<PendingMessage> queue = deviceQueues.get(deviceId);
        if (queue == null || queue.isEmpty()) return List.of();

        List<PendingMessage> result = new ArrayList<>();
        PendingMessage msg;
        while ((msg = queue.poll()) != null) {
            result.add(msg);
        }
        return result;
    }

    /**
     * PC 回传处理结果
     */
    public void receiveResult(String requestId, String content, String widgetJson, String status,
                              List<String> attachmentFileIds) {
        // 找到原始消息获取 userId
        HubMessage originalMsg = messageRepository.findByRequestId(requestId).orElse(null);
        if (originalMsg == null) {
            log.warn("收到未知 requestId 的结果: {}", requestId);
            return;
        }

        // 更新原消息状态
        originalMsg.setStatus("completed");
        originalMsg.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(originalMsg);

        // 创建回复消息
        String replyId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        HubMessage reply = new HubMessage();
        reply.setMessageId(replyId);
        reply.setUserId(originalMsg.getUserId());
        reply.setRequestId(requestId);
        reply.setChannel(originalMsg.getChannel());
        reply.setRole("assistant");
        reply.setContent(content);
        reply.setWidgetJson(widgetJson);
        reply.setStatus(status != null ? status : "completed");
        messageRepository.save(reply);

        // 关联附件文件到回复消息
        if (attachmentFileIds != null) {
            for (String fileId : attachmentFileIds) {
                fileRepository.findById(fileId).ifPresent(file -> {
                    file.setMessageId(replyId);
                    fileRepository.save(file);
                });
            }
        }

        log.info("结果已保存: requestId={}, replyId={}, files={}", requestId, replyId,
                 attachmentFileIds != null ? attachmentFileIds.size() : 0);
    }

    /**
     * PC 回传流式进度
     */
    public void receiveStream(String requestId, String phase, String streamContent) {
        HubMessage msg = messageRepository.findByRequestId(requestId).orElse(null);
        if (msg == null) return;

        msg.setStreamingPhase(phase);
        msg.setStreamingContent(streamContent);
        msg.setStatus("streaming");
        msg.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(msg);
    }

    /**
     * 清理设备的待处理队列（设备离线时调用）
     */
    public void clearDeviceQueue(String deviceId) {
        deviceQueues.remove(deviceId);
    }

    // ─── 内部类 ─────────────────────────────────────────

    @lombok.Data
    public static class PendingMessage {
        private String requestId;
        private String messageId;
        private String userId;
        private String content;
        private String channel;
        private List<String> attachmentFileIds;
        private LocalDateTime createdAt;
    }
}
