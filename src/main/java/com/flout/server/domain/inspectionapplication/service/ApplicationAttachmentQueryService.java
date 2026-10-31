package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationAttachmentResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationAttachmentQueryService {
  private final ApplicationAttachmentReader reader;
  private final AttachmentStorage storage;

  // DB 조회 종료 후 트랜잭션 밖에서 URL 발급
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public List<ApplicationAttachmentResponse> get(Long operatorId, Long applicationId) {
    var photos = reader.get(operatorId, applicationId);
    // 저장된 경로 대신 임시 이미지 URL 반환
    try {
      return photos.stream()
          .map(
              photo ->
                  new ApplicationAttachmentResponse(
                      photo.attachmentId(),
                      photo.originalFilename(),
                      storage.readUrl(photo.objectKey())))
          .toList();
    } catch (RuntimeException exception) {
      log.error("첨부사진 URL 발급 실패. applicationId={}", applicationId, exception);
      throw new BaseException(InspectionApplicationErrorCode.ATTACHMENT_READ_FAILED);
    }
  }
}
