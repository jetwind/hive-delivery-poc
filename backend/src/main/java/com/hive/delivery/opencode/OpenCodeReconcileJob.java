package com.hive.delivery.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.hive.delivery.config.OpenCodeProperties;
import com.hive.delivery.domain.*;
import com.hive.delivery.repo.*;
import com.hive.delivery.service.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OpenCodeReconcileJob {
    private static final Logger log=LoggerFactory.getLogger(OpenCodeReconcileJob.class);
    private final TaskRunRepository runs; private final DeliveryNodeRepository nodes; private final OpenCodeClient client;
    private final OpenCodeProperties props; private final EventService events; private final ProjectControlService control;
    public OpenCodeReconcileJob(TaskRunRepository runs,DeliveryNodeRepository nodes,OpenCodeClient client,OpenCodeProperties props,EventService events,@Lazy ProjectControlService control){this.runs=runs;this.nodes=nodes;this.client=client;this.props=props;this.events=events;this.control=control;}
    @Scheduled(fixedDelayString="${hive.opencode.reconcile-delay-ms:5000}") public void poll(){
        var waiting=runs.findByStatus(RunStatus.WAITING_EXTERNAL); if(!waiting.isEmpty()) log.info("[Reconcile] {} WAITING runs",waiting.size()); else return;
        if(props.mock()){for(var r:waiting) if(r.getStartedAt().isBefore(Instant.now().minusSeconds(2))) finishMock(r);return;}
        if(!props.enabled()) return;
        JsonNode statuses;try{statuses=client.statuses();}catch(Exception e){return;}
        for(var run:waiting){JsonNode s=statuses.path(run.getExternalSessionId());String type=s.path("type").asText();if("idle".equalsIgnoreCase(type)) finishReal(run);}
    }
    @Transactional void finishMock(TaskRun run){complete(run,"Mock OpenCode execution completed", "[]", "[]");}
    @Transactional void finishReal(TaskRun run){
        try{complete(run,client.messages(run.getExternalSessionId()).toString(),client.diff(run.getExternalSessionId()).toString(),"[]");}
        catch(Exception e){run.setSummary("Unable to collect OpenCode result: "+e.getMessage());runs.save(run);}
    }
    void complete(TaskRun run,String summary,String changed,String findings){
        var node=nodes.findById(run.getNodeId()).orElseThrow();run.setStatus(RunStatus.COMPLETED);run.setSummary(summary);run.setChangedFilesJson(changed);run.setFindingsJson(findings);run.setFinishedAt(Instant.now());runs.save(run);node.setStatus(NodeStatus.COMPLETED);nodes.save(node);events.emit(run.getProjectId(),EventType.NODE_COMPLETED,node.getId(),Map.of("sessionId",Objects.toString(run.getExternalSessionId(),"")));control.kick(run.getProjectId());
    }
}
