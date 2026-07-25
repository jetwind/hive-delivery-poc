package com.hive.delivery.controlgraph;

import com.hive.delivery.executor.NodeExecutorService;
import com.hive.delivery.service.*;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.Command;
import org.springframework.context.annotation.*;
import java.util.*;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.*;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class DeliveryControlGraphConfig {
    private final ControlDecisionService decisions; private final StageExpansionService expansion; private final NodeExecutorService executor;
    private final StageCompletionService stages; private final ProjectService projects;
    public DeliveryControlGraphConfig(ControlDecisionService decisions,StageExpansionService expansion,NodeExecutorService executor,StageCompletionService stages,ProjectService projects){this.decisions=decisions;this.expansion=expansion;this.executor=executor;this.stages=stages;this.projects=projects;}
    @Bean public StateGraph<DeliveryControlState> deliveryControlGraph() throws GraphStateException {
        return new StateGraph<>(DeliveryControlState::new)
          .addNode("load_context",node_async(state->{var d=decisions.decide(UUID.fromString(state.projectId()));Map<String,Object> u=new HashMap<>();u.put(DeliveryControlState.ACTION,d.action());u.put(DeliveryControlState.MESSAGE,d.message());if(d.nodeId()!=null)u.put(DeliveryControlState.CURRENT_NODE_ID,d.nodeId().toString());return u;}))
          .addNode("route",(state,config)->completedFuture(Map.of()))
          .addNode("expand_stage",node_async(state->{expansion.expand(UUID.fromString(state.projectId()),UUID.fromString(state.currentNodeId().orElseThrow()));return Map.of();}))
          .addNode("execute_node",node_async(state->{executor.dispatch(UUID.fromString(state.projectId()),UUID.fromString(state.currentNodeId().orElseThrow()));return Map.of();}))
          .addNode("complete_stage",node_async(state->{stages.complete(UUID.fromString(state.projectId()),UUID.fromString(state.currentNodeId().orElseThrow()));return Map.of();}))
          .addNode("complete_project",node_async(state->{projects.markCompleted(UUID.fromString(state.projectId()));return Map.of();}))
          .addNode("idle",node_async(state->Map.of()))
          .addEdge(START,"load_context").addEdge("load_context","route")
          .addConditionalEdges("route",(state,config)->completedFuture(new Command(state.action())),Map.of(
              "EXPAND_STAGE","expand_stage","EXECUTE_NODE","execute_node","COMPLETE_STAGE","complete_stage","COMPLETE_PROJECT","complete_project","IDLE","idle"))
          .addEdge("expand_stage",END).addEdge("execute_node",END).addEdge("complete_stage",END).addEdge("complete_project",END).addEdge("idle",END);
    }
}
