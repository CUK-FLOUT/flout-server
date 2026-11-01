package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest;
import com.flout.server.domain.inspection.dto.request.SaveInspectionResultDraftRequest;
import com.flout.server.domain.inspection.dto.response.PublishInspectionResultResponse;
import com.flout.server.domain.inspection.dto.response.SaveInspectionResultDraftResponse;
import com.flout.server.domain.inspection.service.PublishInspectionResultService;
import com.flout.server.domain.inspection.service.SaveInspectionResultDraftService;
import com.flout.server.global.response.SuccessResponse;
import com.flout.server.global.response.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역의 현장점검 관리")
public class AdminInspectionResultController {

  private final PublishInspectionResultService service;
  private final SaveInspectionResultDraftService draftService;

  @PutMapping("/{inspectionId}/result/draft")
  @Operation(
      summary = "점검 결과 임시저장",
      description =
          "선택 입력한 내용을 비공개 DRAFT로 저장합니다. PUT 전체 교체 방식으로 생략·null 필드는 비우고 목록은 교체합니다. "
              + "최초 저장은 초안을 생성하고 재저장은 같은 초안을 갱신합니다. 기존 주민 공개본, 점검·신청 상태와 이력은 변경하지 않습니다. "
              + "모든 점검 상태에서 관할 운영자가 저장할 수 있으며 내부 메모는 주민에게 공개하지 않습니다.")
  public SuccessResponse<SaveInspectionResultDraftResponse> saveDraft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody SaveInspectionResultDraftRequest request) {
    return SuccessResponse.of(
        draftService.save(Long.valueOf(jwt.getSubject()), inspectionId, request),
        GeneralSuccessCode.SUCCESS_OK);
  }

  @PostMapping("/{inspectionId}/result")
  @Operation(
      summary = "점검 결과 저장 및 주민 공개",
      description =
          "점검 결과를 최종 저장하고 주민에게 공개합니다. "
              + "점검 상태가 IN_PROGRESS일 때만 공개할 수 있으며, "
              + "공개 시 점검 상태를 COMPLETED, 신청 상태를 INSPECTION_COMPLETED로 변경합니다.")
  public SuccessResponse<PublishInspectionResultResponse> publish(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody PublishInspectionResultRequest request) {
    return SuccessResponse.of(
        service.publish(Long.valueOf(jwt.getSubject()), inspectionId, request),
        GeneralSuccessCode.SUCCESS_OK);
  }
}
