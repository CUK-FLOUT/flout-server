package com.flout.server.domain.inspection.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties("inspectorId")
public record SaveInspectionAssignmentRequest(
    @NotNull(message = "확정 방문일을 입력해주세요.") @Schema(example = "2030-01-03") LocalDate visitDate,
    @NotNull(message = "방문 시간을 입력해주세요.") @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "14:00")
        LocalTime visitTime,
    @NotBlank(message = "주민 방문 전 안내를 입력해주세요.") @Size(max = 2000, message = "주민 방문 전 안내는 2000자 이하여야 합니다.") @Schema(
            description = "필수, 공백만 입력 불가, 최대 2000자",
            example = "방문 전 배수구 주변을 정리해주세요.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String preVisitNotice) {
  @Override
  public String toString() {
    return "SaveInspectionAssignmentRequest[redacted]";
  }
}
