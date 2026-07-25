package com.hive.delivery.web;

import com.hive.delivery.service.*;
import com.hive.delivery.template.TemplateRegistry;
import com.hive.delivery.opencode.OpenCodeClient;
import com.hive.delivery.repo.*;
import com.hive.delivery.domain.DeliveryNode;
import com.hive.delivery.domain.DeliveryProject;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import static com.hive.delivery.web.ApiDtos.*;

@RestController @RequestMapping("/api")
public class DeliveryController {
    private final ProjectService projects;private final GraphQueryService graph;private final ProjectControlService control;private final HumanGateService gates;private final ProjectEventStream stream;private final TemplateRegistry templates;private final EventService events;private final OpenCodeClient opencode;private final TaskRunRepository taskRuns;private final DeliveryNodeRepository nodeRepo;private final DeliveryProjectRepository projectRepo;
    public DeliveryController(ProjectService projects,GraphQueryService graph,ProjectControlService control,HumanGateService gates,ProjectEventStream stream,TemplateRegistry templates,EventService events,OpenCodeClient opencode,TaskRunRepository taskRuns,DeliveryNodeRepository nodeRepo,DeliveryProjectRepository projectRepo){this.projects=projects;this.graph=graph;this.control=control;this.gates=gates;this.stream=stream;this.templates=templates;this.events=events;this.opencode=opencode;this.taskRuns=taskRuns;this.nodeRepo=nodeRepo;this.projectRepo=projectRepo;}
    @GetMapping("/projects") public List<ProjectView> list(){return projects.list().stream().map(p->new ProjectView(p.getId(),p.getName(),p.getStatus().name(),p.getCurrentGraphRevision(),p.getWorkspacePath())).toList();}
    @PostMapping("/projects") public ProjectView create(@RequestBody CreateProjectRequest r){var p=projects.create(r.name(),r.lifecycleCode()==null?"software-delivery":r.lifecycleCode(),r.lifecycleVersion()==null?"1.0.0":r.lifecycleVersion(),r.workspacePath()==null?"../workspace/product-search-demo":r.workspacePath(),r.requirement());return new ProjectView(p.getId(),p.getName(),p.getStatus().name(),p.getCurrentGraphRevision(),p.getWorkspacePath());}
    @GetMapping("/projects/{id}/graph") public GraphSnapshot graph(@PathVariable UUID id){return graph.snapshot(id);}
    @PostMapping("/projects/{id}/start") public Map<String,Object> start(@PathVariable UUID id){control.kick(id);return Map.of("accepted",true);}
    @PostMapping("/projects/{id}/nodes/{nodeId}/approve") public Map<String,Object> approve(@PathVariable UUID id,@PathVariable UUID nodeId){gates.approve(id,nodeId);return Map.of("approved",true);}
    @PostMapping("/projects/{id}/nodes/{nodeId}/reject") public Map<String,Object> reject(@PathVariable UUID id,@PathVariable UUID nodeId){gates.reject(id,nodeId);return Map.of("rejected",true);}
    @PostMapping("/projects/{id}/events/change") public Map<String,Object> change(@PathVariable UUID id,@RequestBody ChangeRequest r){events.emit(id,com.hive.delivery.domain.Enums.EventType.CHANGE_REQUESTED,null,Map.of("description",r.description(),"note","POC persists the event; Graph Patch planner is the next extension point"));return Map.of("accepted",true);}
    @GetMapping(value="/projects/{id}/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter stream(@PathVariable UUID id){return stream.subscribe(id);}
    @PostMapping("/templates/reload") public Map<String,Object> reload() throws Exception{templates.reload();return Map.of("reloaded",true,"count",templates.lifecycles().size());}
    @DeleteMapping("/projects") public Map<String,Object> clearAll(){projects.deleteAll();return Map.of("cleared",true);}
    @GetMapping("/projects/{id}/nodes/{nodeId}/session") public NodeSessionView nodeSession(@PathVariable UUID id,@PathVariable UUID nodeId){
        var run=taskRuns.findByProjectIdOrderByStartedAtDesc(id).stream().filter(r->nodeId.equals(r.getNodeId())).findFirst();
        if(run.isEmpty())return new NodeSessionView(null,null,List.of(),false);
        var sid=run.get().getExternalSessionId();
        if(sid==null)return new NodeSessionView(null,null,List.of(),false);
        var node=nodeRepo.findById(nodeId).orElse(null);
        var project=projectRepo.findById(id).orElse(null);
        String promptText=node!=null&&project!=null?buildPrompt(project,node):null;
        try{
            var raw=opencode.messages(sid);
            List<SessionMsg> msgs=new ArrayList<>();
            if(raw.isArray())for(var m:raw){for(var p:m.path("parts")){if("text".equals(p.path("type").asText())){
            String text=p.path("text").asText();String role=m.path("role").asText("assistant");msgs.add(new SessionMsg(role,text));}}}
            return new NodeSessionView(sid,promptText,msgs,true);
        }catch(Exception e){return new NodeSessionView(sid,promptText,List.of(),false);}
    }
    private String buildPrompt(DeliveryProject p,DeliveryNode n){return String.format("""
你是 Hive Engineering 的交付任务执行 Agent。请在当前 OpenCode 工作区中完成以下任务。
项目：%s
阶段：%s
任务：%s
说明：%s
验收标准：%s

要求：先阅读现有文档和代码；只完成当前任务；必要时修改文档、Java代码和测试；运行相关编译或测试；最后清楚说明修改文件、验证结果和重要发现。不要修改 Hive Delivery Graph。
""",p.getName(),n.getStageCode(),n.getTitle(),Objects.toString(n.getDescription(),""),Objects.toString(n.getAcceptanceCriteriaJson(),"[]"));}
}
