package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.SupportAttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ResidentSupportAttachmentsResponse(
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "실제로 제출된 증빙서류. 없으면 빈 배열") List<Attachment> attachments) {
  public record Attachment(
      @Schema(description = "첨부서류 ID", example = "1") Long attachmentId,
      @Schema(description = "첨부서류 유형", example = "LANDLORD_CONSENT")
          SupportAttachmentType attachmentType,
      @Schema(description = "원본 파일명", example = "임대인동의서.pdf") String fileName,
      @Schema(description = "파일 크기(바이트)", example = "12345") long size,
      @Schema(description = "저장된 MIME 유형", example = "application/pdf") String contentType,
      OffsetDateTime submittedAt,
      @Schema(description = "15분 유효 비공개 파일 열람 URL. 외부 공유 금지. 발급 시 객체 존재 여부를 확인하지 않습니다.")
          String fileUrl) {}
}
