package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.response.FacilityActionImageUploadResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.FacilityActionImageService;
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
@RequestMapping("/api/admin/public-issues")
@Tag(name = "운영자-공공인프라", description = "ADMIN 및 부천시 관할 공공시설 문제 관리")
public class AdminFacilityActionImageController {
  private final FacilityActionImageService service;

  @PostMapping(
      value = "/{publicIssueId}/actions/{actionId}/images",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "공공시설 조치 사진 업로드",
      description =
          "완료 처리 응답의 actionId를 사용해 JPG/JPEG/PNG 사진을 추가합니다. 파일당 최대 10MiB이며 응답 URL은 15분 유효한 비공개 S3 서명 URL입니다.")
  public ResponseEntity<SuccessResponse<FacilityActionImageUploadResponse>> upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @PathVariable Long actionId,
      @Parameter(description = "JPG/JPEG/PNG 사진, 최소 1개, 파일당 최대 10MiB", required = true)
          @RequestPart(value = "files", required = false)
          List<MultipartFile> files) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.upload(Long.valueOf(jwt.getSubject()), publicIssueId, actionId, files),
                PublicIssueSuccessCode.ACTION_IMAGES_UPLOADED));
  }
}
