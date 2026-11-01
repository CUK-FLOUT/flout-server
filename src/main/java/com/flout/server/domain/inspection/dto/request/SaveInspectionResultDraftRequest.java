package com.flout.server.domain.inspection.dto.request;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest.FollowUpType;
import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest.VulnerabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "초안 전체 교체. 생략·null인 목록은 빈 목록으로 저장합니다.")
public record SaveInspectionResultDraftRequest(
    @Size(max = 4000) String summary,
    @Size(max = 20) List<@NotNull @Valid VulnerabilityRequest> vulnerabilities,
    @Size(max = 20) List<@NotNull @Valid RecommendedActionRequest> recommendedActions,
    FollowUpType followUpType,
    @Size(max = 4000) String internalMemo) {

  public record VulnerabilityRequest(
      VulnerabilityType type,
      @Size(max = 255) String title,
      @Size(max = 4000) String expertOpinion,
      @Size(max = 4000) String fieldFindings) {}

  public record RecommendedActionRequest(
      @Size(max = 255) String title,
      VulnerabilityType relatedTarget,
      @Size(max = 4000) String description) {}
}
