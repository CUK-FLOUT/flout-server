package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueMapResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.AdminPublicIssueListService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/map/public-issues")
@Tag(name = "운영자-공공인프라", description = "ADMIN 및 부천시 관할 공공시설 문제 관리")
public class AdminPublicIssueMapController {
  private final AdminPublicIssueListService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지역·공공 문제 지도 조회",
      description =
          "인증된 부천시 운영자의 지역·공공 문제 위치를 문제 ID 내림차순으로 조회합니다. 목록과 동일한 지역·처리 상태·문제 유형·검색어 조건을 적용하며 데이터를 변경하지 않습니다.")
  public SuccessResponse<AdminPublicIssueMapResponse> markers(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "원미구·소사구·오정구. 생략·빈 값이면 관할 전체", example = "원미구")
          @RequestParam(defaultValue = "")
          String district,
      @Parameter(
              description =
                  "처리 상태. RECEIVED, UNDER_REVIEW, INSPECTION_REQUIRED, ACTION_IN_PROGRESS, COMPLETED. 생략·빈 값이면 전체")
          @RequestParam(required = false)
          String status,
      @Parameter(
              description =
                  "문제 유형. REPEATED_FLOODING, SEWAGE_BACKFLOW, POOR_ROAD_DRAINAGE, BLOCKED_STORM_DRAIN. 생략·빈 값이면 전체")
          @RequestParam(required = false)
          String issueType,
      @Parameter(description = "지역 주소·문제 상세·현장 확인 내용 부분 검색, 최대 255자")
          @RequestParam(defaultValue = "")
          String keyword) {
    return SuccessResponse.of(
        service.markers(Long.valueOf(jwt.getSubject()), district, status, issueType, keyword),
        PublicIssueSuccessCode.MAP_FOUND);
  }
}
