package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.*;

@Getter
@RequiredArgsConstructor
public enum SupportApprovalErrorCode implements BaseResponseCode {
  CONSENT_DOCUMENT_REQUIRED(400, "SUPPORT_APPROVAL_400_1", "임대인 동의서 제출이 필요합니다."),
  INVALID_OCCUPANCY(400, "SUPPORT_APPROVAL_400_2", "승인할 수 없는 거주 형태입니다."),
  JURISDICTION_ACCESS_DENIED(403, "SUPPORT_APPROVAL_403_1", "해당 관할 지원 신청에 접근할 수 없습니다."),
  APPLICATION_NOT_FOUND(404, "SUPPORT_APPROVAL_404_1", "지원 신청을 찾을 수 없습니다."),
  ALREADY_DECIDED(409, "SUPPORT_APPROVAL_409_1", "이미 처리된 지원 신청입니다.");
  private final int status;
  private final String code;
  private final String message;
}
