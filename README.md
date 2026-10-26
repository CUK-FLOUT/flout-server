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

## AI 취약도 분석 모델

수집된 데이터를 기반으로 `HIGH`, `CAUTION`, `LOW` 취약도 등급을 예측하고,
Tree 계열과 Boosting 계열 모델을 학습 데이터 내 교차 검증으로 비교합니다. 선정된
최종 모델은 그전까지 사용하지 않은 홀드아웃 테스트 데이터에서 한 번 평가합니다.

자세한 내용은 [AI_MODEL_PIPELINE.md](docs/AI_MODEL_PIPELINE.md)을 확인해 주세요.

## 로컬 실행

최초 실행 시 로컬 환경변수 파일을 생성합니다.

```bash
cp .env.example .env
```

주소 검색 API를 사용하려면 `.env`의 `KAKAO_REST_API_KEY`에 Kakao Developers에서 발급한 REST API 키를 설정합니다. `.env`는 Git에서 제외되므로 실제 키를 커밋하지 않습니다.

이후 실행 스크립트를 사용하면 `.env`를 환경변수로 로드하고 MySQL이 준비된 다음
Spring Boot 서버가 시작됩니다.

```bash
./scripts/run-local.sh
```

- 서버를 종료하려면 실행 중인 터미널에서 `Ctrl+C`를 누릅니다.
- MySQL 컨테이너까지 종료하려면 `docker compose down`을 실행합니다.
- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Windows IntelliJ 실행

1. Docker Desktop에서 MySQL을 먼저 실행합니다. Git Bash에서는 `./scripts/run-local.sh`로 `.env`를 포함해 실행할 수 있습니다.
2. IntelliJ에서는 `Run` → `Edit Configurations...` → Spring Boot 실행 구성의 `Environment variables`에 `.env`의 DB 설정값과 `KAKAO_REST_API_KEY`를 추가합니다.
3. `Active profiles`는 `local`로 유지하고 `FloutServerApplication`을 실행합니다.

`KAKAO_REST_API_KEY`에는 실제 REST API 키를 입력하며, `.env.example`의 예시 값은 사용하지 않습니다.

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
