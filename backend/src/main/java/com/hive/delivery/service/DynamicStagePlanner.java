package com.hive.delivery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.opencode.OpenCodeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DynamicStagePlanner {
    private static final Logger log=LoggerFactory.getLogger(DynamicStagePlanner.class);
    private final OpenCodeClient client; private final ObjectMapper json;
    public DynamicStagePlanner(OpenCodeClient client, ObjectMapper json){this.client=client;this.json=json;}

    public record NodePlan(String name,String description,String type,Map<String,String> executor,
                           List<String> dependsOn,List<String> acceptanceCriteria){}

    public List<NodePlan> plan(String projectName,String workspace,String stageCode,String stageName,String objective){
        String prompt=buildPrompt(projectName,workspace,stageCode,stageName,objective);
        log.info("[OpenCode Plan] {} / {} | objective: {}",stageCode,stageName,objective);
        try{
            var s=client.createSession("Plan-"+stageCode+"-"+stageName,null);
            String sid=s.path("id").asText();
            if(sid.isBlank()) throw new IllegalStateException("OpenCode createSession returned no id");
            log.info("[OpenCode Plan] session={} created, sending prompt ({} chars)",sid,prompt.length());
            client.promptAsync(sid,"build",prompt);
            for(int i=0;i<6;i++){
                TimeUnit.SECONDS.sleep(5);
                var statuses=client.statuses();
                JsonNode st=statuses.path(sid);
                String type=st.path("type").asText();
                if("idle".equalsIgnoreCase(type)) break;
                if("error".equalsIgnoreCase(type)||"failed".equalsIgnoreCase(type)) throw new IllegalStateException("OpenCode session failed: "+st);
            }
            var messages=client.messages(sid);
            var result=parseResponse(messages);
            log.info("[OpenCode Plan] session={} done, parsed {} tasks",sid,result.size());
            return result;
        }catch(Exception e){
            log.warn("[OpenCode Plan] failed, using fallback for {}",stageName,e);
            return fallback(stageName);
        }
    }

    private String buildPrompt(String projectName,String workspace,String stageCode,String stageName,String objective){
        return """
You are the Hive Delivery Graph Planner. Analyze the project and generate a detailed task plan for this delivery stage.

**Project**: %s
**Workspace**: %s (read existing docs/code to understand current state)
**Stage**: %s - %s  
**Objective**: %s

Read the project workspace first to understand the current context. Then generate a task breakdown for this stage.
Return ONLY a JSON array of tasks. Each task has:
- name: short Chinese name
- description: what this task does
- type: "TASK" or "GATE" (GATE for human approval checkpoints)
- executor: { "type": "AGENT" or "CODE" or "HUMAN", "handler": "opencode.task" for AGENT, "artifact.baseline" for CODE, "human.approval" for HUMAN, "role": "delivery-agent" for AGENT, "approverRole": "tech-lead" for HUMAN GATE }
- dependsOn: list of task names this depends on (empty if none)
- acceptanceCriteria: list of verification criteria (1-3 items)

Example:
[
  {"name":"分析原始需求","description":"阅读并分析产品需求文档","type":"TASK","executor":{"type":"AGENT","handler":"opencode.task","role":"delivery-agent","approverRole":null},"dependsOn":[],"acceptanceCriteria":["需求边界明确","形成可验证验收标准"]},
  {"name":"需求确认","description":"业务负责人确认需求","type":"GATE","executor":{"type":"HUMAN","handler":"human.approval","role":null,"approverRole":"tech-lead"},"dependsOn":["分析原始需求"],"acceptanceCriteria":["业务确认通过"]}
]

Generate 2-4 tasks per stage. Include a GATE if human approval is needed. Output ONLY valid JSON, no markdown, no explanation.
""".formatted(projectName,workspace,stageCode,stageName,objective);
    }

    private List<NodePlan> parseResponse(JsonNode messages){
        if(!messages.isArray()||messages.isEmpty()) return List.of();
        String text="";
        for(var m:messages){
            if(!"assistant".equals(m.path("role").asText())) continue;
            for(var p:m.path("parts")){
                if("text".equals(p.path("type").asText())) text=p.path("text").asText();
            }
        }
        log.debug("[OpenCode Plan] raw AI response: {}",text.substring(0,Math.min(text.length(),500)));
        String jsonStr=extractJson(text);
        if(jsonStr.isEmpty()) return List.of();
        try{return json.readValue(jsonStr,new TypeReference<List<NodePlan>>(){});}
        catch(Exception e){log.warn("[OpenCode Plan] JSON parse failed: {}",e.getMessage());return List.of();}
    }

    private String extractJson(String text){
        int start=text.indexOf('['); int end=text.lastIndexOf(']');
        if(start>=0&&end>start) return text.substring(start,end+1);
        return "";
    }

    private List<NodePlan> fallback(String stageName){
        return List.of(
            new NodePlan("分析"+stageName,"分析当前阶段需求","TASK",Map.of("type","AGENT","handler","opencode.task","role","delivery-agent","approverRole",""),List.of(),List.of("分析完成")),
            new NodePlan(stageName+"确认",stageName+"确认","GATE",Map.of("type","HUMAN","handler","human.approval","role","","approverRole","tech-lead"),List.of("分析"+stageName),List.of("确认通过"))
        );
    }
}
