package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class PublicIssueActionImageValidator {
  private final InspectionPhotoValidator validator;

  public InspectionPhotoValidator.ValidatedPhoto validate(MultipartFile file) {
    return checked(() -> validator.validate(file));
  }

  public byte[] bytesForUpload(InspectionPhotoValidator.ValidatedPhoto photo) {
    return checked(() -> validator.bytesForUpload(photo));
  }

  private <T> T checked(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.EMPTY_PHOTOS)
        throw new BaseException(PublicIssueErrorCode.EMPTY_ACTION_IMAGES);
      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS)
        throw new BaseException(PublicIssueErrorCode.INVALID_ACTION_IMAGES);
      throw e;
    }
  }
}
