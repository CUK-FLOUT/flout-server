package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.request.SaveConstructionScheduleRequest;
import com.flout.server.domain.construction.dto.response.ConstructionScheduleResponse;
import com.flout.server.domain.construction.response.code.ConstructionSuccessCode;
import com.flout.server.domain.construction.service.ConstructionScheduleService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/constructions")
@Tag(name = "운영자-시공관리", description = "지원 승인 건의 시공 관리")
public class AdminConstructionScheduleController {
  private final ConstructionScheduleService service;

  @PostMapping(
      value = "/{constructionId}/schedule",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 배정·일정 저장",
      description =
          "부천시 관할 ADMIN이 지원 승인된 시공 건의 확정 날짜와 시간을 저장합니다. constructionId는 지원 승인 응답에서 받은 시공 ID입니다. 로그인한 운영자를 담당자로 배정하고 SCHEDULED로 저장합니다. 미배정 PENDING 건에 최초 1회만 저장할 수 있으며 이미 담당자 또는 일정이 저장된 건은 409로 거절합니다. 한국 시간 기준 과거 일정도 거부합니다. 주민의 희망 일정과 점검 일정은 변경하지 않습니다.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "schedule",
                      value =
                          "{\"constructionDate\":\"2030-01-03\",\"constructionTime\":\"10:00\"}")))
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_1: 시공 일정 저장 성공",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_2: 날짜·시간 누락/형식 오류, COMMON_400_3: 잘못된 ID, CONSTRUCTION_400_1: 과거 일정",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한·관할 없음, CONSTRUCTION_403_1: 다른 관할 신청",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "CONSTRUCTION_404_1: 시공 건 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "409",
      description = "CONSTRUCTION_409_1: 지원 미승인, CONSTRUCTION_409_2: 이미 배정·일정 저장됨 또는 시공 시작/완료",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<ConstructionScheduleResponse> save(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "시공 ID. 지원 신청 ID·점검 ID와 구분합니다.") @PathVariable Long constructionId,
      @Valid @RequestBody SaveConstructionScheduleRequest request) {
    return SuccessResponse.of(
        service.save(Long.valueOf(jwt.getSubject()), constructionId, request),
        ConstructionSuccessCode.SCHEDULE_SAVED);
  }
}
