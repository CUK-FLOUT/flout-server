package com.flout.server.domain.priorityinspection.repository;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityInspectionDesignationRepository
    extends JpaRepository<PriorityInspectionDesignation, Long> {}
