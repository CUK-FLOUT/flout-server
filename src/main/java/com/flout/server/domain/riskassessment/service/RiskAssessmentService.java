package com.flout.server.domain.riskassessment.service;

import com.flout.server.domain.riskassessment.dto.request.CreateRiskAssessmentRequest;
import com.flout.server.domain.riskassessment.dto.response.CreateRiskAssessmentResponse;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskAssessmentService {
  private final UserRepository users;
  private final RiskAssessmentRepository assessments;

  /** 로그인한 주민의 주소에 임시 분석 결과를 저장한다. */
  @Transactional
  public CreateRiskAssessmentResponse create(Long userId, CreateRiskAssessmentRequest request) {
    var resident =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (resident.getRole() != UserRole.RESIDENT) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }

    var assessment =
        assessments.save(
            RiskAssessment.completedDemo(
                resident, request.address().strip(), request.detailAddress(), RiskLevel.HIGH));
    return new CreateRiskAssessmentResponse(
        assessment.getId(), assessment.getRiskLevel(), assessment.getStatus());
  }
}
