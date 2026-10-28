package com.flout.server.domain.inspectionapplication.response.code;

import static com.flout.server.global.constant.StaticValue.CREATED;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionApplicationSuccessCode implements BaseResponseCode {
  INSPECTION_APPLICATION_CREATED(CREATED, "APPLICATION_201_1", "현장점검 신청이 완료되었습니다."),
  INTERNAL_MEMO_SAVED(200, "APPLICATION_200_1", "내부 메모가 저장되었습니다.");

  private final int status;
  private final String code;
  private final String message;
}
