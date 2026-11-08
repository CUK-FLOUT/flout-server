package com.flout.server.domain.publicissue.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublicIssueSuccessCode implements BaseResponseCode {
  REGISTERED(200, "PUBLIC_ISSUE_200_1", "지역·공공 문제 등록 성공"),
  ACTION_COMPLETED(200, "PUBLIC_ISSUE_200_2", "공공시설 조치 완료 처리 성공"),
  REVIEW_SAVED(200, "PUBLIC_ISSUE_200_3", "공공시설 검토 내용 저장 성공"),
  ACTION_IMAGES_UPLOADED(200, "PUBLIC_ISSUE_200_4", "공공시설 조치 사진 업로드 성공"),
  LIST_FOUND(200, "PUBLIC_ISSUE_200_5", "지역·공공 문제 목록 조회 성공"),
  STATUS_FOUND(200, "PUBLIC_ISSUE_200_6", "지역·공공 문제 현황 건수 조회 성공"),
  MAP_FOUND(200, "PUBLIC_ISSUE_200_7", "지역·공공 문제 지도 조회 성공");
  private final int status;
  private final String code;
  private final String message;
}
