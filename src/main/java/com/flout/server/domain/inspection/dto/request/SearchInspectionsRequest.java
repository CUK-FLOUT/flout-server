package com.flout.server.domain.inspection.dto.request;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.LocalDate;

public record SearchInspectionsRequest(
    String keyword,
    String district,
    InspectionStatus status,
    Long inspectorUserId,
    LocalDate visitDate,
    int page,
    int size) {}
