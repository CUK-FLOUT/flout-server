package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueRequest;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueListResponse;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueStatusResponse;
import com.flout.server.domain.publicissue.dto.response.CreatePublicIssueResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.AdminPublicIssueListService;
import com.flout.server.domain.publicissue.service.PublicIssueService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/public-issues")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역의 현장점검 관리")
public class AdminPublicIssueController {
  private final PublicIssueService service;
  private final AdminPublicIssueListService listService;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지역·공공 문제 목록 조회·검색",
      description =
          "인증된 부천시 운영자의 지역·공공 문제를 문제 ID 내림차순으로 조회합니다. 지역·처리 상태·문제 유형·주소와 문제 내용 검색을 조합할 수 있습니다. 관련 신청은 문제 위치에서 직선거리 1km 이하인 부천시 접수 신청이며 임시저장은 제외합니다. 침수 이력은 관련 신청 중 침수 경험 YES 건수입니다.")
  public SuccessResponse<AdminPublicIssueListResponse> search(
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
          String keyword,
      @Parameter(description = "0부터 시작하는 페이지", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기 1~100", example = "10") @RequestParam(defaultValue = "10")
          int size) {
    return SuccessResponse.of(
        listService.search(
            Long.valueOf(jwt.getSubject()), district, status, issueType, keyword, page, size),
        PublicIssueSuccessCode.LIST_FOUND);
  }

  @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지역·공공 문제 현황 건수 조회",
      description =
          "인증된 부천시 운영자의 지역·공공 문제 현황을 조회합니다. 등록 전체, 검토 대기·검토 중·현장점검 필요, 조치 진행, 조치 완료 건수를 반환하며 지역·문제 유형·검색어 조건을 동일하게 적용합니다.")
  public SuccessResponse<AdminPublicIssueStatusResponse> status(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "원미구·소사구·오정구. 생략·빈 값이면 관할 전체", example = "원미구")
          @RequestParam(defaultValue = "")
          String district,
      @Parameter(
              description =
                  "문제 유형. REPEATED_FLOODING, SEWAGE_BACKFLOW, POOR_ROAD_DRAINAGE, BLOCKED_STORM_DRAIN. 생략·빈 값이면 전체")
          @RequestParam(required = false)
          String issueType,
      @Parameter(description = "지역 주소·문제 상세·현장 확인 내용 부분 검색, 최대 255자")
          @RequestParam(defaultValue = "")
          String keyword) {
    return SuccessResponse.of(
        listService.status(Long.valueOf(jwt.getSubject()), district, issueType, keyword),
        PublicIssueSuccessCode.STATUS_FOUND);
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지역·공공 문제 등록",
      description =
          "관할 원본 점검에 연결된 문제를 RECEIVED로 등록합니다. 주소는 선택이며 생략·null이면 원본 점검 신청 주소를 사용합니다. 나머지 필드는 필수이며 주소는 255자, 상세 내용·현장 확인 내용은 각각 4000자까지 허용합니다. 동일 주소는 원본 좌표를 재사용하고 다른 주소는 기존 좌표 서비스를 호출합니다. 같은 점검에서 여러 건 등록할 수 있습니다.")
  public SuccessResponse<CreatePublicIssueResponse> register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePublicIssueRequest request) {
    return SuccessResponse.of(
        service.register(Long.valueOf(jwt.getSubject()), request),
        PublicIssueSuccessCode.REGISTERED);
  }
}
