package com.hive.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.domain.*;
import com.hive.delivery.repo.*;
import com.hive.delivery.template.TemplateRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class StageExpansionService {
    private final ProjectService projectService; private final DeliveryNodeRepository nodes; private final DeliveryEdgeRepository edges;
    private final TemplateRegistry templates; private final EventService events; private final ObjectMapper json;
    public StageExpansionService(ProjectService projectService,DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,TemplateRegistry templates,EventService events,ObjectMapper json){this.projectService=projectService;this.nodes=nodes;this.edges=edges;this.templates=templates;this.events=events;this.json=json;}
    public Optional<DeliveryNode> findExpandable(UUID projectId){
        return nodes.findByProjectIdOrderBySortOrderAsc(projectId).stream().filter(n->n.getNodeType()==NodeType.STAGE&&n.getStatus()==NodeStatus.READY)
                .filter(n->nodes.findByProjectIdAndParentNodeIdOrderBySortOrderAsc(projectId,n.getId()).isEmpty()).findFirst();
    }
    @Transactional public void expand(UUID projectId,UUID stageId){
        var project=projectService.get(projectId); var stage=nodes.findById(stageId).orElseThrow();
        var life=templates.lifecycle(project.getLifecycleTemplateCode(),project.getLifecycleTemplateVersion());
        var ref=life.stages().stream().filter(s->s.code().equals(stage.getStageCode())).findFirst().orElseThrow();
        var st=templates.stageByFile(ref.template()); projectService.bumpRevision(projectId); int rev=projectService.get(projectId).getCurrentGraphRevision();
        Map<String,UUID> ids=new LinkedHashMap<>(); int index=1;
        for(var nt:st.nodes()){
            var n=new DeliveryNode();n.setId(UUID.randomUUID());n.setProjectId(projectId);n.setTemplateNodeId(nt.id());n.setStageCode(stage.getStageCode());
            n.setNodeType(NodeType.valueOf(nt.type()));n.setTitle(nt.name());n.setDescription(nt.description());n.setStatus(nt.dependsOn()==null||nt.dependsOn().isEmpty()?NodeStatus.READY:NodeStatus.BLOCKED);
            n.setExecutorType(ExecutorType.valueOf(nt.executor().type()));n.setHandler(nt.executor().handler());n.setAgentRole(nt.executor().role());n.setParentNodeId(stageId);n.setCreatedRevision(rev);n.setSortOrder(stage.getSortOrder()+index++);
            try{n.setAcceptanceCriteriaJson(json.writeValueAsString(nt.acceptanceCriteria()==null?List.of():nt.acceptanceCriteria()));}catch(Exception e){n.setAcceptanceCriteriaJson("[]");}
            nodes.save(n);ids.put(nt.id(),n.getId()); if(n.getStatus()==NodeStatus.READY) events.emit(projectId,EventType.NODE_READY,n.getId(),Map.of("title",n.getTitle()));
        }
        for(var nt:st.nodes()) if(nt.dependsOn()!=null) for(String dep:nt.dependsOn()){
            var e=new DeliveryEdge();e.setId(UUID.randomUUID());e.setProjectId(projectId);e.setFromNodeId(ids.get(dep));e.setToNodeId(ids.get(nt.id()));e.setEdgeType(EdgeType.DEPENDS_ON);e.setCreatedRevision(rev);edges.save(e);
        }
        stage.setStatus(NodeStatus.RUNNING);nodes.save(stage);events.emit(projectId,EventType.STAGE_EXPANDED,stageId,Map.of("stage",stage.getStageCode(),"revision",rev));
    }
}
