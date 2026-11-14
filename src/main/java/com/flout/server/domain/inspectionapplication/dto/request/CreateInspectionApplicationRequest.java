package com.flout.server.domain.inspectionapplication.dto.request;

import com.flout.server.domain.inspectionapplication.entity.FloodExperience;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import com.flout.server.domain.inspectionapplication.validation.KoreanMobileNumber;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInspectionApplicationRequest(
    @NotNull(message = "취약도 분석 결과 ID는 필수입니다.") @Schema(
            description = "점검을 신청할 완료 상태의 침수 위험도 분석 ID",
            example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long assessmentId,
    @Schema(description = "분석 결과에 연결된 예방 가이드 ID", example = "1") Long guideId,
    @NotBlank(message = "점검 주소는 필수입니다.") @Size(max = 255, message = "점검 주소는 255자 이하여야 합니다.") @Schema(
            description = "점검 주소 (필수, 최대 255자)",
            example = "경기도 부천시 원미구 지봉로 43",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String address,
    @NotBlank(message = "상세 주소는 필수입니다.") @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.") @Schema(
            description = "상세 주소 (필수, 최대 255자)",
            example = "101호",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String detailAddress,
    @NotNull(message = "위도는 필수입니다.") @DecimalMin(value = "-90.0000000", message = "위도는 -90 이상이어야 합니다.") @DecimalMax(value = "90.0000000", message = "위도는 90 이하여야 합니다.") @Schema(
            description = "점검 위치 위도(-90.0000000~90.0000000)",
            example = "37.4874850",
            requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,
    @NotNull(message = "경도는 필수입니다.") @DecimalMin(value = "-180.0000000", message = "경도는 -180 이상이어야 합니다.") @DecimalMax(value = "180.0000000", message = "경도는 180 이하여야 합니다.") @Schema(
            description = "점검 위치 경도(-180.0000000~180.0000000)",
            example = "126.7985672",
            requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,
    @NotNull(message = "연락처는 필수입니다.") @KoreanMobileNumber
        @Schema(
            description = "국내 휴대전화 번호 (하이픈 포함 또는 제외)",
            example = "010-1234-5678",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String contact,
    @NotNull(message = "점유 형태는 필수입니다.") @Schema(
            description = "점유 형태",
            allowableValues = {"OWNER", "TENANT", "ETC"},
            example = "OWNER",
            requiredMode = Schema.RequiredMode.REQUIRED)
        OccupancyType occupancyType,
    @NotNull(message = "과거 침수 경험 여부는 필수입니다.") @Schema(
            description = "과거 침수 경험 여부",
            allowableValues = {"YES", "NO", "UNKNOWN"},
            example = "YES",
            requiredMode = Schema.RequiredMode.REQUIRED)
        FloodExperience floodExperience,
    @NotNull(message = "기존 침수 방지 시설 설치 여부는 필수입니다.") @Schema(
            description = "기존 침수 방지 시설 설치 여부",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean existingFacility,
    @Size(max = 2000) @Schema(description = "현장점검 요청 사유 (최대 2000자)", example = "최근 배수구 역류가 있어 점검을 희망합니다.")
        String requestReason,
    @NotNull(message = "희망 방문일은 필수입니다.") @FutureOrPresent @Schema(
            description = "희망 방문일(오늘 이후)",
            format = "date",
            example = "2030-12-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate preferredVisitDate,
    @NotNull(message = "희망 방문 시간대는 필수입니다.") @Schema(
            description = "희망 방문 시간대",
            allowableValues = {"MORNING", "AFTERNOON", "ANY"},
            example = "MORNING",
            requiredMode = Schema.RequiredMode.REQUIRED)
        PreferredTimeSlot preferredTimeSlot,
    @NotEmpty(message = "침수 유입 위치는 1개 이상 선택해야 합니다.") @ArraySchema(
            arraySchema =
                @Schema(description = "침수 유입 위치 목록", requiredMode = Schema.RequiredMode.REQUIRED),
            schema =
                @Schema(
                    implementation = InflowLocation.class,
                    allowableValues = {
                      "WINDOW",
                      "ENTRANCE",
                      "BATHROOM",
                      "DRAIN",
                      "ETC",
                      "UNKNOWN"
                    }),
            minItems = 1)
        List<@NotNull InflowLocation> inflowLocations,
    @NotBlank(message = "신청자 이름은 필수입니다.") @Size(max = 50, message = "신청자 이름은 50자 이하여야 합니다.") @Schema(
            description = "신청 당시 입력한 신청자 이름",
            example = "김주민",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String applicantName) {}
