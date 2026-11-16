package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.InternalMemoResponse;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalMemoService {
  private final AdminApplicationAccess access;
  private final InspectionApplicationRepository applications;

  // 관할 확인 후 내부 메모 저장
  @Transactional
  public InternalMemoResponse save(Long operatorId, Long applicationId, String requestedMemo) {
    access.requireWritableApplication(operatorId, applicationId);
    String memo = requestedMemo.strip();
    if (memo.isBlank()) memo = null;
    if (applications.updateInternalMemo(applicationId, memo) != 1) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND);
    }
    return new InternalMemoResponse(applicationId, memo);
  }
}
