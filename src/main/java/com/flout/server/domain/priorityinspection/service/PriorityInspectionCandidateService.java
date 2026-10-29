package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionCandidatesResponse;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionCandidateRepository;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityInspectionCandidateService {
  private final PriorityInspectionAccess access;
  private final PriorityInspectionCandidateRepository candidates;

  @Transactional(readOnly = true)
  public PriorityInspectionCandidatesResponse search(
      Long operatorId,
      String keyword,
      String district,
      RiskLevel riskLevel,
      Boolean designated,
      int page,
      int size) {
    access.requireJurisdiction(operatorId);
    if (page < 0
        || size < 1
        || size > 100
        || keyword.length() > 255
        || !Set.of("", "원미구", "소사구", "오정구").contains(district)) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    return candidates.search(keyword.strip(), district, riskLevel, designated, page, size);
  }
}
