package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.entity.ApplicationAttachment;
import com.flout.server.domain.inspectionapplication.entity.ApplicationStatusHistory;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import com.flout.server.domain.inspectionapplication.repository.ApplicationStatusHistoryRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class AttachmentService {
  private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
  private static final long MAX_DECODED_PIXELS = 1_000_000L;
  private static final long MAX_SOURCE_PIXELS = 100_000_000L;
  private static final int MAX_DIMENSION = 20_000;
  private final InspectionApplicationRepository applications;
  private final ApplicationAttachmentRepository attachments;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final InspectionRepository inspections;
  private final ApplicationStatusHistoryRepository histories;
  private final TransactionTemplate transactions;

  public AttachmentService(
      InspectionApplicationRepository applications,
      ApplicationAttachmentRepository attachments,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      InspectionRepository inspections,
      ApplicationStatusHistoryRepository histories,
      PlatformTransactionManager transactionManager) {
    this.applications = applications;
    this.attachments = attachments;
    this.storage = storage;
    this.cleanup = cleanup;
    this.inspections = inspections;
    this.histories = histories;
    this.transactions = new TransactionTemplate(transactionManager);
    this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public List<AttachmentResult> upload(Long userId, Long applicationId, List<MultipartFile> files) {
    return upload(userId, applicationId, files, null);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public List<AttachmentResult> upload(
      Long userId, Long applicationId, List<MultipartFile> files, String requestKey) {
    if (files == null || files.isEmpty() || files.size() > 5) throw invalid();
    if (requestKey != null && !requestKey.matches("[A-Za-z0-9._:-]{1,128}")) throw invalid();
    String storedKey =
        requestKey == null ? null : digest(requestKey.getBytes(StandardCharsets.UTF_8));
    transactions.executeWithoutResult(
        status -> {
          InspectionApplication application =
              applications
                  .findById(applicationId)
                  .orElseThrow(
                      () ->
                          new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
          authorize(application, userId);
          if (storedKey == null
              && attachments.countByApplicationId(applicationId) + files.size() > 5)
            throw invalid();
        });
    List<ValidatedFile> validated = files.stream().map(this::validate).toList();
    String requestHash = storedKey == null ? null : requestHash(validated);
    if (storedKey != null) {
      List<AttachmentResult> previous =
          transactions.execute(status -> findPrevious(applicationId, storedKey, requestHash));
      if (previous != null) return previous;
    }
    List<String> attemptedKeys =
        validated.stream()
            .map(file -> "applications/" + applicationId + "/" + UUID.randomUUID())
            .toList();
    try {
      cleanup.register(attemptedKeys);
      for (int i = 0; i < validated.size(); i++) {
        ValidatedFile file = validated.get(i);
        byte[] bytes = readFile(file.file());
        if (bytes.length != file.size() || !digest(bytes).equals(file.hash())) throw invalid();
        storage.upload(attemptedKeys.get(i), bytes, file.contentType());
      }
      List<AttachmentResult> result =
          transactions.execute(
              status -> {
                for (String key : attemptedKeys) cleanup.lock(key);
                InspectionApplication application =
                    applications
                        .findForAttachmentUpload(applicationId)
                        .orElseThrow(
                            () ->
                                new BaseException(
                                    InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
                authorize(application, userId);
                if (storedKey != null) {
                  List<AttachmentResult> previous =
                      findPrevious(applicationId, storedKey, requestHash);
                  if (previous != null) return previous;
                }
                if (attachments.countByApplicationId(applicationId) + files.size() > 5)
                  throw invalid();
                List<AttachmentResult> saved = new ArrayList<>();
                for (int i = 0; i < validated.size(); i++) {
                  ValidatedFile file = validated.get(i);
                  String key = attemptedKeys.get(i);
                  ApplicationAttachment attachment =
                      attachments.saveAndFlush(
                          ApplicationAttachment.create(
                              application,
                              key,
                              file.filename(),
                              file.size(),
                              file.contentType(),
                              storedKey,
                              requestHash));
                  saved.add(
                      new AttachmentResult(
                          attachment.getId(), file.filename(), file.size(), file.contentType()));
                  cleanup.completed(key);
                }
                if (application.getStatus() == InspectionApplicationStatus.DRAFT) {
                  application.receive();
                  histories.save(ApplicationStatusHistory.received(application));
                  inspections.saveAndFlush(Inspection.received(application));
                }
                return saved;
              });
      cleanup.reconcile(attemptedKeys);
      return result;
    } catch (RuntimeException e) {
      cleanup.reconcile(attemptedKeys);
      if (e instanceof BaseException baseException) throw baseException;
      log.error("사진 등록 실패", e);
      throw new BaseException(InspectionApplicationErrorCode.ATTACHMENT_UPLOAD_FAILED);
    }
  }

  private List<AttachmentResult> findPrevious(Long applicationId, String key, String hash) {
    List<ApplicationAttachment> previous =
        attachments.findAllByApplicationIdAndUploadRequestKeyOrderByIdAsc(applicationId, key);
    if (previous.isEmpty()) return null;
    if (previous.stream().anyMatch(attachment -> !hash.equals(attachment.getUploadRequestHash()))) {
      throw new BaseException(InspectionApplicationErrorCode.ATTACHMENT_IDEMPOTENCY_CONFLICT);
    }
    return previous.stream()
        .map(
            attachment ->
                new AttachmentResult(
                    attachment.getId(),
                    attachment.getOriginalFilename(),
                    attachment.getFileSize(),
                    attachment.getContentType()))
        .toList();
  }

  private String requestHash(List<ValidatedFile> files) {
    MessageDigest digest = sha256();
    for (ValidatedFile file : files) {
      digest.update(file.filename().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(file.hash().getBytes(StandardCharsets.US_ASCII));
      digest.update((byte) 0);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private String digest(byte[] bytes) {
    return HexFormat.of().formatHex(sha256().digest(bytes));
  }

  private MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private void authorize(InspectionApplication application, Long userId) {
    if (application.getApplicant().getRole() != UserRole.RESIDENT
        || !application.getApplicant().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_ACCESS_DENIED);
    }
  }

  private byte[] readFile(MultipartFile file) {
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes((int) MAX_FILE_SIZE + 1);
      if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE) throw invalid();
      return bytes;
    } catch (IOException e) {
      throw invalid();
    }
  }

  private ValidatedFile validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) throw invalid();
    String filename = file.getOriginalFilename();
    if (filename == null
        || filename.isBlank()
        || filename.length() > 255
        || filename.contains("/")
        || filename.contains("\\")
        || filename.chars().anyMatch(Character::isISOControl)) throw invalid();
    String lower = filename.toLowerCase(Locale.ROOT);
    boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    boolean png = lower.endsWith(".png");
    if (!jpeg && !png) throw invalid();
    try {
      byte[] bytes = readFile(file);
      try (ImageInputStream input =
          ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        if (input == null) throw invalid();
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        ImageReader reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName();
          if (!(jpeg && (format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")))
              && !(png && format.equalsIgnoreCase("png"))) throw invalid();
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION)
            throw invalid();
          long pixels = (long) width * height;
          if (pixels > MAX_SOURCE_PIXELS) throw invalid();
          int subsampling =
              Math.max(1, (int) Math.ceil(Math.sqrt((double) pixels / MAX_DECODED_PIXELS)));
          while (((width + (long) subsampling - 1) / subsampling)
                  * ((height + (long) subsampling - 1) / subsampling)
              > MAX_DECODED_PIXELS) {
            subsampling *= 2;
          }
          var parameters = reader.getDefaultReadParam();
          parameters.setSourceSubsampling(subsampling, subsampling, 0, 0);
          if (reader.read(0, parameters) == null) throw invalid();
        } finally {
          reader.dispose();
        }
      }
      return new ValidatedFile(
          filename, file, bytes.length, jpeg ? "image/jpeg" : "image/png", digest(bytes));
    } catch (IOException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  private BaseException invalid() {
    return new BaseException(InspectionApplicationErrorCode.INVALID_ATTACHMENTS);
  }

  private record ValidatedFile(
      String filename, MultipartFile file, long size, String contentType, String hash) {}

  public record AttachmentResult(
      Long attachmentId, String filename, long size, String contentType) {}
}
