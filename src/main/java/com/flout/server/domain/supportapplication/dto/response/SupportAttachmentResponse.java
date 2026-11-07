package com.flout.server.domain.supportapplication.dto.response;

import com.flout.server.domain.supportapplication.entity.*;
import java.time.*;

public record SupportAttachmentResponse(
    Long attachmentId,
    Long supportApplicationId,
    SupportAttachmentType attachmentType,
    String fileName,
    long size,
    String contentType,
    OffsetDateTime submittedAt,
    SupportApplicationStatus status) {
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
