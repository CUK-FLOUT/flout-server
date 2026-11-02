package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.global.exception.BaseException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import software.amazon.awssdk.services.s3.S3Client;

class AttachmentServiceTest {
  @Test
  void rejectsOversizedDimensionsAndPixelCountBeforeDecoding() throws Exception {
    var applications = mock(InspectionApplicationRepository.class);
    var attachments = mock(ApplicationAttachmentRepository.class);
    var storage = mock(AttachmentStorage.class);
    var cleanup = mock(AttachmentCleanupService.class);
    var manager = mock(PlatformTransactionManager.class);
    var application = mock(InspectionApplication.class);
    var user = User.resident("owner@example.com", "hash", "owner");
    ReflectionTestUtils.setField(user, "id", 1L);
    when(application.getApplicant()).thenReturn(user);
    when(applications.findById(10L)).thenReturn(Optional.of(application));
    when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    var service =
        new AttachmentService(
            applications,
            attachments,
            storage,
            cleanup,
            mock(com.flout.server.domain.inspection.repository.InspectionRepository.class),
            mock(
                com.flout.server.domain.inspectionapplication.repository
                    .ApplicationStatusHistoryRepository.class),
            manager);
    var data = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", data);
    for (int[] dimensions :
        List.of(new int[] {20001, 1}, new int[] {10001, 10000}, new int[] {50000, 50000})) {
      byte[] bytes = data.toByteArray();
      ByteBuffer.wrap(bytes).putInt(16, dimensions[0]).putInt(20, dimensions[1]);
      var file = new MockMultipartFile("files", "photo.png", "image/png", bytes);
      assertThatThrownBy(() -> service.upload(1L, 10L, List.of(file)))
          .isInstanceOfSatisfying(
              BaseException.class,
              e ->
                  assertThat(e.getBaseResponseCode())
                      .isEqualTo(InspectionApplicationErrorCode.INVALID_ATTACHMENTS));
    }
    verifyNoInteractions(storage, cleanup);
  }

  @Test
  void reusesAndClosesSharedS3Client() {
    var storage = new S3AttachmentStorage("ap-northeast-2", "bucket");
    var client = mock(S3Client.class);
    ReflectionTestUtils.setField(storage, "client", client);
    storage.upload("one", new byte[] {1}, "image/png");
    storage.delete("one");
    verify(client)
        .putObject(
            any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            any(software.amazon.awssdk.core.sync.RequestBody.class));
    verify(client)
        .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    verify(client, never()).close();
    storage.close();
    verify(client).close();
    assertThatThrownBy(() -> storage.delete("one")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void missingS3ConfigurationFailsOnlyWhenStorageIsUsed() {
    var storage = new S3AttachmentStorage("", "");
    assertThatThrownBy(() -> storage.upload("key", new byte[] {1}, "image/png"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWS_REGION");
  }

  @Test
  void cleansUploadedObjectWhenCommitFails() throws Exception {
    var applications = mock(InspectionApplicationRepository.class);
    var attachments = mock(ApplicationAttachmentRepository.class);
    var storage = mock(AttachmentStorage.class);
    var cleanup = mock(AttachmentCleanupService.class);
    var manager = mock(PlatformTransactionManager.class);
    var transaction = mock(TransactionStatus.class);
    var application = mock(InspectionApplication.class);
    var user = User.resident("owner@example.com", "hash", "주민");
    ReflectionTestUtils.setField(user, "id", 1L);
    when(application.getApplicant()).thenReturn(user);
    when(applications.findById(10L)).thenReturn(Optional.of(application));
    when(applications.findForAttachmentUpload(10L)).thenReturn(Optional.of(application));
    when(manager.getTransaction(any())).thenReturn(transaction);
    when(attachments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    doNothing()
        .doThrow(new IllegalStateException("commit failure"))
        .when(manager)
        .commit(transaction);
    var data = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", data);
    var file = new MockMultipartFile("files", "photo.png", "image/png", data.toByteArray());

    var service =
        new AttachmentService(
            applications,
            attachments,
            storage,
            cleanup,
            mock(com.flout.server.domain.inspection.repository.InspectionRepository.class),
            mock(
                com.flout.server.domain.inspectionapplication.repository
                    .ApplicationStatusHistoryRepository.class),
            manager);
    assertThatThrownBy(() -> service.upload(1L, 10L, List.of(file)))
        .isInstanceOf(BaseException.class);
    var key = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(storage).upload(key.capture(), any(), eq("image/png"));
    verify(cleanup).reconcile(List.of(key.getValue()));
  }
}
