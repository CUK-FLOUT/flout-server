package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.CompletePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CompletePublicIssueActionResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.FacilityActionImageService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
public class AdminPublicIssueActionController {

  private final FacilityActionImageService service;

  @PostMapping(
      value = "/{publicIssueId}/actions",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "공공시설 조치 사진 등록 및 완료 처리",
      description =
          "조치 내용(request)과 사진(files)을 함께 제출합니다. "
              + "사진은 최소 1장, JPG/JPEG/PNG 형식이며 파일당 최대 10MiB입니다. "
              + "사진 저장 및 DB 저장이 모두 성공해야 완료 처리됩니다.")
  public ResponseEntity<SuccessResponse<CompletePublicIssueActionResponse>> complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @Parameter(description = "조치 완료 내용 JSON", required = true) @Valid @RequestPart("request")
          CompletePublicIssueActionRequest request,
      @Parameter(description = "조치 완료 사진", required = true)
          @RequestPart(value = "files", required = false)
          List<MultipartFile> files) {

    var response = service.complete(Long.valueOf(jwt.getSubject()), publicIssueId, request, files);

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(SuccessResponse.of(response, PublicIssueSuccessCode.ACTION_COMPLETED));
  }
}
