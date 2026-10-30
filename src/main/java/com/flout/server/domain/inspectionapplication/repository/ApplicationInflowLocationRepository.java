package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.entity.ApplicationInflowLocation;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationInflowLocationRepository
    extends JpaRepository<ApplicationInflowLocation, Long> {
  // 유입 위치 저장 순서 조회, 미입력 시 빈 목록 반환
  @Query(
      "select l.inflowLocation from ApplicationInflowLocation l where l.application.id = :applicationId order by l.id")
  List<InflowLocation> findLocationsByApplicationId(@Param("applicationId") Long applicationId);
}
