package com.flout.server.domain.residentprogress;

import com.flout.server.domain.inspectionapplication.controller.InspectionApplicationController;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.supportapplication.controller.SupportApplicationController;
import com.flout.server.domain.supportapplication.dto.request.CreateSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.CreateSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.exception.GlobalExceptionHandler;
import com.flout.server.global.response.ErrorResponse;
import com.flout.server.global.response.SuccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recovers an already committed response without relaxing validation for new submissions. */
@RestControllerAdvice(
    assignableTypes = {InspectionApplicationController.class, SupportApplicationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExpiredSubmissionRetryAdvice {
  private final FormDraftService drafts;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> invalid(MethodArgumentNotValidException e, HttpServletRequest http) {
    String header = http.getHeader("X-Form-Draft-Id");
    boolean onlyExpiredDate =
        !e.getAllErrors().isEmpty()
            && e.getAllErrors().stream()
                .allMatch(
                    error ->
                        error instanceof FieldError field
                            && "preferredVisitDate".equals(field.getField())
                            && field.getCodes() != null
                            && Arrays.asList(field.getCodes()).contains("FutureOrPresent"));
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (header == null || !onlyExpiredDate || !(auth instanceof JwtAuthenticationToken jwt))
      return new GlobalExceptionHandler().handleMethodArgumentNotValidException(e);
    UUID draftId;
    Long userId;
    try {
      draftId = UUID.fromString(header);
      userId = Long.valueOf(jwt.getToken().getSubject());
    } catch (IllegalArgumentException ex) {
      return new GlobalExceptionHandler().handleMethodArgumentNotValidException(e);
    }
    try {
      Object body = e.getBindingResult().getTarget();
      if (body instanceof CreateInspectionApplicationRequest request) {
        var normalized =
            new CreateInspectionApplicationRequest(
                request.assessmentId(),
                request.guideId(),
                request.address().strip(),
                request.detailAddress(),
                request.latitude(),
                request.longitude(),
                request.contact(),
                request.occupancyType(),
                request.floodExperience(),
                request.existingFacility(),
                request.requestReason(),
                request.preferredVisitDate(),
                request.preferredTimeSlot(),
                request.inflowLocations(),
                request.applicantName());
        var response =
            drafts.recoverExpiredSubmission(
                userId,
                "INSPECTION",
                draftId,
                request.assessmentId(),
                normalized,
                CreateInspectionApplicationResponse.class);
        if (response != null)
          return ResponseEntity.status(201)
              .body(
                  SuccessResponse.of(
                      response, InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED));
      } else if (body instanceof CreateSupportApplicationRequest request) {
        var response =
            drafts.recoverExpiredSubmission(
                userId,
                "SUPPORT",
                draftId,
                request.applicationId(),
                request,
                CreateSupportApplicationResponse.class);
        if (response != null)
          return ResponseEntity.status(201)
              .body(
                  SuccessResponse.of(
                      response,
                      response.status() == SupportApplicationStatus.DRAFT
                          ? SupportApplicationSuccessCode.DRAFT_CREATED
                          : SupportApplicationSuccessCode.SUBMITTED));
      }
    } catch (BaseException conflict) {
      var code = conflict.getBaseResponseCode();
      return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
    }
    return new GlobalExceptionHandler().handleMethodArgumentNotValidException(e);
  }
}
