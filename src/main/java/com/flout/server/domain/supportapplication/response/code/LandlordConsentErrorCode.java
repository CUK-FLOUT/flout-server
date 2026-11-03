package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.*;

@Getter
@RequiredArgsConstructor
public enum LandlordConsentErrorCode implements BaseResponseCode {
  INVALID_OCCUPANCY(400, "LANDLORD_CONSENT_400_1", "세입자만 임대인 동의를 확인할 수 있습니다."),
  ATTACHMENT_REQUIRED(400, "LANDLORD_CONSENT_400_2", "제출된 임대인 동의서가 필요합니다."),
  INVALID_STATUS(409, "LANDLORD_CONSENT_409_1", "동의 확인 대기 상태에서만 확인할 수 있습니다.");

  private final int status;
  private final String code;
  private final String message;
}
