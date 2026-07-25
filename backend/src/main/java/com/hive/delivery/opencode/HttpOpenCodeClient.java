package com.hive.delivery.opencode;

import com.fasterxml.jackson.databind.*;
import com.hive.delivery.config.OpenCodeProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class HttpOpenCodeClient implements OpenCodeClient {
    private final RestClient http; private final ObjectMapper json;
    public HttpOpenCodeClient(OpenCodeProperties p,ObjectMapper json){
        var b=RestClient.builder().baseUrl(p.baseUrl());
        if(p.password()!=null&&!p.password().isBlank()){
            String auth=Base64.getEncoder().encodeToString((p.username()+":"+p.password()).getBytes(StandardCharsets.UTF_8));
            b.defaultHeader(HttpHeaders.AUTHORIZATION,"Basic "+auth);
        }
        this.http=b.build();this.json=json;
    }
    private JsonNode node(String body){try{return body==null?json.nullNode():json.readTree(body);}catch(Exception e){throw new IllegalStateException(e);}}
    public JsonNode health(){return node(http.get().uri("/global/health").retrieve().body(String.class));}
    public JsonNode createSession(String title,String parentId){Map<String,Object> body=new LinkedHashMap<>();body.put("title",title);if(parentId!=null)body.put("parentID",parentId);return node(http.post().uri("/session").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));}
    public void promptAsync(String id,String agent,String prompt){
        var body=Map.of("agent",agent,"parts",List.of(Map.of("type","text","text",prompt)));
        http.post().uri("/session/{id}/prompt_async",id).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }
    public JsonNode statuses(){return node(http.get().uri("/session/status").retrieve().body(String.class));}
    public JsonNode messages(String id){return node(http.get().uri("/session/{id}/message?limit=20",id).retrieve().body(String.class));}
    public JsonNode diff(String id){return node(http.get().uri("/session/{id}/diff",id).retrieve().body(String.class));}
    public boolean abort(String id){Boolean b=http.post().uri("/session/{id}/abort",id).retrieve().body(Boolean.class);return Boolean.TRUE.equals(b);}
}
