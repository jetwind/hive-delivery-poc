package com.hive.delivery.service;

import com.hive.delivery.domain.DeliveryNode;
import com.hive.delivery.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class ReadyNodeService {
    private final DeliveryNodeRepository nodes; private final DeliveryEdgeRepository edges; private final EventService events;
    public ReadyNodeService(DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,EventService events){this.nodes=nodes;this.edges=edges;this.events=events;}
    public Optional<DeliveryNode> nextReady(UUID projectId){ return nodes.findByProjectIdAndStatusOrderBySortOrderAsc(projectId,NodeStatus.READY).stream().filter(n->n.getNodeType()!=NodeType.STAGE).findFirst(); }
    @Transactional public void unlock(UUID projectId){
        var all=nodes.findByProjectIdOrderBySortOrderAsc(projectId); Map<UUID,DeliveryNode> byId=new HashMap<>(); all.forEach(n->byId.put(n.getId(),n));
        for(var n:all){ if(n.getStatus()!=NodeStatus.BLOCKED||n.getNodeType()==NodeType.STAGE) continue;
            var incoming=edges.findByProjectIdAndToNodeId(projectId,n.getId()); if(!incoming.isEmpty()&&incoming.stream().allMatch(e->byId.get(e.getFromNodeId()).getStatus()==NodeStatus.COMPLETED)){
                n.setStatus(NodeStatus.READY);nodes.save(n);events.emit(projectId,EventType.NODE_READY,n.getId(),Map.of("title",n.getTitle()));
            }
        }
    }
}
