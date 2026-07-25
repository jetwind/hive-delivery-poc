package com.hive.delivery.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.EventType;

@Entity @Table(name="delivery_event") @Getter @Setter @NoArgsConstructor
public class DeliveryEvent {
    @Id private UUID id;
    @Column(nullable=false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EventType eventType;
    private UUID nodeId;
    @Column(columnDefinition="text") private String payloadJson;
    @Column(nullable=false) private Instant createdAt;
    public static DeliveryEvent of(UUID projectId, EventType type, UUID nodeId, String payload) {
        var e=new DeliveryEvent(); e.id=UUID.randomUUID(); e.projectId=projectId; e.eventType=type;
        e.nodeId=nodeId; e.payloadJson=payload; e.createdAt=Instant.now(); return e;
    }
}
