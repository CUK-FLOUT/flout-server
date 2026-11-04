package com.flout.server.domain.construction.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConstructionSuccessCode implements BaseResponseCode {
  SCHEDULE_SAVED(200, "CONSTRUCTION_200_1", "시공 일정이 저장되었습니다."),
  PHOTOS_UPLOADED(200, "CONSTRUCTION_200_2", "시공 사진이 업로드되었습니다.");
  private final int status;
  private final String code;
  private final String message;
}
