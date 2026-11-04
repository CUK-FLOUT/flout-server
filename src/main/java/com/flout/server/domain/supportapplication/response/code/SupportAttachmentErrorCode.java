package com.flout.server.domain.supportapplication.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.*;

@Getter
@RequiredArgsConstructor
public enum SupportAttachmentErrorCode implements BaseResponseCode {
  INVALID_FILE(
      400, "SUPPORT_ATTACHMENT_400_1", "15 MiB 이하의 유효한 PDF, JPG, JPEG, PNG 파일 1개를 제출해 주세요."),
  INVALID_OCCUPANCY(400, "SUPPORT_ATTACHMENT_400_2", "세입자만 임대인 동의서를 제출할 수 있습니다."),
  APPLICATION_NOT_FOUND(404, "SUPPORT_ATTACHMENT_404_1", "지원 신청을 찾을 수 없습니다."),
  ACCESS_DENIED(403, "SUPPORT_ATTACHMENT_403_1", "본인의 지원 신청에만 증빙서류를 제출할 수 있습니다."),
  ALREADY_SUBMITTED(409, "SUPPORT_ATTACHMENT_409_1", "이미 제출한 유형의 증빙서류입니다."),
  APPLICATION_NOT_DRAFT(409, "SUPPORT_ATTACHMENT_409_2", "임시 저장 상태의 신청만 접수할 수 있습니다."),
  UPLOAD_FAILED(500, "SUPPORT_ATTACHMENT_500_1", "증빙서류 제출에 실패했습니다.");
  private final int status;
  private final String code;
  private final String message;
}
