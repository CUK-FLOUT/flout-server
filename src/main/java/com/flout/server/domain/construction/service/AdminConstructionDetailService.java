package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.response.AdminConstructionDetailResponse;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.repository.ConstructionPhotoRepository;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.entity.LandlordConsentStatus;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminConstructionDetailService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final SupportDecisionAccess access;
  private final ConstructionRepository constructions;
  private final ConstructionPhotoRepository photos;
  private final AttachmentStorage storage;

  @Transactional(readOnly = true)
  public AdminConstructionDetailResponse detail(Long operatorId, Long constructionId) {
    access.requireOperator(operatorId);
    if (constructionId == null || constructionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);

    var construction =
        constructions
            .findById(constructionId)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    var support = construction.getSupportApplication();
    if (!BUCHEON.matcher(support.getAddress().strip()).find())
      throw new BaseException(ConstructionErrorCode.JURISDICTION_ACCESS_DENIED);

    var source = support.getApplication();
    var scheduledAt = construction.getScheduledAt();
    var manager = construction.getManager();
    var consent = support.getLandlordConsentStatus();
    boolean published = construction.getStatus() == ConstructionStatus.COMPLETED;
    return new AdminConstructionDetailResponse(
        construction.getId(),
        support.getId(),
        source.getId(),
        String.format(Locale.ROOT, "%06d", source.getId()),
        support.getApplicantName(),
        support.getStatus(),
        consent == LandlordConsentStatus.CONFIRMED ? "VERIFIED" : consent.name(),
        support.getRequestedFacility(),
        support.getSupportScope(),
        support.getPreferredVisitDate(),
        support.getPreferredVisitTime(),
        scheduledAt == null ? null : scheduledAt.toLocalDate(),
        scheduledAt == null ? null : scheduledAt.toLocalTime(),
        construction.getStatus(),
        manager == null ? null : manager.getId(),
        manager == null ? null : manager.getName(),
        published,
        published ? construction.getFinalInstallationResult() : null,
        published && construction.getCompletedAt() != null
            ? construction.getCompletedAt().atZone(SEOUL).toOffsetDateTime()
            : null,
        readPhotos(constructionId));
  }

  private List<AdminConstructionDetailResponse.Photo> readPhotos(Long constructionId) {
    var saved = photos.findByConstructionIdOrderByIdAsc(constructionId);
    try {
      return saved.stream()
          .map(
              photo ->
                  new AdminConstructionDetailResponse.Photo(
                      photo.getId(),
                      photo.getOriginalFilename(),
                      storage.readUrl(photo.getObjectKey()),
                      photo.getFileSize(),
                      photo.getContentType(),
                      photo.getCreatedAt().atZone(SEOUL).toOffsetDateTime()))
          .toList();
    } catch (RuntimeException cause) {
      throw new BaseException(ConstructionErrorCode.PHOTO_READ_FAILED);
    }
  }
}
