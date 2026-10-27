# 인증 토큰 재발급

주민·운영자와 이메일·Google 로그인에서 공통으로 사용합니다.

## 구현 범위

이슈 #26과 PR #27은 인증 토큰 재발급만 구현합니다. 로그아웃 API 및 로그아웃 시 Refresh Token을 폐기하는 기능은 포함하지 않습니다.
클라이언트에서 저장한 토큰을 삭제하더라도 서버의 Refresh Token은 무효화되지 않으며, 만료 전까지 재발급에 사용할 수 있습니다.

## 로그인 응답

기존 LoginResponse 필드에 아래 세 필드가 추가됩니다.

```json
{
  "refreshToken": "example-refresh-token",
  "refreshExpiresIn": 604800,
  "refreshExpiresAt": "2026-10-28T13:30:00Z"
}
```

Access Token 기본 유효 기간은 1시간, Refresh Token은 7일입니다.
Refresh Token은 32바이트 난수를 Base64URL로 인코딩한 값이며 DB에는 SHA-256 해시만 저장합니다.
원문 토큰을 로그나 저장소에 기록하지 않습니다.

## POST /api/auth/refresh

Authorization 헤더 없이 JSON 본문으로 요청합니다. 만료된 Access Token을 헤더에 보내면 인증 필터에서 거절되므로 생략합니다.

```json
{
  "refreshToken": "로그인 또는 직전 재발급 응답의 실제 refreshToken"
}
```

성공: 200 / USER_200_2 / 인증 토큰이 재발급되었습니다.
result는 로그인과 동일한 LoginResponse이며 새 Access Token과 새 Refresh Token을 포함합니다.

- 기존 Refresh Token은 한 번만 사용할 수 있고 성공 시 즉시 폐기합니다.
- 동시 재발급 요청은 한 건만 성공합니다. 클라이언트도 재발급 요청을 하나로 합쳐 처리해야 합니다.
- 새 토큰은 현재 DB 계정의 권한으로 발급하며, 요청에 담긴 userId나 role을 신뢰하지 않습니다.
- 재발급 중 오류가 나면 기존 토큰 소비도 함께 롤백합니다.
- 새 Refresh Token의 만료는 재발급 시점부터 다시 계산됩니다. 프론트는 응답받은 토큰 두 개를 함께 교체해야 합니다.
- 미등록·변조·만료·사용 완료 토큰은 모두 401 / USER_401_4를 반환합니다.
- 필수 토큰 누락·공백·잘못된 JSON은 400 / COMMON_400_2를 반환합니다.

## 설정 및 DB

- JWT_ACCESS_TOKEN_SECONDS: 기본 3600초
- JWT_REFRESH_TOKEN_SECONDS: 기본 604800초, 허용 범위 60~7776000초
- Flyway V7에서 refresh_tokens 테이블을 추가합니다.
- 회원 삭제 시 해당 회원의 Refresh Token도 함께 삭제합니다.
- 만료된 행은 인증에 사용할 수 없으며, 오래된 만료 행의 물리적 정리는 운영 DB 유지보수 대상으로 관리합니다.
- 기존 로그인에서 받은 Access Token은 변경 없이 사용할 수 있지만 Refresh Token이 없으면 한 번 다시 로그인해야 합니다.

브라우저에서 토큰을 보관하는 방법은 프론트와 별도로 합의해야 합니다. 이 API는 쿠키를 자동 설정하지 않으며 JSON 본문으로 전달합니다.
