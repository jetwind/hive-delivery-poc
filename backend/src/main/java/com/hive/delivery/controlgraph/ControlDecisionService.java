package com.hive.delivery.controlgraph;

import com.hive.delivery.repo.DeliveryNodeRepository;
import com.hive.delivery.service.*;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.hive.delivery.domain.Enums.*;

@Service
public class ControlDecisionService {
    private final StageExpansionService expansion; private final ReadyNodeService ready; private final StageCompletionService stages;
    private final DeliveryNodeRepository nodes;
    public ControlDecisionService(StageExpansionService expansion,ReadyNodeService ready,StageCompletionService stages,DeliveryNodeRepository nodes){this.expansion=expansion;this.ready=ready;this.stages=stages;this.nodes=nodes;}
    public Decision decide(UUID projectId){
        ready.unlock(projectId);
        var waiting=nodes.findByProjectIdOrderBySortOrderAsc(projectId).stream().anyMatch(n->n.getStatus()==NodeStatus.WAITING_EXTERNAL||n.getStatus()==NodeStatus.WAITING_HUMAN||n.getStatus()==NodeStatus.FAILED);
        if(waiting) return new Decision("IDLE",null,"waiting external/human or failed");
        var e=expansion.findExpandable(projectId);if(e.isPresent())return new Decision("EXPAND_STAGE",e.get().getId(),"expand stage");
        var n=ready.nextReady(projectId);if(n.isPresent())return new Decision("EXECUTE_NODE",n.get().getId(),"execute node");
        var s=stages.completableStage(projectId);if(s.isPresent())return new Decision("COMPLETE_STAGE",s.get().getId(),"complete stage");
        if(stages.projectComplete(projectId))return new Decision("COMPLETE_PROJECT",null,"all stages complete");
        return new Decision("IDLE",null,"no actionable node");
    }
    public record Decision(String action,UUID nodeId,String message){}
}
