package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportDetailErrorCode implements BaseResponseCode {
  APPLICATION_NOT_FOUND(404, "SUPPORT_404_1", "요청한 지원 신청 내역이 존재하지 않습니다."),
  ATTACHMENT_READ_FAILED(500, "SUPPORT_DETAIL_500_1", "지원 신청 첨부자료를 조회할 수 없습니다.");
  private final int status;
  private final String code;
  private final String message;
}
