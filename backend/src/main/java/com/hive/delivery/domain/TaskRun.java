package com.hive.delivery.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.RunStatus;

@Entity @Table(name="task_run") @Getter @Setter @NoArgsConstructor
public class TaskRun {
    @Id private UUID id;
    @Column(nullable=false) private UUID projectId;
    @Column(nullable=false) private UUID nodeId;
    @Column(nullable=false) private int attempt;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RunStatus status;
    @Column(nullable=false) private String executorType;
    private String externalSessionId;
    @Column(columnDefinition="text") private String summary;
    @Column(columnDefinition="text") private String changedFilesJson;
    @Column(columnDefinition="text") private String findingsJson;
    @Column(nullable=false) private Instant startedAt;
    private Instant finishedAt;
}
