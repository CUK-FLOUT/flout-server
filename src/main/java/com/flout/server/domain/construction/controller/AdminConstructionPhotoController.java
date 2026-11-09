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
          "부천시 관할 ADMIN이 지원 승인된 시공 건에 사진을 추가합니다. files는 최소 1장 필수이며 JPG/JPEG/PNG, 파일당 최대 10MiB, 요청 전체 최대 80MiB입니다. 실제 이미지 내용을 검증합니다. Idempotency-Key는 선택값이며 같은 운영자·시공 건·키의 재시도는 기존 사진 ID와 새 조회 URL을 반환합니다. 키는 대소문자를 구분하고 성공 기록은 자동 만료하지 않습니다. 파일 내용·이름·순서가 달라지면 409이며 처리 중인 요청도 409입니다. 실패 시 같은 키로 재시도할 수 있고 서버 중단으로 남은 선점은 15분 후 회수됩니다. 새 업로드에는 새 키를 사용합니다. 전체 완료 후 신규 업로드는 금지하지만 성공했던 요청의 재조회는 가능합니다. URL은 15분 유효합니다.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_2: 시공 사진 업로드 성공",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "COMMON_400_3: files 누락 또는 잘못된 ID, CONSTRUCTION_400_2: 빈 사진 목록/빈 파일, CONSTRUCTION_400_3: 사진 형식·내용·파일명 오류, CONSTRUCTION_400_5: 잘못된 Idempotency-Key",
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
      description =
          "CONSTRUCTION_409_1: 지원 미승인, CONSTRUCTION_409_3: 전체 완료된 시공, CONSTRUCTION_409_6: 같은 키의 요청 내용 불일치, CONSTRUCTION_409_7: 같은 키 처리 중",
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
              description = "선택값. 동일 업로드 재시도에는 같은 키, 새 업로드에는 새 키. 영문·숫자 및 . _ : - 사용, 1~128자",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String requestKey,
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
                service.upload(Long.valueOf(jwt.getSubject()), constructionId, files, requestKey),
                ConstructionSuccessCode.PHOTOS_UPLOADED));
  }
}
