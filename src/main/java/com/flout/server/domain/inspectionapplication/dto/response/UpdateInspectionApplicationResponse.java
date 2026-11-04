package com.flout.server.domain.inspectionapplication.dto.response;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;

public record UpdateInspectionApplicationResponse(
    Long applicationId, InspectionApplicationStatus status) {}
