# 맞춤 예방 가이드 생성 API

## 주민 API

`POST /api/resident/risk-assessments/{assessmentId}/guide`

본인의 완료된 취약도 분석 결과에서 지역 상대 취약도 등급과 거주층만 사용해 체크리스트
4개를 생성한다. 주소, 좌표, 상세주소, 모델 점수, SHAP 근거는 Gemini에 보내지 않는다.
같은 분석 결과에는 가이드를 한 번만 저장한다. `AI` 가이드는 그대로 재사용하고,
`DEFAULT` 가이드는 해결 여부나 항목 진행이 기록되지 않은 경우 후속 요청에서 Gemini 생성을
재시도한다. 생성 중 기존 내용이 바뀌지 않았고 검증된 AI 결과의 수칙 ID와 순서가 기존과
일치하면 같은 `guideId`의 내용을 교체한다. 해결 여부 또는 항목 진행이 기록된 가이드는
보호하여 재생성하지 않는다.

반지하와 1층만 지원한다. 저장된 거주층이 2층 이상이거나 미입력이면
`400 / GUIDE_400_1`을 반환한다.

다음은 응답 필드 예시이며 `items`는 첫 항목만 발췌했다. 실제 목록은 4개다.

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
        "itemId": 1,
        "order": 1,
        "ruleId": "BASEMENT_EVAC",
        "title": "반지하 대피 요령 확인",
        "description": "물이 차거나 역류하면 즉시 대피하고 출입문부터 열어 주세요.",
        "completed": false
      }
    ],
    "resolution": null,
    "generatedAt": "2026-11-07T12:00:00+09:00"
  },
  "timeStamp": "2026-11-07 12:00:00"
}
```

- `source=AI`: Gemini 응답이 서버 검증을 통과했다.
- `source=DEFAULT`: Gemini 미설정, 타임아웃, 호출 오류 또는 응답 검증 실패로 검수된 기본
  체크리스트를 사용했다. 진행·해결 기록이 없는 경우 후속 생성 요청에서 재시도한다.
- `grade`는 취약도 분석 모델의 결과를 그대로 반환한다. Gemini가 등급을 다시 판단하지
  않는다.
- 예상 시간은 응답하지 않는다. 생성 직후 모든 항목의 `completed`는 `false`다.
- `itemId`는 항목별 진행 상태 변경에 사용하며 `resolution`은 최종 선택 전 null이다.

## 상세 조회와 진행 상태

로그인한 주민은 본인 가이드에 대해 다음 API를 사용할 수 있다. 모두 갱신된 전체
`PreventionGuideResponse`를 반환한다.

- `GET /api/resident/prevention-guides/{guideId}`: 가이드와 진행 상태 조회. 이전 데이터에 항목 상태가 없으면 조회 시 생성한다.
- `PATCH /api/resident/guide-items/{itemId}/progress`: 필수 Boolean `completed`를 true 또는 false로 저장한다. 최종 선택은 변경하지 않는다.
- `PATCH /api/resident/prevention-guides/{guideId}/resolution`: 필수 `resolution`으로 `CONFIRMED` 또는 `NEEDS_INSPECTION`을 저장한다. 현장점검 신청을 자동 생성하지 않는다.

양수가 아닌 ID는 `400 / COMMON_400_3`, 존재하지 않는 가이드 또는 항목은 각각
`GUIDE_404_2`, `GUIDE_404_3`, 다른 주민의 가이드는 `403 / GUIDE_403_2`이다. 저장된
내용을 읽지 못하거나 항목 수가 맞지 않으면 `500 / GUIDE_500_1`이다.

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

- `GEMINI_API_KEY` (AI 생성에 필요, 저장소에 커밋하지 않음. 미설정 시 기본 가이드 사용)
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
