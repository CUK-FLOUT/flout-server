package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.dto.request.RejectSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.RejectSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatusHistory;
import com.flout.server.domain.supportapplication.repository.SupportApplicationStatusHistoryRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportRejectionService {
  private final SupportDecisionAccess access;
  private final SupportApplicationStatusHistoryRepository histories;

  @Transactional
  public RejectSupportApplicationResponse reject(
      Long operatorId, Long id, RejectSupportApplicationRequest request) {
    var decision = access.lockSubmitted(operatorId, id);
    var application = decision.application();
    application.reject(
        request.reason(), decision.operator(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    histories.saveAndFlush(SupportApplicationStatusHistory.rejected(application));
    return RejectSupportApplicationResponse.from(application);
  }
}
