package com.flout.server.domain.inspectionapplication.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KoreanMobileNumberValidator.class)
public @interface KoreanMobileNumber {
  String message() default "연락처는 국내 휴대전화 형식이어야 합니다. 예: 010-1234-5678 또는 01012345678.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
