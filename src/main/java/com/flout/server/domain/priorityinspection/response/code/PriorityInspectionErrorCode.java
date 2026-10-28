package com.flout.server.domain.priorityinspection.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriorityInspectionErrorCode implements BaseResponseCode {
  JURISDICTION_ACCESS_DENIED(
      403, "PRIORITY_INSPECTION_403_1", "본인 관할 지역의 신청만 우선점검 대상으로 지정할 수 있습니다."),
  DUPLICATE_APPLICATION(400, "PRIORITY_INSPECTION_400_1", "신청 ID는 중복될 수 없습니다."),
  NOT_DESIGNATED(409, "PRIORITY_INSPECTION_409_1", "우선점검 대상으로 지정된 신청만 순서를 저장할 수 있습니다."),
  ORDER_LIST_CHANGED(
      409,
      "PRIORITY_INSPECTION_409_2",
      "관할 지역의 우선점검 대상 전체 신청 ID를 applicationIds에 포함해주세요. 필터와 페이지 밖의 신청도 모두 포함해야 합니다. 지정·취소로 목록이 변경된 경우 다시 조회한 후 요청해주세요.");

  private final int status;
  private final String code;
  private final String message;
}
