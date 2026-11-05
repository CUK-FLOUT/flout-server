package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.response.InspectionPhotoDeleteResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionPhotoDeleteService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리")
public class AdminInspectionPhotoDeleteController {
  private final InspectionPhotoDeleteService service;

  @DeleteMapping(
      value = "/{inspectionId}/attachments/{photoId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 초안 사진 삭제",
      description =
          "부천시 관할 ADMIN이 해당 점검의 DRAFT 사진 한 장을 삭제합니다. photoId는 사진 업로드 또는 결과 조회 응답의 사진 ID입니다. 마지막 초안 사진도 삭제할 수 있지만 결과 공개에는 사진이 최소 1장 필요합니다. 공개된 사진과 주민 신청 첨부자료는 삭제할 수 없습니다. 저장소 정리 실패 시 자동 재시도하며 점검·신청 상태는 변경하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "INSPECTION_200_6: 사진 삭제 성공",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 잘못된 점검·사진 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한 없음, INSPECTION_403_1: 관할 오류",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "INSPECTION_404_1: 점검 없음, INSPECTION_404_2: 사진 없음·다른 점검 사진·이미 삭제됨",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "409",
      description = "INSPECTION_409_5: 공개된 사진 삭제 불가",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "500",
      description = "COMMON_500_1: DB 삭제 실패. 사진과 정리 요청은 함께 롤백",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<InspectionPhotoDeleteResponse>> delete(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "점검 ID. 양의 정수") @PathVariable Long inspectionId,
      @Parameter(description = "현장점검 결과 사진 ID. 양의 정수") @PathVariable Long photoId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.delete(Long.valueOf(jwt.getSubject()), inspectionId, photoId),
                InspectionSuccessCode.PHOTO_DELETED));
  }
}
