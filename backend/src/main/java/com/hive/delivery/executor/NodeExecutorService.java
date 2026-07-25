package com.hive.delivery.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.config.OpenCodeProperties;
import com.hive.delivery.domain.*;
import com.hive.delivery.opencode.OpenCodeClient;
import com.hive.delivery.repo.*;
import com.hive.delivery.service.EventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class NodeExecutorService {
    private final DeliveryNodeRepository nodes; private final TaskRunRepository runs; private final DeliveryProjectRepository projects;
    private final OpenCodeClient opencode; private final OpenCodeProperties props; private final EventService events; private final ObjectMapper json;
    public NodeExecutorService(DeliveryNodeRepository nodes,TaskRunRepository runs,DeliveryProjectRepository projects,OpenCodeClient opencode,OpenCodeProperties props,EventService events,ObjectMapper json){
        this.nodes=nodes;this.runs=runs;this.projects=projects;this.opencode=opencode;this.props=props;this.events=events;this.json=json;}
    @Transactional public void dispatch(UUID projectId,UUID nodeId){
        var node=nodes.findById(nodeId).orElseThrow(); var project=projects.findById(projectId).orElseThrow();
        if(node.getNodeType()==NodeType.GATE||node.getExecutorType()==ExecutorType.HUMAN){node.setStatus(NodeStatus.WAITING_HUMAN);nodes.save(node);events.emit(projectId,EventType.NODE_WAITING,nodeId,Map.of("reason","human_gate"));return;}
        int attempt=(int)runs.countByNodeId(nodeId)+1;var run=new TaskRun();run.setId(UUID.randomUUID());run.setProjectId(projectId);run.setNodeId(nodeId);run.setAttempt(attempt);run.setStartedAt(Instant.now());run.setExecutorType(node.getExecutorType().name());run.setStatus(RunStatus.RUNNING);runs.save(run);
        node.setStatus(NodeStatus.DISPATCHING);nodes.save(node);events.emit(projectId,EventType.NODE_STARTED,nodeId,Map.of("title",node.getTitle(),"handler",node.getHandler()));
        if(node.getExecutorType()==ExecutorType.CODE){completeCode(node,run);return;}
        if(node.getExecutorType()==ExecutorType.AGENT){dispatchAgent(project,node,run);return;}
        fail(node,run,"Unsupported executor: "+node.getExecutorType());
    }
    private void dispatchAgent(DeliveryProject project,DeliveryNode node,TaskRun run){
        try{
            if(props.mock()) { run.setExternalSessionId("mock-"+UUID.randomUUID()); run.setStatus(RunStatus.WAITING_EXTERNAL);runs.save(run);node.setStatus(NodeStatus.WAITING_EXTERNAL);nodes.save(node);events.emit(project.getId(),EventType.NODE_WAITING,node.getId(),Map.of("mock",true));return; }
            if(!props.enabled()) throw new IllegalStateException("OpenCode integration disabled");
            opencode.health(); var s=opencode.createSession("Task-"+node.getId()+"-"+node.getTitle(),project.getPlannerSessionId()); String sid=s.path("id").asText();
            if(sid.isBlank()) throw new IllegalStateException("OpenCode createSession response has no id: "+s);
            run.setExternalSessionId(sid);run.setStatus(RunStatus.WAITING_EXTERNAL);runs.save(run);node.setStatus(NodeStatus.WAITING_EXTERNAL);nodes.save(node);
            opencode.promptAsync(sid,props.agent(),prompt(project,node));events.emit(project.getId(),EventType.NODE_WAITING,node.getId(),Map.of("sessionId",sid));
        }catch(Exception e){fail(node,run,e.getMessage());}
    }
    private String prompt(DeliveryProject p,DeliveryNode n){return """
你是 Hive Engineering 的交付任务执行 Agent。请在当前 OpenCode 工作区中完成以下任务。
项目：%s
工作区（仅供校验）：%s
阶段：%s
任务：%s
说明：%s
验收标准：%s

要求：先阅读现有文档和代码；只完成当前任务；必要时修改文档、Java代码和测试；运行相关编译或测试；最后清楚说明修改文件、验证结果和重要发现。不要修改 Hive Delivery Graph。
""".formatted(p.getName(),p.getWorkspacePath(),n.getStageCode(),n.getTitle(),Objects.toString(n.getDescription(),""),Objects.toString(n.getAcceptanceCriteriaJson(),"[]"));}
    private void completeCode(DeliveryNode n,TaskRun r){r.setStatus(RunStatus.COMPLETED);r.setSummary("Deterministic handler completed: "+n.getHandler());r.setFinishedAt(Instant.now());runs.save(r);n.setStatus(NodeStatus.COMPLETED);nodes.save(n);events.emit(n.getProjectId(),EventType.NODE_COMPLETED,n.getId(),Map.of("handler",n.getHandler()));}
    private void fail(DeliveryNode n,TaskRun r,String m){r.setStatus(RunStatus.FAILED);r.setSummary(m);r.setFinishedAt(Instant.now());runs.save(r);n.setStatus(NodeStatus.FAILED);nodes.save(n);events.emit(n.getProjectId(),EventType.NODE_FAILED,n.getId(),Map.of("error",Objects.toString(m,"unknown")));}
}
