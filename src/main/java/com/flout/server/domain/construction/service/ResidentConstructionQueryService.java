package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.response.ResidentConstructionResultResponse;
import com.flout.server.domain.construction.dto.response.ResidentConstructionScheduleResponse;
import com.flout.server.domain.construction.entity.Construction;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.repository.ConstructionPhotoRepository;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.repository.ConstructionStatusHistoryRepository;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultPhotoRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ResidentConstructionQueryService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final UserRepository users;
  private final ConstructionRepository constructions;
  private final ConstructionPhotoRepository photos;
  private final ConstructionStatusHistoryRepository histories;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionResultPhotoRepository inspectionPhotos;
  private final AttachmentStorage storage;

  public ResidentConstructionScheduleResponse schedule(Long userId, Long constructionId) {
    return ResidentConstructionScheduleResponse.from(owned(userId, constructionId));
  }

  public ResidentConstructionResultResponse result(Long userId, Long constructionId) {
    var construction = owned(userId, constructionId);
    var schedule = ResidentConstructionScheduleResponse.from(construction);
    boolean completed = construction.getStatus() == ConstructionStatus.COMPLETED;
    var workHistory =
        histories.findByConstructionIdOrderByChangedAtAscIdAsc(constructionId).stream()
            .map(
                history ->
                    new ResidentConstructionResultResponse.WorkHistory(
                        history.getStatus(), seoulTime(history.getChangedAt())))
            .toList();
    List<ResidentConstructionResultResponse.Photo> beforePhotos = List.of();
    List<ResidentConstructionResultResponse.Photo> completedPhotos = List.of();
    if (completed) {
      try {
        beforePhotos =
            inspections
                .findByApplicationId(schedule.applicationId())
                .flatMap(
                    inspection ->
                        results.findByInspectionIdAndResultStatus(
                            inspection.getId(), ResultStatus.PUBLISHED))
                .map(
                    result ->
                        inspectionPhotos.findAllByResultIdOrderByIdAsc(result.getId()).stream()
                            .map(
                                photo ->
                                    new ResidentConstructionResultResponse.Photo(
                                        photo.getId(),
                                        photo.getOriginalFilename(),
                                        storage.readUrl(photo.getObjectKey()),
                                        photo.getFileSize(),
                                        photo.getContentType(),
                                        seoulTime(photo.getCreatedAt())))
                            .toList())
                .orElseGet(List::of);
        completedPhotos =
            photos.findByConstructionIdOrderByIdAsc(constructionId).stream()
                .map(
                    photo ->
                        new ResidentConstructionResultResponse.Photo(
                            photo.getId(),
                            photo.getOriginalFilename(),
                            storage.readUrl(photo.getObjectKey()),
                            photo.getFileSize(),
                            photo.getContentType(),
                            seoulTime(photo.getCreatedAt())))
                .toList();
      } catch (RuntimeException cause) {
        log.error(
            "주민 시공 사진 조회 실패. constructionId={}, failureType={}",
            constructionId,
            cause.getClass().getName());
        throw new BaseException(ConstructionErrorCode.PHOTO_READ_FAILED);
      }
    }
    return new ResidentConstructionResultResponse(
        schedule.constructionId(),
        schedule.supportApplicationId(),
        schedule.applicationId(),
        schedule.receiptNo(),
        schedule.applicantName(),
        schedule.address(),
        schedule.detailAddress(),
        schedule.status(),
        schedule.requestedFacility(),
        schedule.supportScope(),
        schedule.scheduledConstructionDate(),
        schedule.scheduledConstructionTime(),
        completed ? seoulTime(construction.getCompletedAt()) : null,
        completed ? construction.getFinalInstallationResult() : null,
        seoulTime(construction.getUpdatedAt()),
        workHistory,
        beforePhotos,
        completedPhotos);
  }

  private Construction owned(Long userId, Long id) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var construction =
        constructions
            .findById(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    var support = construction.getSupportApplication();
    if (!support.getApplicant().getId().equals(userId))
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (support.getStatus() != SupportApplicationStatus.APPROVED)
      throw new BaseException(ConstructionErrorCode.SUPPORT_NOT_APPROVED);
    return construction;
  }

  private static OffsetDateTime seoulTime(LocalDateTime value) {
    return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
  }
}
