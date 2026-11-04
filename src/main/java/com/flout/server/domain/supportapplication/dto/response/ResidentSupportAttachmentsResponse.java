package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportAttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record ResidentSupportAttachmentsResponse(
    Long supportApplicationId,
    @Schema(allowableValues = {"PENDING", "VERIFIED", "NOT_REQUIRED"}) String landlordConsentStatus,
    @Schema(description = "실제로 제출된 증빙서류. 없으면 빈 배열") List<Attachment> attachments) {
  public record Attachment(
      Long attachmentId,
      SupportAttachmentType attachmentType,
      String fileName,
      @Schema(description = "파일 크기, bytes") long size,
      @Schema(description = "저장된 MIME 유형. PDF·이미지 뷰어 선택에 사용") String contentType,
      LocalDateTime submittedAt,
      @Schema(description = "15분 유효 비공개 파일 열람 URL. 외부 공유 금지. 발급 시 객체 존재 여부를 확인하지 않습니다.")
          String fileUrl) {}
}
