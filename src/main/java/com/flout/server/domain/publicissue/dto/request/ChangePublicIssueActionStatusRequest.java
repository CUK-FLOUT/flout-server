package com.flout.server.domain.publicissue.dto.request;

import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangePublicIssueActionStatusRequest(
    @NotNull @Schema(
            description = "변경할 조치 상태. 등록 직후에는 IN_PROGRESS이며 완료 조건 충족 후 COMPLETED로 변경할 수 있습니다.",
            example = "COMPLETED")
        PublicIssueActionStatus status) {}
