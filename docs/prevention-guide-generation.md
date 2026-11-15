# 맞춤 예방 가이드 생성 API

## 주민 API

`POST /api/resident/risk-assessments/{assessmentId}/guide`

본인의 완료된 취약도 분석 결과에서 지역 상대 취약도 등급과 거주층만 사용해 체크리스트
4개를 생성한다. 주소, 좌표, 상세주소, 모델 점수, SHAP 근거는 Gemini에 보내지 않는다.
같은 분석 결과에는 가이드를 한 번만 저장한다. `AI` 가이드는 그대로 재사용하고,
`DEFAULT` 가이드는 후속 요청에서 Gemini를 다시 호출해 서비스가 복구되면 같은 `guideId`의
내용을 `AI` 결과로 교체한다.

반지하와 1층만 지원한다. 2층 이상은 앱의 고정 가이드 화면을 사용하므로 이 API를 호출하지
않는다.

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "GUIDE_200_1",
  "message": "맞춤 예방 가이드를 조회했습니다.",
  "result": {
    "guideId": 1,
    "assessmentId": 1,
    "source": "AI",
    "grade": "HIGH",
    "title": "이것부터 확인하세요",
    "subtitle": "반지하는 대피 요령부터 확인해 주세요.",
    "completedCount": 0,
    "totalCount": 4,
    "items": [
      {
        "order": 1,
        "ruleId": "BASEMENT_EVAC",
        "title": "반지하 대피 요령 확인",
        "description": "물이 차거나 역류하면 즉시 대피하고 출입문부터 열어 주세요.",
        "completed": false
      }
    ],
    "generatedAt": "2026-11-07T12:00:00+09:00"
  }
}
```

- `source=AI`: Gemini 응답이 서버 검증을 통과했다.
- `source=DEFAULT`: Gemini 미설정, 타임아웃, 호출 오류 또는 응답 검증 실패로 검수된 기본
  체크리스트를 사용했다. 다음 조회에서는 Gemini 생성을 재시도한다.
- `grade`는 취약도 분석 모델의 결과를 그대로 반환한다. Gemini가 등급을 다시 판단하지
  않는다.
- 예상 시간은 응답하지 않는다. 생성 직후 모든 항목의 `completed`는 `false`다.

## 우선순위

| 거주층 | 등급 | 수칙 ID 순서 |
| --- | --- | --- |
| 반지하 | HIGH | `BASEMENT_EVAC`, `BASEMENT_TOOLS`, `BARRIER`, `POWER` |
| 반지하 | CAUTION | `BASEMENT_EVAC`, `BARRIER`, `DRAIN`, `PLAN` |
| 반지하 | LOW | `BASEMENT_EVAC`, `DRAIN`, `ALERT`, `PLAN` |
| 1층 | HIGH | `BARRIER`, `DRAIN`, `POWER`, `PLAN` |
| 1층 | CAUTION | `DRAIN`, `BARRIER`, `ALERT`, `PLAN` |
| 1층 | LOW | `DRAIN`, `ALERT`, `PLAN`, `KIT` |

Gemini에는 해당 조합의 고정된 수칙 4개만 전달한다. 응답의 ID와 순서가 위 표와 다르거나,
항목 수·문구 길이·금지 표현 규칙을 위반하면 결과를 폐기하고 기본 체크리스트를 사용한다.

## Gemini 연동

Spring 서버가 Gemini `generateContent` REST API를 직접 호출한다. API 키는
`x-goog-api-key` 헤더에만 넣으며 로그나 데이터베이스에 저장하지 않는다. JSON Schema 기반
구조화 출력을 요청한 뒤 애플리케이션에서 의미 규칙을 다시 검증한다.

환경 변수:

- `GEMINI_API_KEY` (필수, 저장소에 커밋하지 않음)
- `GEMINI_MODEL` (기본값: `gemini-3.5-flash-lite`)
- `GEMINI_BASE_URL` (기본값: `https://generativelanguage.googleapis.com`)
- `GEMINI_CONNECT_TIMEOUT_MILLIS` (기본값: `3000`)
- `GEMINI_READ_TIMEOUT_MILLIS` (기본값: `15000`)

로컬 실행 예시:

```shell
export GEMINI_API_KEY='발급받은-키'
./gradlew bootRun
```

키는 `.env`, `application.yml`, 테스트 코드 또는 문서에 실제 값으로 기록하지 않는다.
