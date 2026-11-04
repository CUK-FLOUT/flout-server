package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationListResponse;
import com.flout.server.domain.supportapplication.service.SupportApplicationListService;
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
public class AdminSupportListController {
  private final SupportApplicationListService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 목록 조회·검색",
      description =
          "인증된 운영자의 관할 내 지원 신청을 검색합니다. DRAFT는 제외하며 신청 ID 내림차순으로 정렬합니다. DB 동의 확인 상태 CONFIRMED는 호환성을 위해 VERIFIED로 표시합니다.")
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: page·size 범위, 검색어 길이 또는 파라미터 타입 오류",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<SupportApplicationListResponse> search(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "접수번호·신청자 이름 검색, 최대 255자") @RequestParam(defaultValue = "")
          String keyword,
      @Parameter(description = "0부터 시작하는 페이지", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기 1~100", example = "10") @RequestParam(defaultValue = "10")
          int size) {
    return SuccessResponse.ok(service.search(Long.valueOf(jwt.getSubject()), keyword, page, size));
  }
}
