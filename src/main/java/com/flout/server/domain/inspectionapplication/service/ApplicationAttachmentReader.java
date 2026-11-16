package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationAttachmentReader {
  private final AdminApplicationAccess access;
  private final ApplicationAttachmentRepository attachments;

  // 읽기 전용 트랜잭션에서 권한 검증 및 사진 값 추출
  @Transactional(readOnly = true)
  public List<AttachmentSnapshot> get(Long operatorId, Long applicationId) {
    access.read(operatorId, applicationId);
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
