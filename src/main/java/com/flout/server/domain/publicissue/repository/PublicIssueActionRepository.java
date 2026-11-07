package com.flout.server.domain.publicissue.repository;

import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicIssueActionRepository extends JpaRepository<PublicIssueAction, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from PublicIssueAction a join fetch a.review r join fetch r.localProblem where a.id = :id")
  Optional<PublicIssueAction> findForImageUpload(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from PublicIssueAction a join fetch a.operator join fetch a.review r join fetch r.localProblem where a.id = :id")
  Optional<PublicIssueAction> findForStatusChange(@Param("id") Long id);
}
