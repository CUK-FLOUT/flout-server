package com.flout.server.domain.inspection.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PublishInspectionResultRequest(
        @NotBlank String summary,
        @NotEmpty List<@NotNull @Valid VulnerabilityRequest> vulnerabilities,
        @NotEmpty List<@NotNull @Valid RecommendedActionRequest> recommendedActions,
        @NotNull FollowUpType followUpType,
        @NotBlank String internalMemo
) {

    public record VulnerabilityRequest(
            @NotNull VulnerabilityType type,
            @NotBlank @Size(max = 255) String title,
            @NotBlank String expertOpinion,
            String fieldFindings
    ) {}

    public record RecommendedActionRequest(
            @NotBlank @Size(max = 255) String title,
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
