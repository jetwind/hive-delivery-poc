package com.hive.delivery.controlgraph;

import org.bsc.langgraph4j.state.AgentState;
import java.util.*;

public class DeliveryControlState extends AgentState {
    public static final String PROJECT_ID="projectId", ACTION="action", CURRENT_NODE_ID="currentNodeId", MESSAGE="message";
    public DeliveryControlState(Map<String,Object> init){super(init);}
    public String projectId(){return this.<String>value(PROJECT_ID).orElse("");}
    public String action(){return this.<String>value(ACTION).orElse("IDLE");}
    public Optional<String> currentNodeId(){return this.value(CURRENT_NODE_ID);}
}
