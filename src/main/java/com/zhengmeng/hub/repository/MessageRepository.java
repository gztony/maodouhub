package com.zhengmeng.hub.repository;

import com.zhengmeng.hub.model.HubMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<HubMessage, String> {

    /** 按时间倒序获取用户某频道的消息 */
    List<HubMessage> findByUserIdAndChannelOrderByCreatedAtDesc(String userId, String channel, Pageable pageable);

    /** 获取某 messageId 之前的消息（分页加载更多历史） */
    @Query("SELECT m FROM HubMessage m WHERE m.userId = ?1 AND m.channel = ?2 AND m.createdAt < " +
           "(SELECT m2.createdAt FROM HubMessage m2 WHERE m2.messageId = ?3) ORDER BY m.createdAt DESC")
    List<HubMessage> findBeforeMessage(String userId, String channel, String beforeMessageId, Pageable pageable);

    /** 获取某 messageId 之后的新消息（轮询用） */
    @Query("SELECT m FROM HubMessage m WHERE m.userId = ?1 AND m.channel = ?2 AND m.createdAt > " +
           "(SELECT m2.createdAt FROM HubMessage m2 WHERE m2.messageId = ?3) ORDER BY m.createdAt ASC")
    List<HubMessage> findAfterMessage(String userId, String channel, String sinceMessageId);

    /** 按 requestId 查找 */
    Optional<HubMessage> findByRequestId(String requestId);
}
