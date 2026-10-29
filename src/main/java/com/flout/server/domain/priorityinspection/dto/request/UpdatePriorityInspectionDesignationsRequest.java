package com.flout.server.domain.priorityinspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePriorityInspectionDesignationsRequest(
    @Schema(description = "지정·취소할 신청 ID 목록. 1~500건, 중복 불가. 신규 지정은 이 순서로 추가", example = "[5, 6, 7]")
        @NotEmpty @Size(
            max = UpdatePriorityInspectionDesignationsRequest.MAX_APPLICATIONS,
            message = "신청 ID는 한 번에 최대 {max}개까지 전달할 수 있습니다.")
        List<@NotNull @Positive Long> applicationIds,
    @Schema(description = "true: 일괄 지정, false: 일괄 취소", example = "true") @NotNull Boolean designated) {
  public static final int MAX_APPLICATIONS = 500;
}
