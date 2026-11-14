package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationDetailResponse;
import com.flout.server.domain.supportapplication.service.SupportApplicationDetailService;
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
@RequestMapping("/api/admin/support-applications")
@Tag(name = "운영자-지원관리", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminSupportDetailController {
  private final SupportApplicationDetailService service;

  @GetMapping(value = "/{supportApplicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 상세 조회",
      description =
          "관할 내 지원 신청의 신청 당시 정보와 공개된 점검 결과의 권장조치, 제출 서류를 조회합니다. 관할 밖이거나 존재하지 않는 신청은 동일한 SUPPORT_404_1을 반환합니다. 주민 요청사항은 현장점검 신청의 요청 사유이며, 설치 희망 방문일은 확정 시공일과 다릅니다. 첨부파일 열람 URL은 15분 동안 유효하며 외부에 공유하지 않아야 합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 양의 Long 형식이 아닌 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "SUPPORT_404_1: 조회 가능한 지원 신청 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "500",
      description = "SUPPORT_DETAIL_500_1: 첨부자료 조회 URL 발급 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<SupportApplicationDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "지원 신청 ID. 현장점검 ID와 다릅니다.", example = "201") @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(service.detail(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }
}
