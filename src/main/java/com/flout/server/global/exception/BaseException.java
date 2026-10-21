package com.flout.server.global.exception;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BaseException extends RuntimeException {

  private final BaseResponseCode baseResponseCode;
}
