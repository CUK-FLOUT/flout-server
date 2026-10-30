package com.flout.server.domain.inspection.dto.response;

import java.time.OffsetDateTime;

public record PublishInspectionResultResponse(
        Long inspectionId,
        String resultStatus,
        String inspectionStatus,
        String applicationStatus,
        OffsetDateTime publishedAt,
        String supportStatus
) {
}
