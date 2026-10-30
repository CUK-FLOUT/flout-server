package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.ApplicationInflowLocation;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationInflowLocationRepository
    extends JpaRepository<ApplicationInflowLocation, Long> {
  /** 저장된 위치 순서로 반환하며 입력이 없으면 빈 목록을 반환한다. */
  @Query(
      "select l.inflowLocation from ApplicationInflowLocation l where l.application.id = :applicationId order by l.id")
  List<InflowLocation> findLocationsByApplicationId(@Param("applicationId") Long applicationId);
}
