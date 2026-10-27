package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.ApplicationInflowLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationInflowLocationRepository
    extends JpaRepository<ApplicationInflowLocation, Long> {}
