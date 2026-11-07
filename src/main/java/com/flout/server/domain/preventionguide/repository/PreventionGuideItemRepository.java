package com.flout.server.domain.preventionguide.repository;

import com.flout.server.domain.preventionguide.entity.PreventionGuideItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreventionGuideItemRepository extends JpaRepository<PreventionGuideItem, Long> {
  List<PreventionGuideItem> findByGuideIdOrderByItemOrderAsc(Long guideId);

  @Query("select i.guide.id from PreventionGuideItem i where i.id = :id")
  Optional<Long> findGuideIdByItemId(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PreventionGuideItem i where i.id = :id")
  Optional<PreventionGuideItem> findLockedById(@Param("id") Long id);
}
