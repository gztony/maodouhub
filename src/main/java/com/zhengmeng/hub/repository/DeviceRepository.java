package com.zhengmeng.hub.repository;

import com.zhengmeng.hub.model.HubDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<HubDevice, String> {
    Optional<HubDevice> findByDeviceId(String deviceId);
    List<HubDevice> findByUserId(String userId);
    Optional<HubDevice> findByUserIdAndOnlineTrue(String userId);
}
