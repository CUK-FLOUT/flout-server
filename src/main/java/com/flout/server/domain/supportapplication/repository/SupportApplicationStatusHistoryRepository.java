package com.flout.server.domain.supportapplication.repository;

import com.flout.server.domain.supportapplication.entity.SupportApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportApplicationStatusHistoryRepository
    extends JpaRepository<SupportApplicationStatusHistory, Long> {}
