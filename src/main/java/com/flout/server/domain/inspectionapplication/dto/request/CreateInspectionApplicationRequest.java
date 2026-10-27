package com.flout.server.domain.inspectionapplication.dto.request;

import com.flout.server.domain.inspectionapplication.entity.FloodExperience;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInspectionApplicationRequest(
    @NotNull(message = "취약도 분석 결과 ID는 필수입니다.") @Schema(
            description = "점검을 신청할 완료 상태의 침수 위험도 분석 ID",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Long assessmentId,
    @Schema(description = "분석 결과에 연결된 예방 가이드 ID", example = "1") Long guideId,
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
    @NotBlank(message = "연락처는 필수입니다.") @Size(max = 50, message = "연락처는 50자 이하여야 합니다.") @Schema(
            description = "연락 가능한 전화번호(최대 50자)",
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
    @Schema(description = "현장점검 요청 사유", example = "최근 배수구 역류가 있어 점검을 희망합니다.") String requestReason,
    @FutureOrPresent @Schema(description = "희망 방문일(오늘 이후)", format = "date", example = "2026-11-03")
        LocalDate preferredVisitDate,
    @Schema(
            description = "희망 방문 시간대",
            allowableValues = {"MORNING", "AFTERNOON", "ANY"},
            example = "MORNING")
        PreferredTimeSlot preferredTimeSlot,
    @Schema(
            description = "침수 유입 위치 목록",
            allowableValues = {"WINDOW", "ENTRANCE", "BATHROOM", "DRAIN", "ETC", "UNKNOWN"},
            example = "[\"WINDOW\", \"DRAIN\"]")
        List<InflowLocation> inflowLocations) {}
