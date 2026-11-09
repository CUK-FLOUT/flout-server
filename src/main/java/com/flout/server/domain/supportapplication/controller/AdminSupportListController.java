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
          "인증된 운영자의 관할 내 지원 신청을 검색합니다. DRAFT는 제외하며 신청 ID 내림차순으로 정렬합니다. 지역(district)·지원 상태(status)·검색어를 조합할 수 있으며 건수와 페이지에도 동일하게 적용합니다. REVIEWING 필터는 SUBMITTED를 포함합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: page·size 범위, 검색어 길이, 필터 값 또는 파라미터 타입 오류",
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
          int size,
      @Parameter(
              description = "지역. 생략·빈 값은 전체",
              schema = @Schema(allowableValues = {"원미구", "소사구", "오정구"}))
          @RequestParam(required = false)
          String district,
      @Parameter(
              description = "지원 상태. REVIEWING은 접수(SUBMITTED)와 검토 중(REVIEWING)을 함께 조회",
              schema =
                  @Schema(
                      allowableValues = {
                        "SUBMITTED",
                        "REVIEWING",
                        "SCHEDULING",
                        "APPROVED",
                        "REJECTED"
                      }))
          @RequestParam(required = false)
          String status) {
    return SuccessResponse.ok(
        service.search(Long.valueOf(jwt.getSubject()), keyword, page, size, district, status));
  }
}
