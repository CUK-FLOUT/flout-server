package com.flout.server.domain.priorityinspection.controller;

import com.flout.server.domain.priorityinspection.dto.request.UpdatePriorityInspectionDesignationsRequest;
import com.flout.server.domain.priorityinspection.dto.request.UpdatePriorityInspectionOrderRequest;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionCandidatesResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationsResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDetailResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionOrderResponse;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionSuccessCode;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionCandidateService;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionDesignationService;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionDetailService;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionOrderService;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
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
@RequestMapping("/api/admin/priority-inspections")
@Tag(name = "운영자-우선점검 관리", description = "관할 지역 신청의 우선점검 대상 관리")
public class AdminPriorityInspectionController {
  private final PriorityInspectionDesignationService service;
  private final PriorityInspectionOrderService orderService;
  private final PriorityInspectionCandidateService candidateService;
  private final PriorityInspectionDetailService detailService;

  // 선택한 신청의 상세 패널 조회
  @GetMapping(value = "/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 신청정보 조회",
      description =
          "운영자 본인 관할 신청의 접수번호·신청자 이름·주소·점검 필요도·AI 취약도·침수 경험·주민 요청 사유·인근 신청 건수·저장된 내부 메모를 조회합니다. 미지정 신청도 조회합니다. HIGH=우선점검 권장, CAUTION=점검 검토, LOW=일반 점검입니다. 인근 신청은 목록 필터와 무관한 동일 관할 내 본인 제외 1km 이하 신청 건수입니다. 데이터를 변경하지 않습니다.")
  public SuccessResponse<PriorityInspectionDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "양의 정수인 현장점검 신청 ID. 점검 ID와 구분", example = "5", required = true)
          @PathVariable
          Long applicationId) {
    return SuccessResponse.of(
        detailService.get(Long.valueOf(jwt.getSubject()), applicationId),
        PriorityInspectionSuccessCode.DETAIL_FOUND);
  }

  // 우선점검 후보 검색·목록 조회
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 후보 검색·목록 조회",
      description =
          "관할 신청을 접수번호·주소만 검색합니다. 연결된 점검이 완료(COMPLETED)된 신청은 목록과 전체 건수·페이지 수에서 제외합니다. 지역·취약도·지정 여부 필터는 함께 적용되며 생략하면 전체입니다. page는 0부터, size는 1~100이며 기본 10건입니다. 우선점검 지정 항목을 먼저 표시하고 같은 지정 여부 안에서는 신청 ID 내림차순이며 페이지 분할 전에 정렬합니다. 저장된 선정 순서는 변경하지 않습니다. 인근 신청은 검색·필터·페이지와 무관하게 같은 관할에서 본인을 제외한 직선거리 1km 이내의 전체 신청 건수입니다.")
  public SuccessResponse<PriorityInspectionCandidatesResponse> candidates(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "접수번호·주소 검색어, 최대 255자") @RequestParam(defaultValue = "")
          String keyword,
      @Parameter(description = "지역: 원미구·소사구·오정구. 생략 또는 빈 값이면 지역 전체", example = "원미구")
          @RequestParam(defaultValue = "")
          String district,
      @Parameter(description = "취약도: HIGH=높음, CAUTION=주의, LOW=낮음. 생략하면 전체", example = "HIGH")
          @RequestParam(required = false)
          RiskLevel riskLevel,
      @Parameter(description = "우선점검 지정 여부: true=지정, false=미지정. 생략하면 전체", example = "true")
          @RequestParam(required = false)
          Boolean designated,
      @Parameter(description = "0부터 시작하는 페이지", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기, 1~100", example = "10") @RequestParam(defaultValue = "10")
          int size) {
    return SuccessResponse.of(
        candidateService.search(
            Long.valueOf(jwt.getSubject()), keyword, district, riskLevel, designated, page, size),
        PriorityInspectionSuccessCode.CANDIDATES_FOUND);
  }

  // 복수 신청 일괄 지정·취소
  @PutMapping(
      value = "/designation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 대상 일괄 지정·취소",
      description =
          "applicationIds 전체를 검증한 후 한 트랜잭션으로 지정·취소합니다. 한 건이라도 실패하면 전체 롤백합니다. 신규 지정은 요청 순서대로 마지막에 추가하고 기존 지정 정보는 유지합니다. 취소 후 남은 상대 순서를 유지하며 순위를 정리합니다. 응답은 요청 ID 순서입니다. 메모·신청·점검 상태는 변경하지 않습니다.")
  public SuccessResponse<PriorityInspectionDesignationsResponse> designateAll(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdatePriorityInspectionDesignationsRequest request) {
    return SuccessResponse.of(
        service.designateAll(
            Long.valueOf(jwt.getSubject()), request.applicationIds(), request.designated()),
        request.designated()
            ? PriorityInspectionSuccessCode.DESIGNATED
            : PriorityInspectionSuccessCode.CANCELLED);
  }

  @PutMapping(
      value = "/order",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 선정 순서 저장",
      description =
          "관할 지역의 우선점검 대상 전체 신청 ID를 원하는 순서대로 전달합니다. 첫 항목부터 1순위로 저장합니다. 필터·페이지 밖의 대상도 포함해야 하며 누락되거나 지정 목록이 변경되면 409를 반환합니다. 동일 순서 재요청은 변경 시각을 유지합니다. 신청·점검·메모·최초 지정 정보는 변경하지 않습니다.")
  public SuccessResponse<PriorityInspectionOrderResponse> saveOrder(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdatePriorityInspectionOrderRequest request) {
    return SuccessResponse.of(
        orderService.save(Long.valueOf(jwt.getSubject()), request.applicationIds()),
        PriorityInspectionSuccessCode.ORDER_SAVED);
  }
}
