package com.flout.server.domain.inspectionapplication.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class AttachmentCleanupService {
  private final JdbcTemplate jdbc;
  private final AttachmentStorage storage;
  private final TransactionTemplate transactions;

  public AttachmentCleanupService(
      JdbcTemplate jdbc, AttachmentStorage storage, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.transactions = new TransactionTemplate(manager);
    this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void register(List<String> keys) {
    transactions.executeWithoutResult(
        status -> {
          for (String key : keys) {
            jdbc.update(
                "INSERT INTO attachment_upload_intents (object_key, created_at) VALUES (?, ?)",
                key,
                LocalDateTime.now());
          }
        });
  }

  // Called inside the upload transaction. A reconciler must wait for its outcome.
  public void lock(String key) {
    if (jdbc.queryForList(
            "SELECT object_key FROM attachment_upload_intents WHERE object_key = ? FOR UPDATE",
            String.class,
            key)
        .isEmpty()) {
      throw new IllegalStateException("Upload intent no longer exists");
    }
  }

  // Removing the intent and saving the attachment commit atomically.
  public void completed(String key) {
    jdbc.update("DELETE FROM attachment_upload_intents WHERE object_key = ?", key);
  }

  public void reconcile(List<String> keys) {
    for (String key : keys) {
      try {
        transactions.executeWithoutResult(
            status -> {
              var created =
                  jdbc.query(
                      "SELECT created_at FROM attachment_upload_intents WHERE object_key = ? FOR UPDATE",
                      (row, index) -> row.getTimestamp(1).toLocalDateTime(),
                      key);
              if (created.isEmpty()) return;
              Long count =
                  jdbc.queryForObject(
                      "SELECT (SELECT COUNT(*) FROM application_attachments WHERE object_key = ?)"
                          + " + (SELECT COUNT(*) FROM inspection_result_photos WHERE object_key = ?)",
                      Long.class,
                      key,
                      key);
              if (count == 0) {
                storage.delete(key);
                // A timed-out PUT may finish after the first DELETE. Repeat after the grace period.
                if (created.getFirst().isAfter(LocalDateTime.now().minusHours(1))) return;
              }
              jdbc.update("DELETE FROM attachment_upload_intents WHERE object_key = ?", key);
            });
      } catch (RuntimeException e) {
        log.error("첨부 객체 정리 실패, 재시도 예정: {}, failureType={}", key, e.getClass().getSimpleName());
        try {
          transactions.executeWithoutResult(
              status ->
                  jdbc.update(
                      "UPDATE attachment_upload_intents SET created_at = ? WHERE object_key = ?",
                      LocalDateTime.now(),
                      key));
        } catch (RuntimeException retryFailure) {
          log.error(
              "첨부 객체 재시도 시간 갱신 실패: {}, failureType={}",
              key,
              retryFailure.getClass().getSimpleName());
        }
      }
    }
  }

  @Scheduled(
      scheduler = "attachmentCleanupScheduler",
      initialDelayString = "${app.s3.cleanup-interval-millis:300000}",
      fixedDelayString = "${app.s3.cleanup-interval-millis:300000}")
  public void reconcileStale() {
    try {
      List<String> keys =
          jdbc.queryForList(
              "SELECT object_key FROM attachment_upload_intents WHERE created_at < ? ORDER BY created_at LIMIT 100",
              String.class,
              LocalDateTime.now().minusHours(1));
      reconcile(keys);
    } catch (RuntimeException e) {
      log.error("첨부 객체 정리 목록 조회 실패", e);
    }
  }
}
