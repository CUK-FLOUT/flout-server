package com.flout.server.domain.construction.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConstructionErrorCode implements BaseResponseCode {
  PHOTO_READ_FAILED(500, "CONSTRUCTION_500_2", "시공 사진을 불러오지 못했습니다."),
  PAST_SCHEDULE(400, "CONSTRUCTION_400_1", "확정 시공 일정은 현재 시각 이후로 입력해주세요."),
  JURISDICTION_ACCESS_DENIED(403, "CONSTRUCTION_403_1", "관할 지역의 시공 건만 변경할 수 있습니다."),
  NOT_FOUND(404, "CONSTRUCTION_404_1", "시공 건을 찾을 수 없습니다."),
  SUPPORT_NOT_APPROVED(409, "CONSTRUCTION_409_1", "지원 승인된 신청만 시공 업무를 처리할 수 있습니다."),
  SCHEDULE_NOT_EDITABLE(409, "CONSTRUCTION_409_2", "이미 배정되었거나 시공이 시작·완료되어 일정을 저장할 수 없습니다."),
  EMPTY_PHOTOS(400, "CONSTRUCTION_400_2", "시공 사진을 최소 1장 선택해주세요."),
  INVALID_PHOTOS(400, "CONSTRUCTION_400_3", "JPG, JPEG, PNG 형식의 유효한 사진만 업로드할 수 있습니다."),
  PHOTOS_NOT_EDITABLE(409, "CONSTRUCTION_409_3", "전체 완료된 시공에는 사진을 추가할 수 없습니다."),
  PHOTO_UPLOAD_FAILED(500, "CONSTRUCTION_500_1", "시공 사진 업로드에 실패했습니다."),
  COMPLETION_PHOTO_REQUIRED(400, "CONSTRUCTION_400_4", "전체 완료하려면 시공 사진을 최소 1장 업로드해주세요."),
  ALREADY_COMPLETED(409, "CONSTRUCTION_409_4", "이미 전체 완료된 시공입니다."),
  SCHEDULE_REQUIRED(409, "CONSTRUCTION_409_5", "시공 일정을 저장한 후 전체 완료할 수 있습니다.");

  private final int status;
  private final String code;
  private final String message;
}
