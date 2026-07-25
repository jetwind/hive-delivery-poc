package com.hive.delivery.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface OpenCodeClient {
    JsonNode health();
    JsonNode createSession(String title,String parentId);
    void promptAsync(String sessionId,String agent,String prompt);
    JsonNode statuses();
    JsonNode messages(String sessionId);
    JsonNode diff(String sessionId);
    boolean abort(String sessionId);
}
