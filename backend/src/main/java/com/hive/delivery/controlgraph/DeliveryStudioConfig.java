package com.hive.delivery.controlgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class DeliveryStudioConfig extends LangGraphStudioConfig {
    private final StateGraph<DeliveryControlState> graph;

    public DeliveryStudioConfig(StateGraph<DeliveryControlState> graph) {
        this.graph = graph;
    }

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return Map.of("delivery-control", LangGraphStudioServer.Instance.builder()
                .title("Hive Delivery Control Graph")
                .graph(graph)
                .build());
    }
}
