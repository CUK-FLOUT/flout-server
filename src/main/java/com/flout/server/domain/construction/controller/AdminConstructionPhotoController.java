package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.response.ConstructionPhotoUploadResponse;
import com.flout.server.domain.construction.response.code.ConstructionSuccessCode;
import com.flout.server.domain.construction.service.ConstructionPhotoService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/constructions")
@Tag(name = "운영자-시공관리")
public class AdminConstructionPhotoController {
  private final ConstructionPhotoService service;

  @PostMapping(
      value = "/{constructionId}/photos",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 사진 업로드",
      description =
          "부천시 관할 ADMIN이 지원 승인된 시공 건에 사진을 추가합니다. files는 최소 1장 필수이며 JPG/JPEG/PNG, 파일당 최대 10MiB, 요청 전체 최대 80MiB입니다. 확장자뿐 아니라 실제 이미지 내용을 검증합니다. 기존 사진과 시공·지원 상태는 유지하며 전체 완료된 시공은 업로드할 수 없습니다. 응답은 이번 요청으로 추가한 사진만 포함하고 URL은 15분 유효한 비공개 조회 URL입니다.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_2: 시공 사진 업로드 성공",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "COMMON_400_3: files 누락 또는 잘못된 ID, CONSTRUCTION_400_2: 빈 사진 목록/빈 파일, CONSTRUCTION_400_3: 사진 형식·내용·파일명 오류",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한·관할 없음, CONSTRUCTION_403_1: 다른 관할 신청",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "CONSTRUCTION_404_1: 시공 건 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "409",
      description = "CONSTRUCTION_409_1: 지원 미승인, CONSTRUCTION_409_3: 전체 완료된 시공",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "413",
      description = "COMMON_413_1: 파일 또는 요청 용량 초과",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "500",
      description = "CONSTRUCTION_500_1: 업로드·조회 URL 발급·사진 저장 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<ConstructionPhotoUploadResponse>> upload(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "시공 ID. 지원 신청 ID·점검 ID와 구분합니다.") @PathVariable Long constructionId,
      @Parameter(
              description = "최소 1장 필수. JPG/JPEG/PNG, 파일당 최대 10MiB",
              required = true,
              array =
                  @ArraySchema(minItems = 1, schema = @Schema(type = "string", format = "binary")))
          @RequestPart("files")
          List<MultipartFile> files) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.upload(Long.valueOf(jwt.getSubject()), constructionId, files),
                ConstructionSuccessCode.PHOTOS_UPLOADED));
  }
}
