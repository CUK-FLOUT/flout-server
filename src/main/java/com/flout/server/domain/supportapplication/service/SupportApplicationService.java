package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.service.NotificationService;
import com.flout.server.domain.supportapplication.dto.request.CreateSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.CreateSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.SupportApplication;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.repository.SupportProgramRepository;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SupportApplicationService {
  private static final String UNIQUE = "uk_support_application_source";
  private final UserRepository users;
  private final InspectionApplicationRepository applications;
  private final SupportProgramRepository programs;
  private final SupportApplicationRepository supports;
  private final SupportResultPublicationValidator publicationValidator;
  private final EntityManager entityManager;
  private final NotificationService notifications;
  private final TransactionTemplate transaction;
  private final com.flout.server.domain.residentprogress.FormDraftService formDrafts;

  public SupportApplicationService(
      UserRepository users,
      InspectionApplicationRepository applications,
      SupportProgramRepository programs,
      SupportApplicationRepository supports,
      SupportResultPublicationValidator publicationValidator,
      EntityManager entityManager,
      NotificationService notifications,
      PlatformTransactionManager manager,
      com.flout.server.domain.residentprogress.FormDraftService formDrafts) {
    this.users = users;
    this.applications = applications;
    this.programs = programs;
    this.supports = supports;
    this.publicationValidator = publicationValidator;
    this.entityManager = entityManager;
    this.notifications = notifications;
    this.formDrafts = formDrafts;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CreateSupportApplicationResponse submit(
      Long userId, CreateSupportApplicationRequest request) {
    return submit(userId, request, null);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CreateSupportApplicationResponse submit(
      Long userId, CreateSupportApplicationRequest request, java.util.UUID draftId) {
    try {
      return transaction.execute(
          status -> {
            var user =
                users
                    .findById(userId)
                    .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
            if (user.getRole() != UserRole.RESIDENT)
              throw new BaseException(SupportApplicationErrorCode.RESIDENT_ONLY);
            formDrafts.lockSupportSource(request.applicationId());
            var previous =
                formDrafts.priorSubmission(
                    userId,
                    "SUPPORT",
                    draftId,
                    request.applicationId(),
                    request,
                    CreateSupportApplicationResponse.class);
            if (previous != null) return previous;
            var source =
                applications
                    .findById(request.applicationId())
                    .orElseThrow(
                        () -> new BaseException(SupportApplicationErrorCode.APPLICATION_NOT_FOUND));
            if (!source.getApplicant().getId().equals(userId))
              throw new BaseException(SupportApplicationErrorCode.APPLICATION_ACCESS_DENIED);
            publicationValidator.requirePublished(source.getId());
            var program =
                programs
                    .findById(request.programId())
                    .orElseThrow(
                        () -> new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_FOUND));
            if (supports.existsByApplicationId(source.getId()))
              throw new BaseException(SupportApplicationErrorCode.DUPLICATE_APPLICATION);
            if (source.getApplicantName() == null
                || source.getApplicantName().isBlank()
                || source.getContact() == null
                || source.getContact().isBlank()
                || source.getAddress() == null
                || source.getAddress().isBlank()
                || source.getOccupancyType() == null)
              throw new BaseException(SupportApplicationErrorCode.SOURCE_INFORMATION_UNAVAILABLE);
            var saved =
                supports.saveAndFlush(
                    SupportApplication.submitted(
                        source,
                        program,
                        user,
                        request.requestedFacility(),
                        request.preferredVisitDate(),
                        request.preferredVisitTime()));

            entityManager.refresh(saved);
            if (saved.getStatus() == SupportApplicationStatus.SUBMITTED) {
              notifications.send(
                  userId,
                  NotificationType.SUPPORT_RECEIVED,
                  source.getId(),
                  "support-received:" + saved.getId());
            }
            var response = CreateSupportApplicationResponse.from(saved);
            formDrafts.link(
                userId,
                "SUPPORT",
                draftId,
                source.getId(),
                saved.getId(),
                request,
                response,
                saved.getStatus() != SupportApplicationStatus.DRAFT);
            if (draftId == null)
              formDrafts.scheduleUniqueLink(
                  userId, "SUPPORT", source.getId(), saved.getId(), request, response);
            return response;
          });
    } catch (DataIntegrityViolationException e) {
      if (isDuplicate(e))
        throw new BaseException(SupportApplicationErrorCode.DUPLICATE_APPLICATION);
      throw e;
    }
  }

  private boolean isDuplicate(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException v
          && v.getConstraintName() != null
          && v.getConstraintName().toLowerCase(Locale.ROOT).contains(UNIQUE)) return true;
      if (cause instanceof SQLException s
          && ("23505".equals(s.getSQLState()) || s.getErrorCode() == 1062)
          && s.getMessage() != null
          && s.getMessage().toLowerCase(Locale.ROOT).contains(UNIQUE)) return true;
    }
    return false;
  }
}
