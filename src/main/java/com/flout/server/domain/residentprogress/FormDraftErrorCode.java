package com.flout.server.domain.residentprogress;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FormDraftErrorCode implements BaseResponseCode {
  NOT_FOUND(404, "FORM_DRAFT_404_1", "임시 저장된 신청서를 찾을 수 없습니다."),
  CONFLICT(409, "FORM_DRAFT_409_1", "임시 저장된 신청서가 변경되었거나 제출되었습니다."),
  INSPECTION_DRAFT_EXISTS(
      409, "FORM_DRAFT_409_2", "이미 작성된 현장점검 신청이 있습니다. 주민 홈에서 기존 신청의 사진 첨부를 진행해 주세요."),
  INSPECTION_EXISTS(409, "FORM_DRAFT_409_3", "이미 현장점검 신청이 존재합니다. 주민 홈에서 기존 신청의 진행 현황을 확인해 주세요."),
  SUPPORT_DRAFT_EXISTS(409, "FORM_DRAFT_409_4", "이미 작성된 지원 신청이 있습니다. 주민 홈에서 임대인 동의서 첨부를 진행해 주세요."),
  SUPPORT_EXISTS(409, "FORM_DRAFT_409_5", "이미 지원 신청이 존재합니다. 주민 홈에서 기존 신청의 진행 현황을 확인해 주세요."),
  EDITING_EXISTS(409, "FORM_DRAFT_409_6", "이미 작성 중인 임시저장 폼이 있습니다. 기존 임시저장 폼을 이어서 작성해 주세요."),
  INVALID(400, "FORM_DRAFT_400_1", "임시 저장 요청이 올바르지 않습니다.");

  private final int status;
  private final String code;
  private final String message;
}
