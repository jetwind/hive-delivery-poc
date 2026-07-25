package com.hive.delivery.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.EdgeType;

@Entity @Table(name="delivery_edge") @Getter @Setter @NoArgsConstructor
public class DeliveryEdge {
    @Id private UUID id;
    @Column(nullable=false) private UUID projectId;
    @Column(nullable=false) private UUID fromNodeId;
    @Column(nullable=false) private UUID toNodeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EdgeType edgeType;
    @Column(nullable=false) private int createdRevision;
}
