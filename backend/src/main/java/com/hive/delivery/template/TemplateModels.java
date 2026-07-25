package com.hive.delivery.template;

import java.util.List;

public final class TemplateModels {
    private TemplateModels() {}
    public record Metadata(String code, String name, String version) {}
    public record LifecycleTemplate(String apiVersion, String kind, Metadata metadata, List<StageRef> stages) {}
    public record StageRef(String id, String code, String name, List<String> dependsOn, String template) {}
    public record StageTemplate(String apiVersion, String kind, Metadata metadata, Contract contract, List<NodeTemplate> nodes) {}
    public record Contract(String objective, List<String> requiredInputs, List<String> requiredOutputs) {}
    public record ExecutorTemplate(String type, String handler, String role, String approverRole) {}
    public record NodeTemplate(String id, String type, String name, String description, List<String> dependsOn,
                               ExecutorTemplate executor, List<String> acceptanceCriteria) {}
}
