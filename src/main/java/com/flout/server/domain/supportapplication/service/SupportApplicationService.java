package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
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
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final EntityManager entityManager;
  private final NotificationService notifications;
  private final TransactionTemplate transaction;

  public SupportApplicationService(
      UserRepository users,
      InspectionApplicationRepository applications,
      SupportProgramRepository programs,
      SupportApplicationRepository supports,
      InspectionRepository inspections,
      InspectionResultRepository results,
      EntityManager entityManager,
      NotificationService notifications,
      PlatformTransactionManager manager) {
    this.users = users;
    this.applications = applications;
    this.programs = programs;
    this.supports = supports;
    this.inspections = inspections;
    this.results = results;
    this.entityManager = entityManager;
    this.notifications = notifications;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CreateSupportApplicationResponse submit(
      Long userId, CreateSupportApplicationRequest request) {
    try {
      return transaction.execute(
          status -> {
            var user =
                users
                    .findById(userId)
                    .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
            if (user.getRole() != UserRole.RESIDENT)
              throw new BaseException(SupportApplicationErrorCode.RESIDENT_ONLY);
            var source =
                applications
                    .findById(request.applicationId())
                    .orElseThrow(
                        () -> new BaseException(SupportApplicationErrorCode.APPLICATION_NOT_FOUND));
            if (!source.getApplicant().getId().equals(userId))
              throw new BaseException(SupportApplicationErrorCode.APPLICATION_ACCESS_DENIED);
            boolean published =
                inspections
                    .findByApplicationId(source.getId())
                    .map(
                        inspection ->
                            results
                                .findByInspectionIdAndResultStatus(
                                    inspection.getId(), ResultStatus.PUBLISHED)
                                .isPresent())
                    .orElse(false);
            if (!published)
              throw new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED);
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
            return CreateSupportApplicationResponse.from(saved);
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
