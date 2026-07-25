package com.hive.delivery.service;

import com.hive.delivery.domain.DeliveryNode;
import com.hive.delivery.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class StageCompletionService {
    private final DeliveryNodeRepository nodes; private final DeliveryEdgeRepository edges; private final EventService events;
    public StageCompletionService(DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,EventService events){this.nodes=nodes;this.edges=edges;this.events=events;}
    public Optional<DeliveryNode> completableStage(UUID projectId){
        return nodes.findByProjectIdOrderBySortOrderAsc(projectId).stream().filter(n->n.getNodeType()==NodeType.STAGE&&n.getStatus()==NodeStatus.RUNNING)
          .filter(s->{var c=nodes.findByProjectIdAndParentNodeIdOrderBySortOrderAsc(projectId,s.getId()); return !c.isEmpty()&&c.stream().allMatch(n->n.getStatus()==NodeStatus.COMPLETED||n.getStatus()==NodeStatus.SUPERSEDED);}).findFirst();
    }
    @Transactional public void complete(UUID projectId,UUID stageId){
        var s=nodes.findById(stageId).orElseThrow();s.setStatus(NodeStatus.COMPLETED);nodes.save(s);events.emit(projectId,EventType.STAGE_COMPLETED,stageId,Map.of("stage",s.getStageCode()));
        for(var edge:edges.findByProjectId(projectId)) if(edge.getFromNodeId().equals(stageId)){
            var next=nodes.findById(edge.getToNodeId()).orElseThrow(); if(next.getStatus()==NodeStatus.BLOCKED){next.setStatus(NodeStatus.READY);nodes.save(next);}
        }
    }
    public boolean projectComplete(UUID projectId){return nodes.findByProjectIdOrderBySortOrderAsc(projectId).stream().filter(n->n.getNodeType()==NodeType.STAGE).allMatch(n->n.getStatus()==NodeStatus.COMPLETED);}
}
