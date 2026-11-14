package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.ValidateRecommendedFacilitiesRequest;
import com.flout.server.domain.inspection.dto.response.ValidateRecommendedFacilitiesResponse;
import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역의 현장점검 관리")
public class AdminRecommendedFacilityValidationController {
  private final RecommendedFacilityExtractor extractor;

  @PostMapping(
      value = "/recommended-facilities/validate",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "권장 예방조치 시설 추출 사전 검증",
      description =
          "관리자(ADMIN)가 입력한 권장 예방조치에서 설치 의도가 명확한 시설을 기존 규칙으로 추출해 중복 없이 반환합니다. 시설이 없으면 hasFacilities는 false이고 facilities는 빈 배열입니다. 점검 결과를 저장하거나 주민에게 공개하지 않으며 지원사업 신청 자격을 판단하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "COMMON_200: 호출에 성공했습니다.",
      useReturnTypeSchema = true,
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "시설 추출",
                    value =
                        """
                        {"isSuccess":true,"status":200,"code":"COMMON_200","message":"호출에 성공했습니다.","result":{"hasFacilities":true,"facilities":["출입구 물막이판"]},"timeStamp":"2026-11-14 10:00:00"}
                        """),
                @ExampleObject(
                    name = "시설 없음",
                    value =
                        """
                        {"isSuccess":true,"status":200,"code":"COMMON_200","message":"호출에 성공했습니다.","result":{"hasFacilities":false,"facilities":[]},"timeStamp":"2026-11-14 10:00:00"}
                        """)
              }))
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_2: 잘못된 JSON 또는 권장 예방조치 입력값 오류. 유효성 검사 메시지는 실패한 조건에 따라 달라집니다.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(ref = "#/components/schemas/CommonErrorResponse"),
              examples =
                  @ExampleObject(
                      name = "잘못된 JSON",
                      value =
                          """
                          {"isSuccess":false,"status":400,"code":"COMMON_400_2","message":"요청 본문의 형식이 잘못되었습니다.","result":null,"timeStamp":"2026-11-14 10:00:00"}
                          """)))
  @ApiResponse(
      responseCode = "406",
      description = "COMMON_406_1: 지원하지 않는 응답 형식",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(ref = "#/components/schemas/CommonErrorResponse"),
              examples =
                  @ExampleObject(
                      name = "지원하지 않는 응답 형식",
                      value =
                          """
                          {"isSuccess":false,"status":406,"code":"COMMON_406_1","message":"지원하지 않는 응답 형식입니다.","result":null,"timeStamp":"2026-11-14 10:00:00"}
                          """)))
  @ApiResponse(
      responseCode = "415",
      description = "COMMON_415_1: 지원하지 않는 요청 본문 형식",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(ref = "#/components/schemas/CommonErrorResponse"),
              examples =
                  @ExampleObject(
                      name = "지원하지 않는 요청 형식",
                      value =
                          """
                          {"isSuccess":false,"status":415,"code":"COMMON_415_1","message":"지원하지 않는 요청 본문 형식입니다.","result":null,"timeStamp":"2026-11-14 10:00:00"}
                          """)))
  public SuccessResponse<ValidateRecommendedFacilitiesResponse> validate(
      @Valid @RequestBody ValidateRecommendedFacilitiesRequest request) {
    var facilities = extractor.extract(request.descriptions());
    return SuccessResponse.ok(
        new ValidateRecommendedFacilitiesResponse(!facilities.isEmpty(), facilities));
  }
}
