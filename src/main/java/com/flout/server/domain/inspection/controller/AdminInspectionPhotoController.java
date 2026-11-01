package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.response.InspectionPhotoUploadResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionPhotoService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리")
public class AdminInspectionPhotoController {
  private final InspectionPhotoService service;

  @PostMapping(
      value = "/{inspectionId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 사진 업로드",
      description =
          "관할 점검의 DRAFT에 사진을 추가합니다. JPG/JPEG/PNG, 파일당 최대 10MiB, 요청 전체 제한은 80MiB이고 실제 이미지 형식을 검증합니다. 기존 초안 내용·사진과 주민 공개본, 점검·신청 상태 및 이력은 유지합니다. 응답 photos는 이번 업로드 목록이며 URL은 15분 유효한 비공개 S3 서명 URL입니다.")
  public ResponseEntity<SuccessResponse<InspectionPhotoUploadResponse>> upload(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "점검 ID (양의 정수)", example = "1") @PathVariable Long inspectionId,
      @Parameter(description = "JPG/JPEG/PNG 사진, 최소 1개, 파일당 최대 10MiB", required = true)
          @RequestPart(value = "files", required = false)
          List<MultipartFile> files) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.upload(Long.valueOf(jwt.getSubject()), inspectionId, files),
                InspectionSuccessCode.PHOTOS_UPLOADED));
  }
}
