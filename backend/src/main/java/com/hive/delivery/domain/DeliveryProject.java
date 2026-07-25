package com.hive.delivery.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.ProjectStatus;

@Entity @Table(name="delivery_project") @Getter @Setter @NoArgsConstructor
public class DeliveryProject {
    @Id private UUID id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String lifecycleTemplateCode;
    @Column(nullable=false) private String lifecycleTemplateVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ProjectStatus status;
    @Column(nullable=false) private int currentGraphRevision;
    @Column(nullable=false) private String workspacePath;
    private String plannerSessionId;
    @Column(columnDefinition="TEXT") private String requirement;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    public static DeliveryProject create(String name, String code, String version, String workspace) {
        var p=new DeliveryProject(); p.id=UUID.randomUUID(); p.name=name; p.lifecycleTemplateCode=code;
        p.lifecycleTemplateVersion=version; p.status=ProjectStatus.CREATED; p.currentGraphRevision=1;
        p.workspacePath=workspace; p.createdAt=Instant.now(); p.updatedAt=p.createdAt; return p;
    }
    @PreUpdate void touch(){ updatedAt=Instant.now(); }
}
