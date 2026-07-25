package com.hive.delivery.repo;

import com.hive.delivery.domain.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.hive.delivery.domain.Enums;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findTop200ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
