package com.hive.delivery.repo;

import com.hive.delivery.domain.DeliveryNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.hive.delivery.domain.Enums;

public interface DeliveryNodeRepository extends JpaRepository<DeliveryNode, UUID> {
    List<DeliveryNode> findByProjectIdOrderBySortOrderAsc(UUID projectId);
    List<DeliveryNode> findByProjectIdAndParentNodeIdOrderBySortOrderAsc(UUID projectId, UUID parentNodeId);
    List<DeliveryNode> findByProjectIdAndStatusOrderBySortOrderAsc(UUID projectId, Enums.NodeStatus status);
}
