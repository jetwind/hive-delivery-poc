package com.hive.delivery.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.*;

@Entity @Table(name="delivery_node") @Getter @Setter @NoArgsConstructor
public class DeliveryNode {
    @Id private UUID id;
    @Column(nullable=false) private UUID projectId;
    private String templateNodeId;
    @Column(nullable=false) private String stageCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private NodeType nodeType;
    @Column(nullable=false) private String title;
    @Column(columnDefinition="text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private NodeStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ExecutorType executorType;
    private String handler;
    private String agentRole;
    @Column(columnDefinition="text") private String acceptanceCriteriaJson;
    private UUID parentNodeId;
    @Column(nullable=false) private int createdRevision;
    private UUID supersededBy;
    @Column(nullable=false) private int sortOrder;
}
