package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.service.*;
import com.flout.server.domain.supportapplication.dto.response.SupportAttachmentResponse;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.supportapplication.response.code.*;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.sql.SQLException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class SupportAttachmentService {
  private static final String UNIQUE = "uk_support_attachment_type";
  private final UserRepository users;
  private final SupportApplicationRepository applications;
  private final SupportAttachmentRepository attachments;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final SupportDocumentValidator validator;
  private final TransactionTemplate tx;

  public SupportAttachmentService(
      UserRepository users,
      SupportApplicationRepository applications,
      SupportAttachmentRepository attachments,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      SupportDocumentValidator validator,
      PlatformTransactionManager manager) {
    this.users = users;
    this.applications = applications;
    this.attachments = attachments;
    this.storage = storage;
    this.cleanup = cleanup;
    this.validator = validator;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SupportAttachmentResponse submit(Long userId, Long applicationId, MultipartFile file) {
    if (applicationId == null || applicationId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var type =
        tx.execute(
            s -> {
              var application = authorize(userId, applicationId, false);
              var determined = resolveType(application);
              rejectDuplicate(applicationId, determined);
              requireDraft(application);
              return determined;
            });
    var document = validator.validate(file);
    String key =
        "support-applications/" + applicationId + "/attachments/" + type + "/" + UUID.randomUUID();
    var keys = List.of(key);
    try {
      cleanup.register(keys);
      storage.upload(key, document.bytes(), document.contentType());
      var result =
          tx.execute(
              s -> {
                cleanup.lock(key);
                var application = authorize(userId, applicationId, true);
                var currentType = resolveType(application);
                if (currentType != type)
                  throw new BaseException(SupportAttachmentErrorCode.INVALID_OCCUPANCY);
                rejectDuplicate(applicationId, currentType);
                requireDraft(application);
                var saved =
                    attachments.saveAndFlush(
                        SupportAttachment.submitted(
                            application,
                            currentType,
                            key,
                            document.filename(),
                            document.bytes().length,
                            document.contentType()));
                application.submitDraft();
                cleanup.completed(key);
                return SupportAttachmentResponse.from(saved);
              });
      cleanup.reconcile(keys);
      return result;
    } catch (RuntimeException e) {
      cleanup.reconcile(keys);
      if (e instanceof BaseException base) throw base;
      if (e instanceof DataIntegrityViolationException && duplicate(e))
        throw new BaseException(SupportAttachmentErrorCode.ALREADY_SUBMITTED);
      log.error("증빙서류 제출 실패. failureType={}", e.getClass().getSimpleName());
      throw new BaseException(SupportAttachmentErrorCode.UPLOAD_FAILED);
    }
  }

  private SupportApplication authorize(Long userId, Long id, boolean lock) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT)
      throw new BaseException(SupportApplicationErrorCode.RESIDENT_ONLY);
    var application =
        (lock ? applications.findForDocumentUpload(id) : applications.findById(id))
            .orElseThrow(() -> new BaseException(SupportAttachmentErrorCode.APPLICATION_NOT_FOUND));
    if (!application.getApplicant().getId().equals(userId))
      throw new BaseException(SupportAttachmentErrorCode.ACCESS_DENIED);
    return application;
  }

  private SupportAttachmentType resolveType(SupportApplication application) {
    var occupancy = application.getOccupancyType();
    if (occupancy == OccupancyType.TENANT) return SupportAttachmentType.LANDLORD_CONSENT;
    throw new BaseException(SupportAttachmentErrorCode.INVALID_OCCUPANCY);
  }

  private void rejectDuplicate(Long id, SupportAttachmentType type) {
    if (attachments.existsByApplicationIdAndAttachmentType(id, type))
      throw new BaseException(SupportAttachmentErrorCode.ALREADY_SUBMITTED);
  }

  private void requireDraft(SupportApplication application) {
    if (application.getStatus() != SupportApplicationStatus.DRAFT)
      throw new BaseException(SupportAttachmentErrorCode.APPLICATION_NOT_DRAFT);
  }

  private boolean duplicate(Throwable e) {
    for (Throwable c = e; c != null; c = c.getCause()) {
      if (c instanceof ConstraintViolationException v
          && v.getConstraintName() != null
          && v.getConstraintName().toLowerCase(Locale.ROOT).contains(UNIQUE)) return true;
      if (c instanceof SQLException s
          && ("23505".equals(s.getSQLState()) || s.getErrorCode() == 1062)
          && s.getMessage() != null
          && s.getMessage().toLowerCase(Locale.ROOT).contains(UNIQUE)) return true;
    }
    return false;
  }
}
