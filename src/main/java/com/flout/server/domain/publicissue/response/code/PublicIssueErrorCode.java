package com.flout.server.domain.publicissue.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublicIssueErrorCode implements BaseResponseCode {
  COORDINATES_UNAVAILABLE(400, "PUBLIC_ISSUE_400_1", "입력한 주소의 위치를 확인할 수 없습니다. 주소를 다시 선택해 주세요."),
  INVALID_ISSUE_TYPE(400, "PUBLIC_ISSUE_400_2", "허용되지 않은 지역·공공 문제 유형입니다.");
  private final int status;
  private final String code;
  private final String message;
}
