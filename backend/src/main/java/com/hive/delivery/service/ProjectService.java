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
public class ProjectService {
    private final DeliveryProjectRepository projects; private final DeliveryNodeRepository nodes; private final DeliveryEdgeRepository edges;
    private final TaskRunRepository taskRuns; private final DeliveryEventRepository eventsRepo;
    private final TemplateRegistry templates; private final EventService events;
    public ProjectService(DeliveryProjectRepository projects,DeliveryNodeRepository nodes,DeliveryEdgeRepository edges,TaskRunRepository taskRuns,DeliveryEventRepository eventsRepo,TemplateRegistry templates,EventService events){
        this.projects=projects;this.nodes=nodes;this.edges=edges;this.taskRuns=taskRuns;this.eventsRepo=eventsRepo;this.templates=templates;this.events=events;}
    @Transactional public DeliveryProject create(String name,String lifecycleCode,String version,String workspace,String requirement){
        var t=templates.lifecycle(lifecycleCode,version); var p=DeliveryProject.create(name,lifecycleCode,version,workspace);
        if(requirement!=null&&!requirement.isBlank()) p.setRequirement(requirement);
        projects.save(p);
        Map<String,UUID> ids=new LinkedHashMap<>(); int order=0;
        for(var s:t.stages()){
            var n=new DeliveryNode(); n.setId(UUID.randomUUID()); n.setProjectId(p.getId()); n.setTemplateNodeId(s.id()); n.setStageCode(s.code());
            n.setNodeType(NodeType.STAGE); n.setTitle(s.code()+" "+s.name()); n.setDescription("Lifecycle stage"); n.setStatus(s.dependsOn()==null||s.dependsOn().isEmpty()?NodeStatus.READY:NodeStatus.BLOCKED);
            n.setExecutorType(ExecutorType.NONE); n.setCreatedRevision(1); n.setSortOrder(order++*1000); nodes.save(n); ids.put(s.id(),n.getId());
        }
        for(var s:t.stages()) if(s.dependsOn()!=null) for(String dep:s.dependsOn()){
            var e=new DeliveryEdge();e.setId(UUID.randomUUID());e.setProjectId(p.getId());e.setFromNodeId(ids.get(dep));e.setToNodeId(ids.get(s.id()));e.setEdgeType(EdgeType.DEPENDS_ON);e.setCreatedRevision(1);edges.save(e);
        }
        events.emit(p.getId(),EventType.PROJECT_CREATED,null,Map.of("template",lifecycleCode,"version",version)); return p;
    }
    public DeliveryProject get(UUID id){return projects.findById(id).orElseThrow();}
    public List<DeliveryProject> list(){return projects.findAll();}
    @Transactional public void markRunning(UUID id){var p=get(id);p.setStatus(ProjectStatus.RUNNING);projects.save(p);events.emit(id,EventType.PROJECT_STARTED,null,Map.of());}
    @Transactional public void markWaiting(UUID id){var p=get(id);if(p.getStatus()!=ProjectStatus.COMPLETED){p.setStatus(ProjectStatus.WAITING);projects.save(p);}}
    @Transactional public void markCompleted(UUID id){var p=get(id);p.setStatus(ProjectStatus.COMPLETED);projects.save(p);events.emit(id,EventType.PROJECT_COMPLETED,null,Map.of());}
    @Transactional public void bumpRevision(UUID id){var p=get(id);p.setCurrentGraphRevision(p.getCurrentGraphRevision()+1);projects.save(p);}
    @Transactional public void deleteAll(){
        var all=projects.findAll(); if(all.isEmpty())return;
        for(var p:all){
            var pid=p.getId();
            taskRuns.deleteAll(taskRuns.findByProjectIdOrderByStartedAtDesc(pid));
            eventsRepo.deleteAll(eventsRepo.findTop200ByProjectIdOrderByCreatedAtDesc(pid));
            nodes.deleteAll(nodes.findByProjectIdOrderBySortOrderAsc(pid));
            edges.deleteAll(edges.findByProjectId(pid));
            projects.delete(p);
        }
    }
}
