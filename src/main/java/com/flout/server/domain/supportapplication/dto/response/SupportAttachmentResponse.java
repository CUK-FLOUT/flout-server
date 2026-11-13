package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record SupportAttachmentResponse(
    @Schema(description = "첨부서류 ID", example = "1") Long attachmentId,
    @Schema(description = "지원 신청 ID", example = "201") Long supportApplicationId,
    @Schema(description = "첨부서류 유형", example = "LANDLORD_CONSENT")
        SupportAttachmentType attachmentType,
    @Schema(description = "원본 파일명", example = "임대인동의서.pdf") String fileName,
    @Schema(description = "파일 크기(바이트)", example = "12345") long size,
    @Schema(description = "파일 MIME 유형", example = "application/pdf") String contentType,
    OffsetDateTime submittedAt,
    @Schema(description = "접수된 지원 신청 상태", example = "SUBMITTED") SupportApplicationStatus status) {
  public static SupportAttachmentResponse from(SupportAttachment a) {
    return new SupportAttachmentResponse(
        a.getId(),
        a.getApplication().getId(),
        a.getAttachmentType(),
        a.getOriginalFilename(),
        a.getFileSize(),
        a.getContentType(),
        a.getSubmittedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime(),
        a.getApplication().getStatus());
  }
}
