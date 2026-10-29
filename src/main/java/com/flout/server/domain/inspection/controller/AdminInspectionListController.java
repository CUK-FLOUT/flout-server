package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.InspectionStatusFilter;
import com.flout.server.domain.inspection.dto.request.SearchInspectionsRequest;
import com.flout.server.domain.inspection.dto.response.InspectionListResponse;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionListService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역 현장점검 관리")
public class AdminInspectionListController {
  private final InspectionListService service;

  // 목록 주소는 구 단위로만 표시
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 검색·목록 조회",
      description =
          "운영자 관할의 점검을 검색·필터링합니다. 검색은 원본 주소·접수번호 부분 일치이며 응답 주소는 구 단위만 반환합니다. 상태·지역·담당자·방문 희망일은 AND 조건입니다. visitDate는 날짜 하나를 받아 주민 방문 희망일과 정확히 일치하는 점검을 조회하며 생략하면 방문일 전체입니다. 우선점검 지정 항목 우선, 같은 지정 여부 안에서는 신청 ID·점검 ID 내림차순, 기본 10건입니다. 데이터를 변경하지 않습니다.")
  public SuccessResponse<InspectionListResponse> search(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description =
                  "상태: ALL=전체 / RECEIVED=배정 대기 / SCHEDULED=예정 / IN_PROGRESS=진행 / COMPLETED=완료")
          @RequestParam(defaultValue = "ALL")
          InspectionStatusFilter status,
      @Parameter(description = "원미구·소사구·오정구. 생략·빈 값이면 전체", example = "원미구")
          @RequestParam(defaultValue = "")
          String district,
      @Parameter(description = "특정 담당자의 사용자 ID. 생략하면 전체", example = "3")
          @RequestParam(required = false)
          Long inspectorUserId,
      @Parameter(description = "조회할 방문 희망일 하나, yyyy-MM-dd. 생략하면 방문일 전체", example = "2026-11-03")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate visitDate,
      @Parameter(description = "주소·접수번호 검색어, 최대 255자") @RequestParam(defaultValue = "")
          String keyword,
      @Parameter(description = "0부터 시작하는 페이지", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "페이지 크기 1~100", example = "10") @RequestParam(defaultValue = "10")
          int size) {
    // ALL은 조회 전용 옵션이다. 저장된 점검 상태로 변환하지 않고 null로 전달해 상태 조건을 생략한다.
    // 인증된 사용자 ID만 서비스에 전달하며 관할 정보는 요청 파라미터로 받지 않는다.
    return SuccessResponse.of(
        service.search(
            Long.valueOf(jwt.getSubject()),
            new SearchInspectionsRequest(
                keyword,
                district,
                status == InspectionStatusFilter.ALL
                    ? null
                    : InspectionStatus.valueOf(status.name()),
                inspectorUserId,
                visitDate,
                page,
                size)),
        InspectionSuccessCode.LIST_FOUND);
  }
}
