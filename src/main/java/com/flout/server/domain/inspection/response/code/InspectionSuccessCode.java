package com.flout.server.domain.inspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionSuccessCode implements BaseResponseCode {
  STATUS_CHANGED(200, "INSPECTION_200_1", "점검 상태가 저장되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
