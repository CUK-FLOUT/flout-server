package com.flout.server.domain.construction.repository;

import com.flout.server.domain.construction.entity.Construction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConstructionRepository extends JpaRepository<Construction, Long> {
  Optional<Construction> findBySupportApplicationId(Long supportApplicationId);

  @Query("select c.supportApplication.id from Construction c where c.id = :id")
  Optional<Long> findSupportApplicationId(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Construction c where c.id = :id")
  Optional<Construction> findForScheduleUpdate(@Param("id") Long id);
}
