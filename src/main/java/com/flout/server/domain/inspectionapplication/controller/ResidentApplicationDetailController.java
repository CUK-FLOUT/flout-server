package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationSummaryResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ResidentApplicationDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "주민-내신청", description = "주민 본인의 신청 내역과 상세정보 조회")
public class ResidentApplicationDetailController {
  private final ResidentApplicationDetailService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "내 신청 내역 조회",
      description =
          "본인의 접수된 신청을 접수 시각과 신청 ID 내림차순으로 조회하며, 접수 시각이 없는 기존 건은 생성 시각을 사용합니다. DRAFT는 제외하고 페이지·필터 없이 전체 목록을 반환하며 내역이 없으면 빈 배열입니다. 진행 단계는 점검 결과 공개 여부와 지원·시공 상태를 함께 반영합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "APPLICATION_200_10: 내 신청 목록 조회 성공",
      useReturnTypeSchema = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                @ExampleObject(
                    name = "applications",
                    value =
                        """
            {"isSuccess":true,"status":200,"code":"APPLICATION_200_10","message":"내 신청 내역을 조회했습니다.","result":[{"applicationId":3,"receiptNumber":"000003","title":"시공 · 출입구 물막이판","address":"경기도 부천시 원미구 지봉로 43","stage":"CONSTRUCTION_IN_PROGRESS","closed":false,"createdAt":"2026-10-06T10:00:00+09:00","submittedAt":"2026-10-06T10:05:00+09:00","updatedAt":"2026-10-21T10:00:00+09:00","inspectionId":5,"supportApplicationId":7,"constructionId":9,"resultPublished":true}],"timeStamp":"2026-10-21 10:00:00"}
            """),
                @ExampleObject(
                    name = "empty",
                    value =
                        """
            {"isSuccess":true,"status":200,"code":"APPLICATION_200_10","message":"내 신청 내역을 조회했습니다.","result":[],"timeStamp":"2026-10-21 10:00:00"}
            """)
              }))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 주민 권한 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<List<ResidentApplicationSummaryResponse>> list(
      @AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.of(
        service.list(Long.valueOf(jwt.getSubject())), InspectionApplicationSuccessCode.LIST_FOUND);
  }

  @GetMapping("/{applicationId}")
  @Operation(
      summary = "내 신청 내역 상세 조회",
      description =
          "본인의 현장점검 신청 정보와 진행 단계, 공개된 점검·지원·시공 요약을 조회합니다. 지원 DRAFT와 점검 초안·내부 메모는 노출하지 않으며, 지원 범위·미승인 사유·최종 설치 결과는 해당 상태에서만 반환합니다. 임시 신청도 조회할 수 있으며 이때 progress.stage는 null입니다. progress.updatedAt과 확정 일정은 서울 시간 오프셋을 사용합니다.")
  public SuccessResponse<ResidentApplicationDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 현장점검 신청 ID", example = "1", required = true) @PathVariable
          Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.DETAIL_FOUND);
  }
}
