package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.InspectionApplicationDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
@Tag(name = "운영자-GIS 신청관리", description = "관할 지역 현장점검 신청 관리")
public class AdminApplicationDetailController {
  private final InspectionApplicationDetailService service;

  /** GIS 신청 정보 탭에 표시할 기본 정보와 주민 입력값을 반환한다. */
  @GetMapping(value = "/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 상세정보 조회",
      description =
          "운영자 본인 관할 신청의 접수번호, 신청자 이름·연락처, 전체 주소·상세주소, 거주 형태, AI 취약도, 침수 경험, 유입 위치, 기존 시설 여부, 주민 방문 희망일·시간대 및 요청 사유를 반환합니다. 반지하 표시는 화면 기본값으로 처리하며 응답에 별도 층수 필드는 없습니다. 희망일·시간대·요청 사유·상세주소가 없으면 null, 유입 위치가 없으면 빈 배열을 반환합니다. 확정 방문 일정과 구분하며 데이터를 변경하지 않습니다.")
  public SuccessResponse<InspectionApplicationDetailResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "신청 ID. 양의 정수이며 점검 ID와 구분", example = "1") @PathVariable
          Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.DETAIL_FOUND);
  }
}
