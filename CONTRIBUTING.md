# Contributing

## 작업 순서

1. GitHub Issue를 생성하고 번호를 확인합니다. 생성된 Issue 제목에는 `[FLOUT-{번호}]`가 자동으로 붙습니다.
2. 최신 `dev`에서 작업 브랜치를 생성합니다.
3. 구현과 테스트를 완료한 뒤 원격 브랜치로 Push합니다.
4. `dev`를 대상으로 Pull Request를 생성합니다.
5. CI와 리뷰를 확인합니다.
6. 사람 승인 1명 이상을 받고 모든 Review Conversation을 해결합니다.
7. Squash merge 후 작업 브랜치를 삭제합니다.

## 브랜치 이름

```text
{type}/FLOUT-{github-issue-number}
```

허용되는 type은 다음과 같습니다.

- `feat`: 새로운 기능
- `fix`: 버그 및 오류 수정
- `hotfix`: 운영 또는 배포 긴급 수정
- `chore`: 설정, 의존성, 패키지 구성 등 작은 변경
- `delete`: 불필요한 코드나 파일 삭제
- `docs`: 문서 및 주석 변경
- `refactor`: 기능 변경 없는 코드 구조 개선

예시:

```text
feat/FLOUT-12
fix/FLOUT-18
chore/FLOUT-1
```

## Issue

Issue 제목은 다음 형식을 사용합니다.

```text
[FLOUT-이슈번호] [TYPE] 작업 요약
```

Issue 양식에서 TYPE을 선택하여 작성하면 GitHub Actions가 발급된 Issue 번호를 제목 앞에 자동으로 추가합니다.

예시:

```text
[FLOUT-3] [FEAT] 회원가입 API 구현
```

## Pull Request

PR 제목은 다음 형식을 사용합니다.

```text
[TYPE] #이슈번호 작업 요약
```

예시:

```text
[FEAT] #12 회원가입 API 구현
```

PR 본문에는 관련 Issue가 병합 시 종료되도록 다음 문구를 포함합니다.

```text
Closes #12
```

## Swagger / OpenAPI 문서화 규칙

API를 추가하거나 변경할 때 구현 코드와 Swagger 문서를 같은 PR에서 함께 수정합니다.

### 필수 문서 항목

| 대상 | 필수 내용 |
| --- | --- |
| Controller | `@Tag`의 기능명과 설명 |
| Endpoint | `@Operation`의 요약과 상세 설명 |
| Request DTO | 각 필드의 설명, 형식, 예시 및 허용값 |
| Path·Query·Header | 파라미터의 용도, 예시 및 필수 여부 |
| 성공 응답 | 실제 HTTP 상태, 응답 코드, 메시지 및 응답 본문 예시 |
| 오류 응답 | 해당 API에서 발생 가능한 도메인 오류의 HTTP 상태, 오류 코드 및 메시지 |
| 인증 | 인증 필요 여부와 필요한 역할 |

### 작성 원칙

- 요약은 `무엇을 하는 API인지` 한 문장으로 작성하고, 상세 설명에는 제약 조건과
  처리 규칙을 작성합니다.
- 요청과 응답 예시는 실제 스키마와 일치해야 하며 실사용 개인정보, 토큰 및 비밀번호를
  사용하지 않습니다.
- 날짜·시간, enum, 이메일, 비밀번호 등 형식이 있는 값은 `format`, `allowableValues`,
  `example`을 명시합니다.
- DTO 필드는 `@Schema`, Path·Query·Header 파라미터는 `@Parameter`를 사용합니다.
  응답 조건이 여러 개라면 `@ApiResponses` 또는 공통 `OpenApiConfig`에서 관리합니다.
- 공통 응답 envelope인 `isSuccess`, `status`, `code`, `message`, `result`, `timeStamp`를
  기준으로 예시를 작성합니다.
- 공통 인증·인가·서버 오류는 `OpenApiConfig`에서 관리하며 Controller마다 중복
  선언하지 않습니다. API 고유의 성공 응답과 도메인 오류만 추가합니다.
- 전역 Bearer 인증이 기본값입니다. 공개 API를 추가할 때는 Security 설정과 Swagger의
  공개 경로 설정을 함께 변경하고 인증 없이 접근 가능한지 테스트합니다.
- API 구현을 변경했다면 `/swagger-ui/index.html`에서 설명과 예시를 확인하고,
  `/v3/api-docs`가 정상 생성되는지 검증합니다.

### 기본 예시

```java
@Tag(name = "회원", description = "회원 가입 및 계정 관리")
@RestController
public class UserController {

  @Operation(summary = "회원 가입", description = "필수 약관에 동의한 주민 계정을 생성합니다.")
  public SuccessResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    // ...
  }
}
```
