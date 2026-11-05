package com.flout.server.domain.publicissue.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublicIssueErrorCode implements BaseResponseCode {
  COORDINATES_UNAVAILABLE(400, "PUBLIC_ISSUE_400_1", "입력한 주소의 위치를 확인할 수 없습니다. 주소를 다시 선택해 주세요."),
  INVALID_ISSUE_TYPE(400, "PUBLIC_ISSUE_400_2", "허용되지 않은 지역·공공 문제 유형입니다."),
  INVALID_REVIEW_STATUS(400, "PUBLIC_ISSUE_400_3", "허용되지 않은 공공시설 검토 상태입니다."),
  PUBLIC_ISSUE_NOT_FOUND(404, "PUBLIC_ISSUE_404_1", "공공시설 문제를 찾을 수 없습니다."),
  FACILITY_NOT_FOUND(404, "PUBLIC_ISSUE_404_2", "공공시설을 찾을 수 없습니다."),
  ACTION_NOT_ALLOWED(409, "PUBLIC_ISSUE_409_1", "현재 상태에서는 공공시설 조치를 등록할 수 없습니다."),
  REVIEW_REQUIRED(409, "PUBLIC_ISSUE_409_2", "공공시설 검토 내용을 먼저 저장해 주세요."),
  REVIEW_NOT_ALLOWED(409, "PUBLIC_ISSUE_409_3", "완료된 공공시설 문제의 검토 내용은 변경할 수 없습니다.");
  private final int status;
  private final String code;
  private final String message;
}
