package com.flout.server.domain.publicissue.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublicIssueSuccessCode implements BaseResponseCode {
  REGISTERED(200, "PUBLIC_ISSUE_200_1", "지역·공공 문제 등록 성공");
  private final int status;
  private final String code;
  private final String message;
}
