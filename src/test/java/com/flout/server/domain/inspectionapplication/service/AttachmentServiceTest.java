package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.inspectionapplication.repository.ApplicationAttachmentRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.global.exception.BaseException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AttachmentServiceTest {
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

    var service = new AttachmentService(applications, attachments, storage, cleanup, manager);
    assertThatThrownBy(() -> service.upload(1L, 10L, List.of(file)))
        .isInstanceOf(BaseException.class);
    var key = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(storage).upload(key.capture(), any(), eq("image/png"));
    verify(cleanup).reconcile(List.of(key.getValue()));
  }
}
