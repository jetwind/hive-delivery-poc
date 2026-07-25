package com.hive.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.domain.*;
import com.hive.delivery.repo.*;
import com.hive.delivery.template.TemplateRegistry;
import com.hive.delivery.template.TemplateModels.StageTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class StageExpansionService {
    private final ProjectService projectService; private final DeliveryNodeRepository nodes; private final DeliveryEdgeRepository edges;
    private final TemplateRegistry templates; private final EventService events; private final ObjectMapper json;
    private final DynamicStagePlanner planner;
    public StageExpansionService(ProjectService projectService,DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,TemplateRegistry templates,EventService events,ObjectMapper json,DynamicStagePlanner planner){this.projectService=projectService;this.nodes=nodes;this.edges=edges;this.templates=templates;this.events=events;this.json=json;this.planner=planner;}
    public Optional<DeliveryNode> findExpandable(UUID projectId){
        return nodes.findByProjectIdOrderBySortOrderAsc(projectId).stream().filter(n->n.getNodeType()==NodeType.STAGE&&n.getStatus()==NodeStatus.READY)
                .filter(n->nodes.findByProjectIdAndParentNodeIdOrderBySortOrderAsc(projectId,n.getId()).isEmpty()).findFirst();
    }
    @Transactional public void expand(UUID projectId,UUID stageId){
        var project=projectService.get(projectId); var stage=nodes.findById(stageId).orElseThrow();
        var life=templates.lifecycle(project.getLifecycleTemplateCode(),project.getLifecycleTemplateVersion());
        var ref=life.stages().stream().filter(s->s.code().equals(stage.getStageCode())).findFirst().orElseThrow();
        String objective=project.getRequirement();
        if(objective==null||objective.isBlank()) try{objective=ref.getClass().getMethod("objective").invoke(ref).toString();}catch(Exception ignored){}
        if(objective!=null&&!objective.isBlank()&&planner!=null){
            var plans=planner.plan(project.getName(),project.getWorkspacePath(),stage.getStageCode(),stage.getTitle(),objective);
            if(!plans.isEmpty()){applyPlan(project,stage,plans);return;}
        }
        var template=templates.stageByFile(ref.template());
        if(template==null) return;
        applyTemplate(projectId,stage,template,project.getCurrentGraphRevision());
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void applyPlan(DeliveryProject project,DeliveryNode stage,List<com.hive.delivery.service.DynamicStagePlanner.NodePlan> plans){
        projectService.bumpRevision(project.getId()); int rev=projectService.get(project.getId()).getCurrentGraphRevision();
        Map<String,UUID> ids=new LinkedHashMap<>(); int index=1;
        for(var p:plans){
            var n=new DeliveryNode();n.setId(UUID.randomUUID());n.setProjectId(project.getId());n.setTemplateNodeId("dynamic-"+index);n.setStageCode(stage.getStageCode());
            n.setNodeType("GATE".equals(p.type())?NodeType.GATE:NodeType.TASK);n.setTitle(p.name());n.setDescription(p.description());n.setStatus(p.dependsOn()==null||p.dependsOn().isEmpty()?NodeStatus.READY:NodeStatus.BLOCKED);
            var exe=p.executor();n.setExecutorType(ExecutorType.valueOf(exe.getOrDefault("type","AGENT")));n.setHandler(exe.getOrDefault("handler","opencode.task"));n.setAgentRole(exe.getOrDefault("role",""));n.setParentNodeId(stage.getId());n.setCreatedRevision(rev);n.setSortOrder(stage.getSortOrder()+index++);
            try{n.setAcceptanceCriteriaJson(json.writeValueAsString(p.acceptanceCriteria()==null?List.of():p.acceptanceCriteria()));}catch(Exception e){n.setAcceptanceCriteriaJson("[]");}
            nodes.save(n);ids.put(p.name(),n.getId());if(n.getStatus()==NodeStatus.READY) events.emit(project.getId(),EventType.NODE_READY,n.getId(),Map.of("title",n.getTitle()));
            for(String dep:p.dependsOn()){var ee=new DeliveryEdge();ee.setId(UUID.randomUUID());ee.setProjectId(project.getId());ee.setFromNodeId(ids.get(dep));ee.setToNodeId(ids.get(p.name()));ee.setEdgeType(EdgeType.DEPENDS_ON);ee.setCreatedRevision(rev);edges.save(ee);}
        }
        stage.setStatus(NodeStatus.RUNNING);nodes.save(stage);events.emit(project.getId(),EventType.STAGE_EXPANDED,stage.getId(),Map.of("stage",stage.getStageCode(),"dynamic",true,"revision",rev));
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void applyTemplate(UUID projectId,DeliveryNode stage,Object template,int currentRev){
        projectService.bumpRevision(projectId); int rev=projectService.get(projectId).getCurrentGraphRevision();
        var t=(StageTemplate)template;
        Map<String,UUID> ids=new LinkedHashMap<>(); int index=1;
        for(var nt:t.nodes()){
            var n=new DeliveryNode();n.setId(UUID.randomUUID());n.setProjectId(projectId);n.setTemplateNodeId(nt.id());n.setStageCode(stage.getStageCode());
            n.setNodeType(NodeType.valueOf(nt.type()));n.setTitle(nt.name());n.setDescription(nt.description());n.setStatus(nt.dependsOn()==null||nt.dependsOn().isEmpty()?NodeStatus.READY:NodeStatus.BLOCKED);
            n.setExecutorType(ExecutorType.valueOf(nt.executor().type()));n.setHandler(nt.executor().handler());n.setAgentRole(nt.executor().role());n.setParentNodeId(stage.getId());n.setCreatedRevision(rev);n.setSortOrder(stage.getSortOrder()+index++);
            try{n.setAcceptanceCriteriaJson(json.writeValueAsString(nt.acceptanceCriteria()==null?List.of():nt.acceptanceCriteria()));}catch(Exception e){n.setAcceptanceCriteriaJson("[]");}
            nodes.save(n);ids.put(nt.id(),n.getId());if(n.getStatus()==NodeStatus.READY) events.emit(projectId,EventType.NODE_READY,n.getId(),Map.of("title",n.getTitle()));
        }
        for(var nt:t.nodes()) if(nt.dependsOn()!=null) for(String dep:nt.dependsOn()){
            var e=new DeliveryEdge();e.setId(UUID.randomUUID());e.setProjectId(projectId);e.setFromNodeId(ids.get(dep));e.setToNodeId(ids.get(nt.id()));e.setEdgeType(EdgeType.DEPENDS_ON);e.setCreatedRevision(rev);edges.save(e);
        }
        stage.setStatus(NodeStatus.RUNNING);nodes.save(stage);events.emit(projectId,EventType.STAGE_EXPANDED,stage.getId(),Map.of("stage",stage.getStageCode(),"revision",rev));
    }
    private boolean dynamicExpand(DeliveryProject project,DeliveryNode stage,Object ref){return false;}
}
