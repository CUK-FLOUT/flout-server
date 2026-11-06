package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.response.ResidentConstructionResultResponse;
import com.flout.server.domain.construction.dto.response.ResidentConstructionScheduleResponse;
import com.flout.server.domain.construction.response.code.ConstructionSuccessCode;
import com.flout.server.domain.construction.service.ResidentConstructionQueryService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/constructions")
@Tag(name = "주민-시공 신청", description = "주민 본인의 확정 시공 일정·진행·완료 결과 조회")
@ApiResponse(
    responseCode = "400",
    description = "COMMON_400_3: 양의 Long 형식이 아닌 시공 ID",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "401",
    description = "COMMON_401_1: 미인증, COMMON_401_3: 무효·만료 토큰",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "403",
    description = "COMMON_403_1: 주민 권한이 없거나 타인의 시공 건",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "404",
    description = "CONSTRUCTION_404_1: 시공 건 없음",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
@ApiResponse(
    responseCode = "409",
    description = "CONSTRUCTION_409_1: 연결된 지원 신청이 승인 상태가 아님",
    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
public class ResidentConstructionController {
  private final ResidentConstructionQueryService service;

  @GetMapping(value = "/{constructionId}/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 일정 조회",
      description =
          "RESIDENT 본인의 지원 승인된 시공 건만 조회합니다. 확정 날짜·시간과 신청 시설·승인 범위를 구분합니다. PENDING 상태의 미확정 일정은 null이며 희망 일정으로 대체하지 않습니다. 조회로 상태나 이력을 변경하지 않습니다. 방문 전 공통 준비사항과 일정 확인 버튼의 UI 상태는 저장하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_4: 시공 일정 조회 성공",
      useReturnTypeSchema = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                @ExampleObject(
                    name = "scheduled",
                    summary = "13 시공 일정 · 확정 일정",
                    value =
                        """
              {"isSuccess":true,"status":200,"code":"CONSTRUCTION_200_4","message":"시공 일정을 조회했습니다.","result":{"constructionId":9,"supportApplicationId":7,"applicationId":3,"receiptNo":"000003","applicantName":"박주민","address":"경기도 부천시 원미구 길주로 210","detailAddress":"101호","status":"SCHEDULED","requestedFacility":"출입구 물막이판","supportScope":"출입구 물막이판 설치","scheduledConstructionDate":"2026-10-21","scheduledConstructionTime":"10:00:00"},"timeStamp":"2026-10-21 09:00:00"}
              """),
                @ExampleObject(
                    name = "pending",
                    summary = "미배정 · 확정 일정 없음",
                    value =
                        """
              {"isSuccess":true,"status":200,"code":"CONSTRUCTION_200_4","message":"시공 일정을 조회했습니다.","result":{"constructionId":9,"supportApplicationId":7,"applicationId":3,"receiptNo":"000003","applicantName":"박주민","address":"경기도 부천시 원미구 길주로 210","detailAddress":null,"status":"PENDING","requestedFacility":"출입구 물막이판","supportScope":"출입구 물막이판 설치","scheduledConstructionDate":null,"scheduledConstructionTime":null},"timeStamp":"2026-10-21 09:00:00"}
              """)
              }))
  public SuccessResponse<ResidentConstructionScheduleResponse> schedule(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "시공 ID. 지원 신청 ID·현장점검 ID와 구분합니다.") @PathVariable
          Long constructionId) {
    return SuccessResponse.of(
        service.schedule(Long.valueOf(jwt.getSubject()), constructionId),
        ConstructionSuccessCode.SCHEDULE_READ);
  }

  @GetMapping(value = "/{constructionId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 결과 조회",
      description =
          "RESIDENT 본인의 승인된 시공 건에서 PENDING·SCHEDULED·IN_PROGRESS·COMPLETED 상태를 조회합니다. 완료 전에는 finalInstallationResult·completedAt이 null이고 beforePhotos·photos는 빈 배열입니다. 완료 시 운영자가 공개한 실제 결과와 시공 사진, 공개된 현장점검 사진을 구분해 제공합니다. beforePhotos는 동일 위치의 전후 비교 쌍을 보장하지 않습니다. workHistory는 실제 상태 변경 이력이며 현장 도착·설치 위치 확인 같은 미저장 작업일지를 생성하지 않습니다. 내부 메모·담당자 연락처·저장소 객체 키는 반환하지 않습니다. 사진 URL은 15분간 유효합니다. 조회로 시공 상태를 변경하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_5: 시공 진행·완료 결과 조회 성공",
      useReturnTypeSchema = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                @ExampleObject(
                    name = "inProgress",
                    summary = "14 시공 진행 · 완료 결과는 비공개",
                    value =
                        """
              {"isSuccess":true,"status":200,"code":"CONSTRUCTION_200_5","message":"시공 진행 및 결과를 조회했습니다.","result":{"constructionId":9,"supportApplicationId":7,"applicationId":3,"receiptNo":"000003","applicantName":"박주민","address":"경기도 부천시 원미구 길주로 210","detailAddress":"101호","status":"IN_PROGRESS","requestedFacility":"출입구 물막이판","supportScope":"출입구 물막이판 설치","scheduledConstructionDate":"2026-10-21","scheduledConstructionTime":"10:00:00","completedAt":null,"finalInstallationResult":null,"updatedAt":"2026-10-21T10:00:00+09:00","workHistory":[{"status":"SCHEDULED","changedAt":"2026-10-20T15:00:00+09:00"},{"status":"IN_PROGRESS","changedAt":"2026-10-21T10:00:00+09:00"}],"beforePhotos":[],"photos":[]},"timeStamp":"2026-10-21 10:32:00"}
              """),
                @ExampleObject(
                    name = "completed",
                    summary = "15 시공 완료 · 공개된 설치 결과 및 사진",
                    value =
                        """
              {"isSuccess":true,"status":200,"code":"CONSTRUCTION_200_5","message":"시공 진행 및 결과를 조회했습니다.","result":{"constructionId":9,"supportApplicationId":7,"applicationId":3,"receiptNo":"000003","applicantName":"박주민","address":"경기도 부천시 원미구 길주로 210","detailAddress":"101호","status":"COMPLETED","requestedFacility":"출입구 물막이판","supportScope":"출입구 물막이판 설치","scheduledConstructionDate":"2026-10-21","scheduledConstructionTime":"10:00:00","completedAt":"2026-10-21T11:30:00+09:00","finalInstallationResult":"출입구 물막이판 설치 · 개폐 및 통행 확인 완료","updatedAt":"2026-10-21T11:30:00+09:00","workHistory":[{"status":"SCHEDULED","changedAt":"2026-10-20T15:00:00+09:00"},{"status":"IN_PROGRESS","changedAt":"2026-10-21T10:00:00+09:00"},{"status":"COMPLETED","changedAt":"2026-10-21T11:30:00+09:00"}],"beforePhotos":[{"photoId":11,"fileName":"점검-출입구.jpg","url":"https://example.com/temporary-before-url","size":100,"contentType":"image/jpeg","uploadedAt":"2026-10-19T10:00:00+09:00"}],"photos":[{"photoId":21,"fileName":"설치완료.jpg","url":"https://example.com/temporary-completed-url","size":200,"contentType":"image/jpeg","uploadedAt":"2026-10-21T11:20:00+09:00"}]},"timeStamp":"2026-10-21 11:30:00"}
              """)
              }))
  @ApiResponse(
      responseCode = "500",
      description = "CONSTRUCTION_500_2: 사진 조회 URL 발급 실패",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<ResidentConstructionResultResponse> result(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "조회할 본인 시공 ID") @PathVariable Long constructionId) {
    return SuccessResponse.of(
        service.result(Long.valueOf(jwt.getSubject()), constructionId),
        ConstructionSuccessCode.RESULT_READ);
  }
}
