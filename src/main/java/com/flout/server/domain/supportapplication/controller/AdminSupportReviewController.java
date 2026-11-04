package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationReviewResponse;
import com.flout.server.domain.supportapplication.service.SupportApplicationReviewService;
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
@RequestMapping("/api/admin/support-applications")
@Tag(name = "운영자-지원관리", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminSupportReviewController {
  private final SupportApplicationReviewService service;

  @GetMapping(
      value = "/{supportApplicationId}/histories",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 자격 검토 정보 조회",
      description =
          "관할 내 지원 신청의 접수번호·신청자·주소 및 거주 정보, 공개된 점검 결과의 취약지점·권장조치, 신청 시설·희망 일정, 지원사업·첨부서류와 임대인 동의 필요 여부를 조회합니다. 주민 요청사항은 현장점검 신청의 요청 사유를 사용합니다. 연락처는 마스킹하며 첨부서류는 실제 제출된 자료만 반환합니다. 이 경로는 검토 화면 조회용이며 상태 변경 이력 목록이 아닙니다.")
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 양의 Long 형식이 아닌 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "SUPPORT_404_1: 관할 내 조회 가능한 지원 신청 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "500",
      description = "SUPPORT_DETAIL_500_1: 첨부자료 조회 URL 발급 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<SupportApplicationReviewResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "지원 신청 ID. 점검 ID와 다릅니다.", example = "201") @PathVariable
          Long supportApplicationId) {
    return SuccessResponse.ok(service.review(Long.valueOf(jwt.getSubject()), supportApplicationId));
  }
}
