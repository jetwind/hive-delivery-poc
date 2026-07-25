package com.hive.delivery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.repo.*;
import com.hive.delivery.web.ApiDtos.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GraphQueryService {
    private final ProjectService projects;private final DeliveryNodeRepository nodes;private final DeliveryEdgeRepository edges;private final TaskRunRepository runs;private final DeliveryEventRepository events;private final ObjectMapper json;
    public GraphQueryService(ProjectService projects,DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,TaskRunRepository runs,DeliveryEventRepository events,ObjectMapper json){this.projects=projects;this.nodes=nodes;this.edges=edges;this.runs=runs;this.events=events;this.json=json;}
    public GraphSnapshot snapshot(UUID id){var p=projects.get(id);return new GraphSnapshot(new ProjectView(p.getId(),p.getName(),p.getStatus().name(),p.getCurrentGraphRevision(),p.getWorkspacePath()),
      nodes.findByProjectIdOrderBySortOrderAsc(id).stream().map(n->new NodeView(n.getId(),n.getTemplateNodeId(),n.getStageCode(),n.getNodeType().name(),n.getTitle(),n.getDescription(),n.getStatus().name(),n.getExecutorType().name(),n.getHandler(),n.getAgentRole(),criteria(n.getAcceptanceCriteriaJson()),n.getParentNodeId(),n.getSortOrder())).toList(),
      edges.findByProjectId(id).stream().map(e->new EdgeView(e.getId(),e.getFromNodeId(),e.getToNodeId(),e.getEdgeType().name())).toList(),
      runs.findByProjectIdOrderByStartedAtDesc(id).stream().map(r->new RunView(r.getId(),r.getNodeId(),r.getAttempt(),r.getStatus().name(),r.getExternalSessionId(),r.getSummary(),r.getStartedAt(),r.getFinishedAt())).toList(),
      events.findTop200ByProjectIdOrderByCreatedAtDesc(id).stream().map(e->new EventView(e.getId(),e.getEventType().name(),e.getNodeId(),e.getPayloadJson(),e.getCreatedAt())).toList());}
    private List<String> criteria(String value){try{return json.readValue(value==null?"[]":value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
}
