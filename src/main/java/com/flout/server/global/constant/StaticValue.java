package com.flout.server.global.constant;

public class StaticValue {

  // 성공
  public static final int OK = 200;
  public static final int CREATED = 201;
  public static final int ACCEPTED = 202;
  public static final int NO_CONTENT = 204;

  // 리다이렉션
  public static final int MOVED_PERMANENTLY = 301;
  public static final int FOUND = 302;
  public static final int SEE_OTHER = 303;
  public static final int NOT_MODIFIED = 304;

  // 클라이언트 오류
  public static final int BAD_REQUEST = 400;
  public static final int UNAUTHORIZED = 401;
  public static final int FORBIDDEN = 403;
  public static final int NOT_FOUND = 404;
  public static final int METHOD_NOT_ALLOWED = 405;
  public static final int CONFLICT = 409;

  // 서버 오류
  public static final int INTERNAL_SERVER_ERROR = 500;
  public static final int BAD_GATEWAY = 502;
  public static final int SERVICE_UNAVAILABLE = 503;

  private StaticValue() {}
}
