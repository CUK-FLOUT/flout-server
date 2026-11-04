package com.flout.server.domain.inspection.dto.response;

import java.time.OffsetDateTime;

public record SaveInspectionResultDraftResponse(
    Long inspectionId, String resultStatus, OffsetDateTime savedAt) {}
