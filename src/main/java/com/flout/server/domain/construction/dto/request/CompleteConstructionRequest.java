package com.flout.server.domain.construction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CompleteConstructionRequest(
    @NotNull(message = "시공 ID를 입력해주세요.") @Positive(message = "시공 ID는 양수여야 합니다.") Long constructionId,
    @Schema(description = "주민에게 공개할 최종 설치 결과", example = "출입구 물막이판 설치와 작동 확인을 완료했습니다.")
        @NotBlank(message = "최종 설치 결과를 입력해주세요.") @Size(max = 4000, message = "최종 설치 결과는 4000자 이하여야 합니다.") String finalInstallationResult) {}
