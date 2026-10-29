package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDetailResponse;
import com.flout.server.domain.priorityinspection.repository.PriorityInspectionCandidateRepository;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriorityInspectionDetailService {
  private final PriorityInspectionAccess access;
  private final InspectionApplicationRepository applications;
  private final PriorityInspectionCandidateRepository candidates;

  // 운영자·관할·신청 ID 검증 후 지정 여부와 무관하게 상세정보 반환
  @Transactional(readOnly = true)
  public PriorityInspectionDetailResponse get(Long operatorId, Long applicationId) {
    access.requireJurisdiction(operatorId);
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var application =
        applications
            .findForPriorityDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (!access.includes(application.getAddress()))
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var count = candidates.nearbyCounts(List.of(applicationId)).getOrDefault(applicationId, 0L);
    return PriorityInspectionDetailResponse.from(application, count);
  }
}
