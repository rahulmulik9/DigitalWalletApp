package com.rahul.monitoring_service.repository;

import com.rahul.monitoring_service.entity.MonitoringFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringFlagRepository extends JpaRepository<MonitoringFlag, Long> {
}