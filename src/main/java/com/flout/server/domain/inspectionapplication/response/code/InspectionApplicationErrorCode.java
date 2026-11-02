package com.flout.server.domain.inspectionapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InspectionApplicationErrorCode implements BaseResponseCode {
  ASSESSMENT_NOT_FOUND(404, "APPLICATION_404_1", "취약도 분석 결과를 찾을 수 없습니다."),

  ASSESSMENT_ACCESS_DENIED(403, "APPLICATION_403_1", "본인의 취약도 분석 결과만 사용할 수 있습니다."),

  ASSESSMENT_NOT_COMPLETED(409, "APPLICATION_409_1", "취약도 분석이 완료되지 않아 현장점검을 신청할 수 없습니다."),

  DUPLICATE_ASSESSMENT_APPLICATION(409, "APPLICATION_409_3", "이미 현장점검을 신청한 취약도 분석 결과입니다."),

  GUIDE_NOT_FOUND(400, "APPLICATION_400_1", "가이드가 취약도 분석 결과에 연결되어 있지 않습니다."),

  INVALID_APPLICATION_ADDRESS(400, "APPLICATION_400_2", "부천시 관할 주소만 현장점검을 신청할 수 있습니다."),

  ASSESSMENT_ADDRESS_MISMATCH(400, "APPLICATION_400_3", "현장점검 신청 주소가 취약도 분석 주소와 일치하지 않습니다."),

  RECEIPT_NUMBER_GENERATION_FAILED(500, "APPLICATION_500_1", "접수번호 생성에 실패했습니다."),

  RESIDENT_ONLY(403, "APPLICATION_403_2", "주민만 현장점검을 신청할 수 있습니다."),

  APPLICATION_NOT_FOUND(404, "APPLICATION_404_2", "현장점검 신청을 찾을 수 없습니다."),

  JURISDICTION_ACCESS_DENIED(403, "APPLICATION_403_3", "본인 관할 지역의 신청만 수정할 수 있습니다."),
  JURISDICTION_READ_ACCESS_DENIED(403, "APPLICATION_403_4", "본인 관할 지역의 신청만 조회할 수 있습니다."),

  APPLICATION_ACCESS_DENIED(403, "APPLICATION_403_5", "본인의 신청서에만 사진을 등록할 수 있습니다."),
  APPLICATION_DETAIL_ACCESS_DENIED(403, "APPLICATION_403_6", "본인의 신청서만 조회할 수 있습니다."),
  INVALID_ATTACHMENTS(400, "APPLICATION_400_4", "사진은 신청서당 최대 5장, 파일당 15MB 이하의 JPG 또는 PNG여야 합니다."),
  ATTACHMENT_UPLOAD_FAILED(500, "APPLICATION_500_2", "사진 등록에 실패했습니다."),
  ATTACHMENT_READ_FAILED(500, "APPLICATION_500_3", "첨부사진 조회에 실패했습니다."),
  ATTACHMENT_IDEMPOTENCY_CONFLICT(409, "APPLICATION_409_2", "동일한 요청 키를 다른 사진에 사용할 수 없습니다.");

  private final int status;
  private final String code;
  private final String message;
}
