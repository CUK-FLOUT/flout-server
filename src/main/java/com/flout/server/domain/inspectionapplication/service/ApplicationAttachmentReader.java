package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationAttachmentReader {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionApplicationRepository applications;
  private final ApplicationAttachmentRepository attachments;

  // 읽기 전용 트랜잭션에서 권한 검증 및 사진 값 추출
  @Transactional(readOnly = true)
  public List<AttachmentSnapshot> get(Long operatorId, Long applicationId) {
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    var application =
        applications
            .findById(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (!BUCHEON_ADDRESS.matcher(application.getAddress().strip()).find())
      throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    // DB 저장 시 발급된 첨부 ID를 등록 순서로 사용
    return attachments.findAllByApplicationIdOrderByIdAsc(applicationId).stream()
        .map(
            photo ->
                new AttachmentSnapshot(
                    photo.getId(), photo.getOriginalFilename(), photo.getObjectKey()))
        .toList();
  }

  // 트랜잭션 밖에서 사용할 사진 값 보관
  public record AttachmentSnapshot(Long attachmentId, String originalFilename, String objectKey) {}
}
