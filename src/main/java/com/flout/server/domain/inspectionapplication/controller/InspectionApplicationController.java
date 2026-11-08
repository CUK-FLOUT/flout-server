package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.request.UpdateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.dto.response.UpdateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.InspectionApplicationService;
import com.flout.server.domain.inspectionapplication.service.ResidentApplicationUpdateService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "주민-현장점검 신청", description = "현장점검 신청부터 진행 상황 및 결과 조회까지 관리")
public class InspectionApplicationController {
  private final InspectionApplicationService inspectionApplicationService;
  private final ResidentApplicationUpdateService updateService;

  public ResponseEntity<SuccessResponse<CreateInspectionApplicationResponse>> submit(
      Jwt jwt, CreateInspectionApplicationRequest request) {
    var response = inspectionApplicationService.submit(Long.valueOf(jwt.getSubject()), request);
    return ResponseEntity.status(201)
        .body(
            SuccessResponse.of(
                response, InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED));
  }

  @PostMapping
  @Operation(
      summary = "현장점검 신청",
      description =
          "완료된 본인 침수 위험도 분석 결과를 기반으로 신청 정보를 DRAFT로 저장합니다. 사진 업로드 성공 시 접수 및 점검이 생성됩니다. 동일한 임시 신청 요청은 기존 신청을 반환합니다.")
  public ResponseEntity<SuccessResponse<CreateInspectionApplicationResponse>> submit(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "신규 폼을 제출할 때 PUT에 사용한 draftId. 응답 유실 시 동일 ID와 동일 요청으로 재시도합니다.")
          @RequestHeader(value = "X-Form-Draft-Id", required = false)
          java.util.UUID draftId,
      @Valid @RequestBody CreateInspectionApplicationRequest request) {
    CreateInspectionApplicationResponse response =
        inspectionApplicationService.submit(Long.valueOf(jwt.getSubject()), request, draftId);
    return ResponseEntity.status(201)
        .body(
            SuccessResponse.of(
                response, InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED));
  }

  @PatchMapping("/{applicationId}")
  @Operation(
      summary = "임시 현장점검 신청 수정",
      description =
          "본인의 DRAFT 신청만 부분 수정합니다. 생략한 필드는 유지하고 요청 사유는 null로 삭제합니다. 희망 일정은 null일 수 없고 유입 위치는 1개 이상으로 전체 교체합니다.")
  public SuccessResponse<UpdateInspectionApplicationResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long applicationId,
      @Valid @RequestBody UpdateInspectionApplicationRequest request) {
    return SuccessResponse.of(
        updateService.update(Long.valueOf(jwt.getSubject()), applicationId, request),
        InspectionApplicationSuccessCode.DRAFT_UPDATED);
  }
}
