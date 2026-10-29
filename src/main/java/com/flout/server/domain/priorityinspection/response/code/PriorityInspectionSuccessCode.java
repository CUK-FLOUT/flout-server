package com.flout.server.domain.priorityinspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriorityInspectionSuccessCode implements BaseResponseCode {
  DESIGNATED(200, "PRIORITY_INSPECTION_200_1", "우선점검 대상으로 지정되었습니다."),
  CANCELLED(200, "PRIORITY_INSPECTION_200_2", "우선점검 대상 지정이 취소되었습니다."),
  ORDER_SAVED(200, "PRIORITY_INSPECTION_200_3", "우선점검 선정 순서가 저장되었습니다."),
  CANDIDATES_FOUND(200, "PRIORITY_INSPECTION_200_4", "우선점검 후보 목록을 조회했습니다."),
  DETAIL_FOUND(200, "PRIORITY_INSPECTION_200_5", "우선점검 신청정보를 조회했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
