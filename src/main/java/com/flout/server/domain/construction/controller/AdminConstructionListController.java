package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.response.AdminConstructionListResponse;
import com.flout.server.domain.construction.service.AdminConstructionListService;
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
public class AdminConstructionListController {
  private final AdminConstructionListService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 대상 목록 조회·검색",
      description =
          "인증된 부천시 운영자의 시공 대상을 시공 ID 내림차순으로 조회합니다. 접수번호·신청자 이름 검색과 상태 필터를 조합할 수 있습니다. SCHEDULED 필터는 미배정(PENDING)과 일정 확정(SCHEDULED)을 포함해 화면의 예정 탭으로 사용합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: page·size 범위, 검색어 길이, 상태 값 또는 파라미터 타입 오류",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<AdminConstructionListResponse> search(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "접수번호·신청자 이름 검색, 최대 255자") @RequestParam(defaultValue = "")
          String keyword,
      @Parameter(description = "0부터 시작하는 페이지", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기 1~100", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(
              description = "시공 상태. 생략·빈 값은 전체, SCHEDULED는 PENDING과 SCHEDULED를 함께 조회",
              schema = @Schema(allowableValues = {"SCHEDULED", "IN_PROGRESS", "COMPLETED"}))
          @RequestParam(required = false)
          String status) {
    return SuccessResponse.ok(
        service.search(Long.valueOf(jwt.getSubject()), keyword, page, size, status));
  }
}
