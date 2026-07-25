package com.hive.delivery.service;

import com.hive.delivery.repo.DeliveryNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class HumanGateService {
    private final DeliveryNodeRepository nodes;private final EventService events;private final ProjectControlService control;
    public HumanGateService(DeliveryNodeRepository nodes,EventService events,ProjectControlService control){this.nodes=nodes;this.events=events;this.control=control;}
    @Transactional public void approve(UUID projectId,UUID nodeId){var n=nodes.findById(nodeId).orElseThrow();if(n.getStatus()!=NodeStatus.WAITING_HUMAN)throw new IllegalStateException("Not waiting human");n.setStatus(NodeStatus.COMPLETED);nodes.save(n);events.emit(projectId,EventType.HUMAN_APPROVED,nodeId,Map.of());control.kick(projectId);}
    @Transactional public void reject(UUID projectId,UUID nodeId){var n=nodes.findById(nodeId).orElseThrow();n.setStatus(NodeStatus.FAILED);nodes.save(n);events.emit(projectId,EventType.HUMAN_REJECTED,nodeId,Map.of());}
}
