# Google 로그인

## 설정

Google Cloud에서 웹 클라이언트 ID를 만들고 프론트의 허용된 JavaScript 원본을 등록합니다.
서버에는 `GOOGLE_CLIENT_IDS` 환경변수로 허용할 클라이언트 ID를 지정합니다.
여러 개면 쉼표로 구분합니다. 빈 값이면 Google 로그인은 503으로 거절하며 기존 이메일 로그인은 유지합니다.
Google Identity Services의 ID 토큰을 HTTPS로 서버에 전달합니다. Google Access Token은 받지 않습니다.
ID 토큰 방식이므로 서버의 Google client secret은 필요하지 않습니다.

## API

### 프론트 없는 로컬 테스트

`local` 프로필로 서버를 실행하고 `http://localhost:8080/dev/google-login`을 엽니다.
Google Cloud의 승인된 JavaScript 원본에는 `http://localhost`와 `http://localhost:8080`을 등록합니다.
별도 프론트 서버 없이 Google 버튼으로 ID 토큰을 받은 다음, 주민/운영자 API를 직접 테스트하거나
ID 토큰을 복사해 Swagger에서 사용할 수 있습니다. 신규 주민 가입 시 두 동의 항목을 직접 선택합니다.
테스트 화면은 local 프로필에서만 제공하며 토큰은 브라우저 저장소에 저장하지 않습니다.

- 주민: `POST /api/auth/google`
- 운영자: `POST /api/auth/admin/google`
- 인증 헤더 불필요, Content-Type: application/json

```json
{
  "idToken": "example-google-id-token",
  "thirdPartyProvisionAgreed": true,
  "statusNotificationAgreed": true
}
```

동의 값은 신규 주민 Google 가입 시에만 필수입니다. 개인정보 제3자 제공과 진행상태 알림 수신에
모두 명시적으로 동의해야 하며, 하나라도 누락하거나 false이면 `USER_400_3`을 반환하며 가입하지 않습니다.
각각 `THIRD_PARTY_PROVISION`, `STATUS_NOTIFICATION` 유형으로 동의 시각과 버전을 저장합니다.
동의 버전은 `THIRD_PARTY_PROVISION_VERSION`, `STATUS_NOTIFICATION_VERSION`으로 설정하며 기본값은 1.0입니다.
기존 이메일 회원가입의 TERMS/PRIVACY 처리와 기존 동의 이력은 변경하지 않습니다.
프론트는 동의 화면에서 받은 값만 보내야 합니다. 기존 Google 회원과 운영자는 생략합니다.
성공 응답은 기존 이메일 로그인과 동일한 `LoginResponse`로, 서비스 Access Token만 발급합니다.
현재 서비스에는 Refresh Token 발급 기능이 없습니다.

## 주민

Google `sub`로 회원을 식별합니다. 미등록 계정은 RESIDENT로만 생성하고 동의를 함께 저장합니다.
이미 이메일 회원가입한 주소는 자동 연결하지 않고 `USER_409_2`를 반환합니다.
비밀번호 재인증 등을 거치는 별도 계정 연결 기능은 이 작업에 포함하지 않습니다.
Google 전용 회원에는 알 수 없는 임의 비밀번호의 해시를 저장하여 기존 스키마를 유지합니다.

## 운영자 사전 등록

운영자 로그인은 계정을 생성하거나 주민을 운영자로 승격하지 않습니다.
DB에 `role=ADMIN` 계정을 먼저 등록합니다. 이메일은 소문자로 저장합니다.
`password_hash`는 기존 이메일 로그인용 BCrypt 해시이며 평문 비밀번호를 넣으면 안 됩니다.
이메일 로그인이 불필요하면 안전한 임의 비밀번호의 해시를 사용합니다.

권장 방식은 Google의 검증된 `sub` 값을 `google_subject`에 미리 연결하는 것입니다.
클라이언트가 임의로 전달한 sub나 이메일을 신뢰해서 등록하면 안 됩니다.

`google_subject`가 비어 있는 기존 ADMIN 계정은 Google이 소유권을 보증하는 인증된 Gmail 또는
Workspace 이메일이 등록 주소와 일치할 때만 최초 로그인에서 연결합니다.
일반 외부 이메일의 경우 사전에 sub를 연결해야 합니다. `email_verified=true`만으로 자동 연결하지 않습니다.
한 번 연결된 sub는 다른 Google 계정으로 덮어쓰지 않습니다.

## 오류

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 400 | USER_400_3 | 신규 주민 개인정보 제3자 제공 및 진행상태 알림 수신 동의 필요 |
| 401 | USER_401_2 | Google ID 토큰 검증 실패 |
| 403 | USER_403_1 | 미등록 운영자 또는 역할/Google 계정 불일치 |
| 409 | USER_409_2 | 계정 연결 필요 또는 동시 가입·연결 충돌. 충돌 시 재로그인 |
| 503 | USER_503_1 | GOOGLE_CLIENT_IDS 미설정 |

운영자 계정은 주민 Google API에서 로그인할 수 없고, 주민 계정은 운영자 Google API에서 로그인할 수 없습니다.
DB 변경은 Flyway V4에서 nullable 고유 `google_subject` 컬럼을 추가합니다.
Flyway V5에서는 기존 동의 유형을 유지하면서 Google 가입용 두 동의 유형을 추가합니다.

## 참고

- [Google ID 토큰 검증](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
- [Google 이메일 소유권 확인 기준](https://developers.google.com/identity/sign-in/android/backend-auth)
