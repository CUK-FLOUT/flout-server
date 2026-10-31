package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationAttachmentResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationAttachmentQueryService {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionApplicationRepository applications;
  private final ApplicationAttachmentRepository attachments;
  private final AttachmentStorage storage;

  // 운영자·신청 관할 검증 후 등록 순서 조회
  @Transactional(readOnly = true)
  public List<ApplicationAttachmentResponse> get(Long operatorId, Long applicationId) {
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
    var photos = attachments.findAllByApplicationIdOrderByIdAsc(applicationId);
    // 저장된 경로 대신 임시 이미지 URL 반환
    try {
      return photos.stream()
          .map(
              photo ->
                  new ApplicationAttachmentResponse(
                      photo.getId(),
                      photo.getOriginalFilename(),
                      storage.readUrl(photo.getObjectKey())))
          .toList();
    } catch (RuntimeException exception) {
      log.error("첨부사진 URL 발급 실패. applicationId={}", applicationId, exception);
      throw new BaseException(InspectionApplicationErrorCode.ATTACHMENT_READ_FAILED);
    }
  }
}
