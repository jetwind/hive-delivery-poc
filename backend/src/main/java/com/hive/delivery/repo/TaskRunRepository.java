package com.hive.delivery.repo;

import com.hive.delivery.domain.TaskRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.hive.delivery.domain.Enums;

public interface TaskRunRepository extends JpaRepository<TaskRun, UUID> {
    List<TaskRun> findByProjectIdOrderByStartedAtDesc(UUID projectId);
    List<TaskRun> findByStatus(Enums.RunStatus status);
    long countByNodeId(UUID nodeId);
}
