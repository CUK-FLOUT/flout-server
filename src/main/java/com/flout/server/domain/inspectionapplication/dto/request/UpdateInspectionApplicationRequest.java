package com.flout.server.domain.inspectionapplication.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.validation.KoreanMobileNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Schema(description = "생략한 필드는 유지합니다. 요청 사유는 null로 삭제할 수 있습니다.")
public class UpdateInspectionApplicationRequest {
  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(max = 50, message = "신청자 이름은 50자 이하여야 합니다.") @Pattern(regexp = "(?sU).*\\S.*", message = "신청자 이름은 공백일 수 없습니다.")
  @Schema(description = "신청자 이름", example = "김주민")
  private String applicantName;

  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  @KoreanMobileNumber
  @Schema(description = "연락처 (전달한 경우 국내 휴대전화 번호, 하이픈 선택)", example = "01012345678")
  private String contact;

  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  private OccupancyType occupancyType;

  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  private FloodExperience floodExperience;

  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean existingFacility;

  @Size(max = 2000, message = "요청 사유는 2000자 이하여야 합니다.") @Schema(description = "점검 요청 사유. null이면 삭제", nullable = true)
  private String requestReason;

  @FutureOrPresent(message = "희망 방문일은 오늘 이후여야 합니다.") @Schema(description = "희망 방문일. 오늘 이후", example = "2030-12-01")
  private LocalDate preferredVisitDate;

  @Schema(description = "희망 방문 시간대", example = "MORNING")
  private PreferredTimeSlot preferredTimeSlot;

  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(min = 1, message = "침수 유입 위치는 1개 이상 선택해야 합니다.") @Schema(description = "유입 위치 전체 교체. 1개 이상 필수")
  private List<@NotNull InflowLocation> inflowLocations;

  @Getter(AccessLevel.NONE)
  private boolean requestReasonProvided;

  @Getter(AccessLevel.NONE)
  private boolean preferredVisitDateProvided;

  @Getter(AccessLevel.NONE)
  private boolean preferredTimeSlotProvided;

  @JsonSetter
  public void setRequestReason(String value) {
    requestReasonProvided = true;
    requestReason = value;
  }

  @JsonSetter(nulls = Nulls.FAIL)
  public void setPreferredVisitDate(LocalDate value) {
    preferredVisitDateProvided = true;
    preferredVisitDate = value;
  }

  @JsonSetter(nulls = Nulls.FAIL)
  public void setPreferredTimeSlot(PreferredTimeSlot value) {
    preferredTimeSlotProvided = true;
    preferredTimeSlot = value;
  }

  @JsonAnySetter
  public void rejectUnknownField(String name, Object value) {
    throw new IllegalArgumentException("수정할 수 없는 필드입니다: " + name);
  }

  public boolean requestReasonProvided() {
    return requestReasonProvided;
  }

  public boolean preferredVisitDateProvided() {
    return preferredVisitDateProvided;
  }

  public boolean preferredTimeSlotProvided() {
    return preferredTimeSlotProvided;
  }
}
