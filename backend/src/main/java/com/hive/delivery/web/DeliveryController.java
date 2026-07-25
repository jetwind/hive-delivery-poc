package com.hive.delivery.web;

import com.hive.delivery.service.*;
import com.hive.delivery.template.TemplateRegistry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import static com.hive.delivery.web.ApiDtos.*;

@RestController @RequestMapping("/api")
public class DeliveryController {
    private final ProjectService projects;private final GraphQueryService graph;private final ProjectControlService control;private final HumanGateService gates;private final ProjectEventStream stream;private final TemplateRegistry templates;private final EventService events;
    public DeliveryController(ProjectService projects,GraphQueryService graph,ProjectControlService control,HumanGateService gates,ProjectEventStream stream,TemplateRegistry templates,EventService events){this.projects=projects;this.graph=graph;this.control=control;this.gates=gates;this.stream=stream;this.templates=templates;this.events=events;}
    @GetMapping("/projects") public List<ProjectView> list(){return projects.list().stream().map(p->new ProjectView(p.getId(),p.getName(),p.getStatus().name(),p.getCurrentGraphRevision(),p.getWorkspacePath())).toList();}
    @PostMapping("/projects") public ProjectView create(@RequestBody CreateProjectRequest r){var p=projects.create(r.name(),r.lifecycleCode()==null?"software-delivery":r.lifecycleCode(),r.lifecycleVersion()==null?"1.0.0":r.lifecycleVersion(),r.workspacePath()==null?"../workspace/product-search-demo":r.workspacePath());return new ProjectView(p.getId(),p.getName(),p.getStatus().name(),p.getCurrentGraphRevision(),p.getWorkspacePath());}
    @GetMapping("/projects/{id}/graph") public GraphSnapshot graph(@PathVariable UUID id){return graph.snapshot(id);}
    @PostMapping("/projects/{id}/start") public Map<String,Object> start(@PathVariable UUID id){control.kick(id);return Map.of("accepted",true);}
    @PostMapping("/projects/{id}/nodes/{nodeId}/approve") public Map<String,Object> approve(@PathVariable UUID id,@PathVariable UUID nodeId){gates.approve(id,nodeId);return Map.of("approved",true);}
    @PostMapping("/projects/{id}/nodes/{nodeId}/reject") public Map<String,Object> reject(@PathVariable UUID id,@PathVariable UUID nodeId){gates.reject(id,nodeId);return Map.of("rejected",true);}
    @PostMapping("/projects/{id}/events/change") public Map<String,Object> change(@PathVariable UUID id,@RequestBody ChangeRequest r){events.emit(id,com.hive.delivery.domain.Enums.EventType.CHANGE_REQUESTED,null,Map.of("description",r.description(),"note","POC persists the event; Graph Patch planner is the next extension point"));return Map.of("accepted",true);}
    @GetMapping(value="/projects/{id}/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter stream(@PathVariable UUID id){return stream.subscribe(id);}
    @PostMapping("/templates/reload") public Map<String,Object> reload() throws Exception{templates.reload();return Map.of("reloaded",true,"count",templates.lifecycles().size());}
}
