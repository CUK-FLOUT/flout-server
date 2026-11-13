package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.*;
import com.flout.server.domain.supportapplication.service.ResidentSupportDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/support-applications/{supportApplicationId}")
@Tag(name = "주민-지원신청")
@ApiResponse(
    responseCode = "400",
    description = "COMMON_400_3: 양의 Long 형식이 아닌 지원 신청 ID",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "403",
    description = "COMMON_403_1: 주민이 아니거나 타인의 지원 신청",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "404",
    description = "SUPPORT_404_1: 지원 신청 없음",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
public class ResidentSupportProgressController {
  private final ResidentSupportDetailService service;

  @GetMapping(value = "/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 시공 일정 조회",
      description =
          "RESIDENT 본인의 APPROVED 지원 신청을 조회합니다. 시공 일정이 확정되면 저장된 날짜와 시간을 반환하며, 미확정이면 둘 다 null입니다.")
  @ApiResponse(responseCode = "200", description = "시공 일정 조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "SUPPORT_DETAIL_409_1: APPROVED이 아닌 상태",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<ResidentSupportScheduleResponse> schedule(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 지원 신청 ID. 점검·시공 ID와 다릅니다.", example = "201", required = true)
          @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(
        service.schedule(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }

  @GetMapping(value = "/attachments", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 증빙서류 조회",
      description =
          "본인 신청의 제출된 증빙서류 메타데이터와 15분 유효 열람 URL을 반환합니다. 신청 상태에 관계없이 조회할 수 있으며 서류가 없으면 attachments는 빈 배열입니다. URL 발급 시 파일의 존재 여부는 확인하지 않으므로 실제 열람에 실패할 수 있습니다. URL은 외부에 공유하지 않아야 합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "증빙서류 조회 성공. 미제출 시 빈 배열",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "500",
      description = "SUPPORT_DETAIL_500_1: 파일 열람 URL 발급 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<ResidentSupportAttachmentsResponse> attachments(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 지원 신청 ID", example = "201", required = true) @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(
        service.attachments(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }
}
