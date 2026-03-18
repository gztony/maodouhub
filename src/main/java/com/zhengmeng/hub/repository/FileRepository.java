package com.zhengmeng.hub.repository;

import com.zhengmeng.hub.model.HubFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<HubFile, String> {
    List<HubFile> findByMessageId(String messageId);
}
