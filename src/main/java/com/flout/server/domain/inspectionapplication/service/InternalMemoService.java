package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.InternalMemoResponse;
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
public class InternalMemoService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionApplicationRepository applications;

  // 관할 확인 후 내부 메모 저장
  @Transactional
  public InternalMemoResponse save(Long operatorId, Long applicationId, String requestedMemo) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    var application =
        applications
            .findById(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    // 부천시 행정구역 일치 여부 확인
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find()) {
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_ACCESS_DENIED);
    }
    String memo = requestedMemo.strip();
    if (memo.isBlank()) memo = null;
    if (applications.updateInternalMemo(applicationId, memo) != 1) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND);
    }
    return new InternalMemoResponse(applicationId, memo);
  }
}
