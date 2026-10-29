package com.flout.server.domain.inspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionSuccessCode implements BaseResponseCode {
  STATUS_CHANGED(200, "INSPECTION_200_1", "점검 상태가 저장되었습니다."),
  ASSIGNMENT_SAVED(200, "INSPECTION_200_2", "점검 배정과 일정이 저장되었습니다."),
  LIST_FOUND(200, "INSPECTION_200_3", "현장점검 목록을 조회했습니다."),
  DETAIL_FOUND(200, "INSPECTION_200_4", "현장점검 배정 상세정보를 조회했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
