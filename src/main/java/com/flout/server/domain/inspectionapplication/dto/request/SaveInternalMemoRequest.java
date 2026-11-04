package com.flout.server.domain.inspectionapplication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveInternalMemoRequest(
    @NotNull(message = "내부 메모는 필수입니다. 삭제하려면 빈 문자열을 입력해주세요.") @Size(max = 2000, message = "내부 메모는 2000자 이하여야 합니다.") @Schema(
            description = "최대 2000자. 앞뒤 공백을 제거하며 빈 문자열 또는 공백만 입력하면 메모 삭제",
            example = "방문 전 연락하여 출입 가능 여부를 확인해주세요.")
        String internalMemo) {
  @Override
  public String toString() {
    return "SaveInternalMemoRequest[REDACTED]";
  }
}
