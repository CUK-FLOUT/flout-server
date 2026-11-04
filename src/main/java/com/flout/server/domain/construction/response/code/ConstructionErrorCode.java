package com.flout.server.domain.construction.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConstructionErrorCode implements BaseResponseCode {
  PAST_SCHEDULE(400, "CONSTRUCTION_400_1", "확정 시공 일정은 현재 시각 이후로 입력해주세요."),
  JURISDICTION_ACCESS_DENIED(403, "CONSTRUCTION_403_1", "관할 지역의 시공 일정만 저장할 수 있습니다."),
  NOT_FOUND(404, "CONSTRUCTION_404_1", "시공 건을 찾을 수 없습니다."),
  SUPPORT_NOT_APPROVED(409, "CONSTRUCTION_409_1", "지원 승인된 신청만 시공 일정을 저장할 수 있습니다."),
  SCHEDULE_NOT_EDITABLE(409, "CONSTRUCTION_409_2", "시공이 시작되었거나 완료되어 일정을 변경할 수 없습니다.");

  private final int status;
  private final String code;
  private final String message;
}
