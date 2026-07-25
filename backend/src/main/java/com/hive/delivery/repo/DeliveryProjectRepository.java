package com.hive.delivery.repo;

import com.hive.delivery.domain.DeliveryProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.hive.delivery.domain.Enums;

public interface DeliveryProjectRepository extends JpaRepository<DeliveryProject, UUID> {
}
