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
          "관할 내 지원 신청 상세를 조회합니다. 다른 관할과 없는 신청은 동일하게 SUPPORT_404_1을 반환합니다. 신청 당시 이름·전체 연락처·주소·점유 형태를 사용합니다. 주민 요청사항은 연결된 현장점검 신청의 요청 사유이며 미입력 시 null입니다. 권장조치는 공개된 점검 결과에서 순서대로 조회하며 점검 완료와 공개 여부를 별도로 반환합니다. 설치 희망 방문일은 확정 시공일과 다릅니다. housingType은 연결된 침수 취약도 분석에 저장된 거주층이며 미입력은 null입니다. 첨부파일은 권한 확인 후 기존 저장소의 15분 유효 서명 URL을 발급하며 재공유하지 않아야 합니다. GET으로 데이터를 변경하지 않습니다.")
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
