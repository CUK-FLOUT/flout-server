package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationStatusResponse;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApplicationStatusService {
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository applications;

  @Transactional(readOnly = true)
  public SupportApplicationStatusResponse getStatus(Long operatorId) {
    access.requireOperator(operatorId);
    var counts =
        applications.countStatusByJurisdiction(SupportDecisionAccess.BUCHEON_ADDRESS.pattern());
    return new SupportApplicationStatusResponse(
        counts.getTotalCount(),
        counts.getReviewingCount(),
        counts.getConsentRequiredCount(),
        counts.getApprovedCount());
  }
}
