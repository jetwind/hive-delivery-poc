package com.hive.delivery.service;

import com.hive.delivery.config.DeliveryProperties;
import com.hive.delivery.controlgraph.*;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ProjectControlService {
    private final StateGraph<DeliveryControlState> graph; private final DeliveryProperties properties; private final ProjectService projects;
    private final ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor();
    public ProjectControlService(StateGraph<DeliveryControlState> graph,DeliveryProperties properties,ProjectService projects){this.graph=graph;this.properties=properties;this.projects=projects;}
    public void kick(UUID projectId){pool.submit(()->run(projectId));}
    public void run(UUID projectId){
        projects.markRunning(projectId);
        try{var compiled=graph.compile();for(int i=0;i<properties.maxTicksPerRun();i++){
            DeliveryControlState last=null;for(var out:compiled.stream(Map.of(DeliveryControlState.PROJECT_ID,projectId.toString()))){last=out.state();}
            if(last==null||"IDLE".equals(last.action())||"COMPLETE_PROJECT".equals(last.action())||"EXECUTE_NODE".equals(last.action())) break;
        }}catch(Exception e){throw new IllegalStateException("Control graph failed",e);}
        if(projects.get(projectId).getStatus()!=com.hive.delivery.domain.Enums.ProjectStatus.COMPLETED) projects.markWaiting(projectId);
    }
}
