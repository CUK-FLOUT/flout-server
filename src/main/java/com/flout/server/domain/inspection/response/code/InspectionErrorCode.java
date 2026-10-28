package com.flout.server.domain.inspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionErrorCode implements BaseResponseCode {
  INSPECTION_NOT_FOUND(404, "INSPECTION_404_1", "점검을 찾을 수 없습니다."),
  JURISDICTION_ACCESS_DENIED(403, "INSPECTION_403_1", "본인 관할 지역의 점검만 변경할 수 있습니다."),
  INVALID_STATUS_TRANSITION(409, "INSPECTION_409_1", "허용되지 않은 점검 상태 변경입니다."),
  VISIT_TIME_NOT_REACHED(409, "INSPECTION_409_2", "확정 방문 일시가 없거나 아직 방문 시간이 되지 않았습니다.");

  private final int status;
  private final String code;
  private final String message;
}
