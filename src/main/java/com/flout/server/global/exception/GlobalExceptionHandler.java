package com.flout.server.global.exception;

import com.flout.server.global.response.ErrorResponse;
import com.flout.server.global.response.code.GeneralErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // 요청 본문 검증 실패
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse<?>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException e) {
    log.warn("요청 본문 검증 실패");
    ErrorResponse<?> errorResponse =
        ErrorResponse.of(
            GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
            e.getAllErrors().isEmpty()
                ? GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY.getMessage()
                : e.getAllErrors().getFirst().getDefaultMessage());
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 요청 파라미터 검증 실패
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse<?>> handleBindException(BindException e) {
    log.warn("요청 파라미터 검증 실패");
    ErrorResponse<?> errorResponse =
        ErrorResponse.of(
            GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
            e.getAllErrors().isEmpty()
                ? GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY.getMessage()
                : e.getAllErrors().getFirst().getDefaultMessage());
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 요청 본문 누락 또는 JSON 형식 오류
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse<?>> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e) {
    log.warn("요청 본문 형식 오류");
    ErrorResponse<?> errorResponse = ErrorResponse.from(GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 요청 파라미터 타입 오류
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse<?>> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    log.error("MethodArgumentTypeMismatchException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse =
        ErrorResponse.from(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 필수 쿼리 파라미터 누락
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse<?>> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException e) {
    log.error("MissingServletRequestParameterException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse =
        ErrorResponse.of(
            GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER,
            e.getParameterName() + " 파라미터가 필요합니다.");
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 필수 멀티파트 항목 누락
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse<?>> handleMissingServletRequestPartException(
      MissingServletRequestPartException e) {
    log.error("MissingServletRequestPartException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse =
        ErrorResponse.from(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 지원하지 않는 HTTP 메서드
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse<?>> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    log.error("HttpRequestMethodNotSupportedException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse = ErrorResponse.from(GeneralErrorCode.UNSUPPORTED_HTTP_METHOD);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 엔드포인트 없음
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse<?>> handleNoHandlerFoundException(NoHandlerFoundException e) {
    log.error("NoHandlerFoundException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse = ErrorResponse.from(GeneralErrorCode.NOT_FOUND_ENDPOINT);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 정적 리소스 없음
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse<?>> handleNoResourceFoundException(
      NoResourceFoundException e) {
    log.error("NoResourceFoundException : {}", e.getMessage(), e);
    ErrorResponse<?> errorResponse = ErrorResponse.from(GeneralErrorCode.NOT_FOUND_ENDPOINT);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  // 연결이 종료된 비동기 요청에는 응답하지 않음
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
    log.debug("AsyncRequestNotUsableException : 클라이언트 연결 종료 - {}", e.getMessage());
  }

  // 비즈니스 예외
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ErrorResponse<?>> handleBaseException(
      BaseException e, HttpServletRequest request) {
    log.error("BaseException : {}", e.getBaseResponseCode().getMessage(), e);
    ErrorResponse<?> errorResponse = ErrorResponse.from(e.getBaseResponseCode());

    // SSE 요청은 JSON 본문 없이 상태 코드만 반환
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
      return ResponseEntity.status(errorResponse.getStatus()).build();

    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse<?>> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException e) {
    ErrorResponse<?> response = ErrorResponse.from(GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ErrorResponse<?>> handleHttpMediaTypeNotAcceptableException(
      HttpMediaTypeNotAcceptableException e) {
    ErrorResponse<?> response = ErrorResponse.from(GeneralErrorCode.NOT_ACCEPTABLE_ERROR);
    return ResponseEntity.status(response.getStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse<?>> handleResponseStatusException(ResponseStatusException e) {
    int status = e.getStatusCode().value();
    for (GeneralErrorCode code : GeneralErrorCode.values()) {
      if (code.getStatus() == status) {
        return ResponseEntity.status(status).body(ErrorResponse.from(code));
      }
    }
    // 예외의 상세 사유는 응답에 노출하지 않음
    HttpStatus httpStatus = HttpStatus.resolve(status);
    String message = httpStatus == null ? "요청 처리에 실패했습니다." : httpStatus.getReasonPhrase();
    return ResponseEntity.status(status)
        .body(ErrorResponse.of(status, "COMMON_" + status + "_1", message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse<?>> handleException(Exception e) {
    log.error("Exception : {}", e.getMessage(), e);
    ErrorResponse<Object> errorResponse = ErrorResponse.from(GeneralErrorCode.SERVER_ERROR);
    return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
  }
}
