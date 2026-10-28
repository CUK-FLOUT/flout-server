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

  /** 현재 운영자와 신청 주소의 관할을 확인한 뒤 메모 컬럼만 갱신한다. */
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
    // 현재 등록된 관할은 부천시다. 이름의 부분 일치나 불명확한 주소는 허용하지 않는다.
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
