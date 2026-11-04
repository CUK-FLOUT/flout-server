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
          "인증된 운영자의 관할 내 지원 신청을 접수번호·지원 신청에 저장된 신청자 이름으로 부분 검색합니다. 검색어는 255자 검증 후 앞뒤 공백을 제거하며 %와 _는 문자 그대로 검색합니다. 지원 신청 ID 내림차순으로 정렬하고 검색·관할 조건을 적용한 전체 건수와 페이지 정보를 반환합니다. 접수번호는 기존 지원 신청 응답과 동일하게 현장점검 신청 ID를 최소 6자리로 표시합니다. DB 동의 확인 상태 CONFIRMED는 VERIFIED로, 기존 SUBMITTED는 REVIEWING으로 응답에만 변환합니다. 지역·지원 상태·동의 상태 필터는 이번 API에 포함하지 않습니다.")
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
