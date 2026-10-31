package com.flout.server.domain.inspectionapplication.response.code;

import static com.flout.server.global.constant.StaticValue.CREATED;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionApplicationSuccessCode implements BaseResponseCode {
  INSPECTION_APPLICATION_CREATED(CREATED, "APPLICATION_201_1", "현장점검 신청이 완료되었습니다."),
  ATTACHMENTS_CREATED(CREATED, "APPLICATION_201_2", "사진이 등록되었습니다."),
  INTERNAL_MEMO_SAVED(200, "APPLICATION_200_1", "내부 메모가 저장되었습니다."),
  DETAIL_FOUND(200, "APPLICATION_200_2", "현장점검 신청 상세정보를 조회했습니다."),
  ATTACHMENTS_FOUND(200, "APPLICATION_200_3", "신청 첨부사진을 조회했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
