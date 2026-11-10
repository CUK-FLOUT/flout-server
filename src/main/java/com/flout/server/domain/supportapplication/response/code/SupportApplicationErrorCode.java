package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportApplicationErrorCode implements BaseResponseCode {
  SOURCE_INFORMATION_UNAVAILABLE(400, "SUPPORT_400_1", "기존 신청자 기본정보를 확인할 수 없습니다. 정보를 확인해 주세요."),
  RESULT_NOT_PUBLISHED(400, "SUPPORT_400_2", "현장점검 결과가 주민에게 공개된 후에만 지원 신청할 수 있습니다."),
  RESIDENT_ONLY(403, "SUPPORT_403_1", "주민만 지원 신청을 할 수 있습니다."),
  APPLICATION_ACCESS_DENIED(403, "SUPPORT_403_2", "본인의 현장점검 신청만 사용할 수 있습니다."),
  APPLICATION_NOT_FOUND(404, "SUPPORT_404_1", "현장점검 신청을 찾을 수 없습니다."),
  PROGRAM_NOT_FOUND(404, "SUPPORT_404_2", "지원사업을 찾을 수 없습니다."),
  MATCHED_PROGRAM_NOT_FOUND(404, "SUPPORT_404_3", "분석 결과와 주소에 맞는 지원사업을 찾을 수 없습니다."),
  DUPLICATE_APPLICATION(409, "SUPPORT_409_1", "이미 접수된 지원 신청이 존재합니다."),
  MATCHING_INPUT_INVALID(500, "SUPPORT_500_1", "추천 서비스를 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),
  GEMINI_RETRY_REQUIRED(503, "SUPPORT_503_1", "추천 서비스에 일시적인 문제가 발생했습니다. 다시 시도해 주세요.");

  private final int status;
  private final String code;
  private final String message;
}
