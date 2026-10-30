package com.flout.server.domain.inspection.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PublishInspectionResultRequest(
        @NotBlank String summary,
        @NotEmpty List<@Valid VulnerabilityRequest> vulnerabilities,
        @NotEmpty List<@Valid RecommendedActionRequest> recommendedActions,
        @NotNull FollowUpType followUpType,
        @NotBlank String internalMemo
) {

    public record VulnerabilityRequest(
            @NotNull VulnerabilityType type,
            @NotBlank String title,
            @NotBlank String expertOpinion,
            String fieldFindings
    ) {}

    public record RecommendedActionRequest(
            @NotBlank String title,
            @NotNull VulnerabilityType relatedTarget,
            @NotBlank String description
    ) {}

    public enum VulnerabilityType {
        ENTRANCE,
        WINDOW_OPENING,
        DRAIN,
        OTHER_STRUCTURE
    }

    public enum FollowUpType {
        INDIVIDUAL_HOUSE,
        PUBLIC_AREA
    }
}
