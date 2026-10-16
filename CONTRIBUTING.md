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
