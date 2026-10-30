package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.repository.ApplicationInflowLocationRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionApplicationDetailService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionApplicationRepository applications;
  private final ApplicationInflowLocationRepository locations;

  // 운영자·관할 검증 후 신청 상세 읽기 전용 조회
  @Transactional(readOnly = true)
  public InspectionApplicationDetailResponse get(Long operatorId, Long applicationId) {
    // 잘못된 신청 ID 거부
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    // 현재 사용자 역할과 관할 확인
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    // 신청자·분석 결과 함께 조회
    var application =
        applications
            .findForAdminDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    // 신청 주소의 행정구역으로 관할 판별
    if (!BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find())
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    return InspectionApplicationDetailResponse.from(
        application, locations.findLocationsByApplicationId(applicationId));
  }
}
