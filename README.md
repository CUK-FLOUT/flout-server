# FLOUT Server

FLOUT 공통 백엔드 서버 및 API입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.1
- Gradle
- Spring Web MVC
- Spring Data JPA
- MySQL 8.4
- Flyway
- Springdoc OpenAPI
- Spring Boot Actuator

## 로컬 실행

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 주민 회원가입

`POST /api/auth/signup`

```json
{
  "email": "resident@example.com",
  "password": "password123",
  "passwordConfirm": "password123",
  "name": "홍길동",
  "termsAgreed": true,
  "privacyAgreed": true
}
```

- 비밀번호: 영문·숫자 포함 8~64자, 공백 불가, UTF-8 72바이트 이하. 특수문자는 선택입니다.
- 이메일은 소문자로 통일하며, 중복 가입은 HTTP 409로 응답합니다.
- 두 필수 약관에 모두 동의해야 가입할 수 있습니다.
- 역할은 서버에서 `RESIDENT`로 지정합니다. 관리자 가입 및 로그인·토큰 발급은 제공하지 않습니다.
- 성공 시 HTTP 201과 공통 응답의 `result`에 `userId`, `email`, `name`, `role`을 반환합니다.
- 성공 코드는 `USER_201_1`, 메시지는 `회원가입이 완료되었습니다.`입니다.
- 비밀번호 불일치는 `USER_400_1`, 바이트 제한 초과는 `USER_400_2`, 이메일 중복은 `USER_409_1`입니다.
- 회원과 약관 동의는 하나의 트랜잭션으로 저장합니다. 동의 버전은 `TERMS_VERSION`, `PRIVACY_VERSION`으로 설정하며 기본값은 `1.0`입니다.
- 서버 시작 시 Flyway가 `users`, `user_consents` 테이블을 생성합니다.

## 테스트와 코드 스타일

```bash
./gradlew test
./gradlew clean build
./gradlew spotlessApply
./gradlew spotlessCheck
```

## 패키지 구조

```text
com.flout.server
├── global
│   ├── config
│   ├── error
│   ├── response
│   └── util
└── {domain}
    ├── controller
    ├── service
    ├── repository
    ├── entity
    └── dto
        ├── request
        └── response
```

## 브랜치 및 PR 규칙

- 브랜치: `{type}/FLOUT-{github-issue-number}`
- 예시: `feat/FLOUT-12`
- PR 제목: `[FEAT] #12 회원가입 API 구현`
- PR 본문에 `Closes #12`를 작성합니다.
- 모든 변경은 PR을 통해 `dev`에 병합합니다.
- `main`에는 원칙적으로 `dev`만 병합합니다.

자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 확인해 주세요.
