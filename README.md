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

자세한 내용은 [AI 취약도 분석 모델 파이프라인](docs/AI_MODEL_PIPELINE.md)을 확인해 주세요.

## 로컬 실행

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

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
