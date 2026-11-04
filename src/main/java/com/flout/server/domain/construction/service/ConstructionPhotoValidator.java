package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator;
import com.flout.server.global.exception.BaseException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ConstructionPhotoValidator {
  private final InspectionPhotoValidator validator;

  public InspectionPhotoValidator.ValidatedPhoto validate(MultipartFile file) {
    return checked(() -> validator.validate(file));
  }

  public byte[] bytesForUpload(InspectionPhotoValidator.ValidatedPhoto photo) {
    return checked(() -> validator.bytesForUpload(photo));
  }

  /** 기존 이미지 내용·크기 검증을 재사용하고 시공 도메인의 오류 코드로 변환한다. */
  private <T> T checked(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.EMPTY_PHOTOS)
        throw new BaseException(ConstructionErrorCode.EMPTY_PHOTOS);
      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS)
        throw new BaseException(ConstructionErrorCode.INVALID_PHOTOS);
      throw e;
    }
  }
}
