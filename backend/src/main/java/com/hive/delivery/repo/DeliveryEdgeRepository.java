package com.hive.delivery.repo;

import com.hive.delivery.domain.DeliveryEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.hive.delivery.domain.Enums;

public interface DeliveryEdgeRepository extends JpaRepository<DeliveryEdge, UUID> {
    List<DeliveryEdge> findByProjectId(UUID projectId);
    List<DeliveryEdge> findByProjectIdAndToNodeId(UUID projectId, UUID toNodeId);
}
