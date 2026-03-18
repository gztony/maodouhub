package com.zhengmeng.hub.repository;

import com.zhengmeng.hub.model.HubUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<HubUser, String> {
    Optional<HubUser> findByUserId(String userId);
}
