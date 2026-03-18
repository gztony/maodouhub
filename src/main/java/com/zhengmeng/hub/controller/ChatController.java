package com.zhengmeng.hub.controller;

import com.zhengmeng.hub.model.HubMessage;
import com.zhengmeng.hub.repository.DeviceRepository;
import com.zhengmeng.hub.repository.MessageRepository;
import com.zhengmeng.hub.service.MessageRouterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小毛豆聊天 API
 * <p>
 * 所有接口需要 JWT 认证（Authorization: Bearer token）
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final MessageRouterService messageRouter;
    private final MessageRepository messageRepository;
    private final DeviceRepository deviceRepository;

    /** 发送消息 */
    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody SendRequest req, Authentication auth) {
        String userId = auth.getName();
        String channel = req.getChannel() != null ? req.getChannel() : "work";

        // 检查 PC 是否在线
        boolean pcOnline = deviceRepository.findByUserIdAndOnlineTrue(userId).isPresent();
        if (!pcOnline) {
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", "你的毛豆 PC 当前不在线，请先启动 PC 端毛豆"
            ));
        }

        String messageId = messageRouter.routeFromMobile(
            userId, req.getContent(), channel,
            req.getAttachmentFileIds()
        );

        if (messageId == null) {
            return ResponseEntity.ok(Map.of("ok", false, "error", "消息发送失败，PC 不在线"));
        }

        return ResponseEntity.accepted().body(Map.of(
            "ok", true,
            "messageId", messageId,
            "status", "delivering"
        ));
    }

    /** 拉取历史消息 */
    @GetMapping("/messages")
    public ResponseEntity<?> getMessages(
            @RequestParam(defaultValue = "work") String channel,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        String userId = auth.getName();
        limit = Math.min(limit, 50);

        List<HubMessage> messages;
        if (before != null) {
            messages = messageRepository.findBeforeMessage(userId, channel, before, PageRequest.of(0, limit));
        } else {
            messages = messageRepository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, channel, PageRequest.of(0, limit));
        }

        // 倒转为正序
        messages = messages.reversed();

        List<Map<String, Object>> result = messages.stream().map(this::toMessageView).toList();
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "messages", result,
            "hasMore", messages.size() == limit
        ));
    }

    /** 轮询新消息 */
    @GetMapping("/poll")
    public ResponseEntity<?> poll(
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "work") String channel,
            Authentication auth) {
        String userId = auth.getName();

        boolean pcOnline = deviceRepository.findByUserIdAndOnlineTrue(userId).isPresent();

        List<HubMessage> newMessages = List.of();
        if (since != null) {
            newMessages = messageRepository.findAfterMessage(userId, channel, since);
        }

        // 检查是否有正在流式生成的内容
        Map<String, Object> streaming = null;
        HubMessage streamingMsg = messageRepository
            .findByUserIdAndChannelOrderByCreatedAtDesc(userId, channel, PageRequest.of(0, 1))
            .stream().findFirst().orElse(null);
        if (streamingMsg != null && "streaming".equals(streamingMsg.getStatus())) {
            streaming = new HashMap<>();
            streaming.put("active", true);
            streaming.put("content", streamingMsg.getStreamingContent());
            streaming.put("phase", streamingMsg.getStreamingPhase());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("messages", newMessages.stream().map(this::toMessageView).toList());
        result.put("pcOnline", pcOnline);
        if (streaming != null) result.put("streaming", streaming);

        return ResponseEntity.ok(result);
    }

    // ─── 内部方法 ─────────────────────────────────────────

    private Map<String, Object> toMessageView(HubMessage msg) {
        Map<String, Object> view = new HashMap<>();
        view.put("messageId", msg.getMessageId());
        view.put("role", msg.getRole());
        view.put("content", msg.getContent());
        view.put("status", msg.getStatus());
        view.put("createdAt", msg.getCreatedAt().toString());
        if (msg.getWidgetJson() != null) view.put("widget", msg.getWidgetJson());
        return view;
    }

    @Data
    public static class SendRequest {
        private String content;
        private String channel;
        private List<String> attachmentFileIds;
    }
}
