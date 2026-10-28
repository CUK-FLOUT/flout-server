package com.flout.server.domain.priorityinspection.controller;

import com.flout.server.domain.priorityinspection.dto.request.UpdatePriorityInspectionDesignationRequest;
import com.flout.server.domain.priorityinspection.dto.request.UpdatePriorityInspectionOrderRequest;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionOrderResponse;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionSuccessCode;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionDesignationService;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionOrderService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
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

  @PutMapping(
      value = "/{applicationId}/designation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 대상 지정·취소",
      description =
          "본인 관할의 신청을 designated가 true이면 우선점검 대상으로 지정하고 false이면 취소합니다. 같은 상태로 재요청하면 기존 상태를 유지합니다. 취소 후 재지정하면 지정 운영자와 시각을 새로 저장합니다. 메모·신청·점검 상태·담당자·방문 일정은 변경하지 않습니다.")
  public SuccessResponse<PriorityInspectionDesignationResponse> designate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long applicationId,
      @Valid @RequestBody UpdatePriorityInspectionDesignationRequest request) {
    var response =
        service.designate(Long.valueOf(jwt.getSubject()), applicationId, request.designated());
    return SuccessResponse.of(
        response,
        response.designated()
            ? PriorityInspectionSuccessCode.DESIGNATED
            : PriorityInspectionSuccessCode.CANCELLED);
  }
}
