package com.hive.delivery.web;

import java.time.Instant;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}
  public record CreateProjectRequest(String name,String lifecycleCode,String lifecycleVersion,String workspacePath) {}
  public record ChangeRequest(String description) {}
  public record ProjectView(UUID id,String name,String status,int revision,String workspacePath) {}
  public record NodeView(UUID id,String templateNodeId,String stageCode,String type,String title,String description,String status,
                         String executorType,String handler,String agentRole,List<String> acceptanceCriteria,UUID parentNodeId,int sortOrder) {}
  public record EdgeView(UUID id,UUID source,UUID target,String type) {}
  public record RunView(UUID id,UUID nodeId,int attempt,String status,String externalSessionId,String summary,Instant startedAt,Instant finishedAt) {}
  public record EventView(UUID id,String type,UUID nodeId,String payload,Instant createdAt) {}
  public record GraphSnapshot(ProjectView project,List<NodeView> nodes,List<EdgeView> edges,List<RunView> runs,List<EventView> events) {}
}
