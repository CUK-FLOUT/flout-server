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
@Tag(name = "주민-지원신청", description = "주민의 침수 예방시설 지원 신청")
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
@ApiResponse(
    responseCode = "409",
    description = "SUPPORT_DETAIL_409_1: 해당 화면에서 조회할 수 없는 지원 상태",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
public class ResidentSupportDetailController {
  private final ResidentSupportDetailService service;

  @ApiResponse(
      responseCode = "200",
      description = "주민 본인 지원 신청 상세 조회 성공",
      useReturnTypeSchema = true)
  @GetMapping(value = "/review", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 검토 상세 조회",
      description =
          "본인의 SUBMITTED 지원 신청을 조회합니다. 다른 상태는 409를 반환합니다. 신청일과 설치 희망 일정은 저장값을 사용하며 확정 시공 일정이 아닙니다. 임대인 동의서 제출 여부는 첨부서류 조회에서 확인합니다.")
  public SuccessResponse<ResidentSupportReviewResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 지원 신청 ID. 점검 ID와 다릅니다.", example = "201", required = true)
          @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(service.review(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }

  @ApiResponse(
      responseCode = "200",
      description = "주민 본인 지원 신청 상세 조회 성공",
      useReturnTypeSchema = true)
  @GetMapping(value = "/approval", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 승인 상세 조회",
      description =
          "RESIDENT 본인의 APPROVED 신청만 조회합니다. 승인 시각과 지원 범위는 DB 저장값을 사용하며, 신청 시설(requestedFacility)과 확정된 지원 범위(supportScope)는 구분합니다. scheduledConstructionDate/Time은 시공 일정 저장 API로 확정한 일정이며 미배정이면 null입니다. 희망 일정으로 대체하거나 시공 진행 상태를 추정하지 않습니다. 내부 관리 정보는 반환하지 않습니다.")
  public SuccessResponse<ResidentSupportApprovalResponse> approval(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 지원 신청 ID", example = "201", required = true) @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(
        service.approval(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }

  @ApiResponse(
      responseCode = "200",
      description = "주민 본인 지원 신청 상세 조회 성공",
      useReturnTypeSchema = true)
  @GetMapping(value = "/rejection", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 미승인 상세 조회",
      description =
          "RESIDENT 본인의 REJECTED 신청만 조회합니다. 실제 저장된 미승인 사유와 처리 시각을 반환하며 임의의 사유를 생성하지 않습니다. 현장점검 결과 다시 보기용 inspectionId와 실제 PUBLISHED 결과 존재 여부를 반환합니다. 결과 내용과 파일은 이 API에 포함하지 않으며 점검 완료만으로 공개 여부를 판단하지 않습니다.")
  public SuccessResponse<ResidentSupportRejectionResponse> rejection(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 지원 신청 ID", example = "201", required = true) @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(
        service.rejection(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }
}
