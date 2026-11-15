# AI 지역 상대 취약도 분석 API

`POST /api/resident/risk-assessments`

Swagger 그룹: `주민-AI취약도 분석`

로그인한 주민의 주소를 좌표로 변환한 뒤 Python 서비스의 사전 계산 격자를 조회합니다.
분석 결과는 `risk_assessments`에 저장하고 같은 응답에서 반환합니다. 점수와 등급은 지역
상대 취약도이며 개별 주택의 실제 침수 확률이나 안전 여부를 뜻하지 않습니다.

## 요청

```json
{
  "address": "경기도 부천시 원미구 지봉로 43",
  "detailAddress": "101호",
  "housingType": "SEMI_BASEMENT"
}
```

- `address`, `detailAddress`: 필수, 공백 제외 내용이 있어야 하며 최대 255자
- `housingType`: 선택, `SEMI_BASEMENT`, `GROUND_FLOOR`, `ABOVE_GROUND`
- 성공 응답의 `assessmentId`는 현장점검 신청과 맞춤 예방 가이드 생성에 사용

## 성공 응답

`201 RISK_ASSESSMENT_201_1`

```json
{
  "isSuccess": true,
  "status": 201,
  "code": "RISK_ASSESSMENT_201_1",
  "message": "취약도 분석이 완료되었습니다.",
  "result": {
    "assessmentId": 1,
    "riskType": "REGIONAL_RELATIVE_VULNERABILITY",
    "cellId": "r605c713",
    "score": 0.2841,
    "riskLevel": "HIGH",
    "status": "COMPLETED",
    "reasons": [],
    "floodRecord": "침수 기록 없음 (안전을 뜻하지 않음)",
    "disclaimer": "지역 상대 취약도이며 개별 주택 침수 위험도가 아닙니다."
  }
}
```

`riskLevel`은 `HIGH`, `CAUTION`, `LOW` 중 하나입니다. 현재 격자 데이터가 SHAP 근거를
제공하지 않으면 `reasons`는 빈 배열입니다.

## Spring-Python 계약

기본 호출은 Python 서비스의 `GET /api/vulnerability?lat={latitude}&lon={longitude}`입니다.
환경 변수로 주소와 제한 시간을 변경할 수 있습니다.

- `AI_MODEL_BASE_URL`
- `AI_MODEL_PREDICT_PATH`
- `AI_MODEL_CONNECT_TIMEOUT_MILLIS`
- `AI_MODEL_READ_TIMEOUT_MILLIS`

Python 성공 응답은 `status=OK`, 격자 ID, 0~1 점수, 등급, 침수 기록 안내와 주의 문구를
포함해야 합니다. 분석 대상 격자 밖 응답은 422로 변환하며 가장 가까운 격자를 임의로
선택하지 않습니다. 응답 형식·점수 범위·등급·필수 문구가 계약과 다르면 저장하지 않습니다.

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | `COMMON_400_2` | 필수값·JSON 형식 오류 |
| 400 | `ADDRESS_400_1` | 부천시의 구체적인 주소가 아님 |
| 403 | `COMMON_403_1` | 주민 계정이 아님 |
| 404 | `ADDRESS_404_2` | 입력 주소와 일치하는 좌표 없음 |
| 422 | `RISK_ASSESSMENT_422_1` | 분석 대상 격자 밖 |
| 500 | `RISK_ASSESSMENT_500_1` | 분석 결과 직렬화·저장 실패 |
| 502 | `ADDRESS_502_1` | 주소 좌표 서비스 호출 실패 |
| 502 | `RISK_ASSESSMENT_502_1`, `RISK_ASSESSMENT_502_2` | Python 호출 또는 응답 검증 실패 |
| 503 | `ADDRESS_503_1` | 주소 좌표 서비스 시간 초과 |
| 503 | `RISK_ASSESSMENT_503_1` | Python 서비스 시간 초과 |
