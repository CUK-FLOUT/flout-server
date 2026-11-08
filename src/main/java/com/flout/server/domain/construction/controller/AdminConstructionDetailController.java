package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.response.AdminConstructionDetailResponse;
import com.flout.server.domain.construction.service.AdminConstructionDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/constructions")
@Tag(name = "운영자-시공관리", description = "ADMIN 및 부천시 관할 시공 관리")
public class AdminConstructionDetailController {
  private final AdminConstructionDetailService service;

  @GetMapping(value = "/{constructionId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 상세 조회",
      description =
          "부천시 관할 운영자가 시공 대상의 지원·동의 정보, 주민 희망 일정, 확정 일정, 진행 상태와 업로드 사진을 조회합니다. 업로드 사진은 완료 전에도 운영자에게 제공하며 최종 설치 결과와 완료 시각은 전체 완료 후에만 반환합니다. resultPublished는 전체 완료와 동시에 주민 공개된 상태를 의미합니다. 조회로 데이터를 변경하지 않습니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 양의 Long 형식이 아닌 시공 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한·관할 없음, CONSTRUCTION_403_1: 타 관할 시공",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "CONSTRUCTION_404_1: 시공 건 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "500",
      description = "CONSTRUCTION_500_2: 사진 조회 URL 발급 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<AdminConstructionDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "시공 ID. 지원 신청 ID·현장점검 ID와 구분합니다.", example = "9") @PathVariable
          Long constructionId) {
    return SuccessResponse.ok(service.detail(Long.valueOf(jwt.getSubject()), constructionId));
  }
}
