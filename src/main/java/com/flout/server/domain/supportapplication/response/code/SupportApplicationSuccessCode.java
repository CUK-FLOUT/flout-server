package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.*;

@Getter
@RequiredArgsConstructor
public enum SupportApplicationSuccessCode implements BaseResponseCode {
  SUBMITTED(201, "SUPPORT_201_1", "지원 신청이 완료되었습니다."),
  ATTACHMENT_SUBMITTED(201, "SUPPORT_201_2", "지원 신청 증빙서류가 제출되었습니다.");
  private final int status;
  private final String code;
  private final String message;
}
