package com.flout.server.domain.publicissue.repository;

import com.flout.server.domain.publicissue.entity.PublicIssue;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicIssueRepository extends JpaRepository<PublicIssue, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PublicIssue i where i.id = :id")
  Optional<PublicIssue> findForAction(@Param("id") Long id);
}
