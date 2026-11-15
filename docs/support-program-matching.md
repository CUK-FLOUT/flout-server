# 지원사업 AI 매칭 API

`POST /api/resident/support-application/{supportApplicationId}/matching`

Swagger 그룹: `주민-지원신청`

경로 변수 이름은 기존 호환성을 위해 `supportApplicationId`이지만 실제 값은 주민 본인
소유의 **현장점검 신청 ID**입니다. 공개된 점검 결과, 주민·주택 정보, 전문가 권장조치와
DB의 지원사업 목록을 비교해 최대 한 개의 사업을 안내합니다.

## 처리 원칙

- PUBLISHED 점검 결과만 사용합니다.
- 상세주소·연락처·좌표는 Gemini에 전송하지 않습니다.
- DB 조회와 입력 스냅샷 복사를 끝낸 뒤 트랜잭션 밖에서 Gemini를 호출합니다.
- Gemini는 후보 사업 ID와 판정값만 반환합니다. 사업명·설명·기간·기관 정보는 실제 DB
  사업을 다시 조회해 응답합니다.
- 모델이 후보 밖 ID나 허용되지 않은 판정값을 반환해도 실제 후보 사업만 노출합니다.
- 지원시설 정보가 있는 사업은 전문가 권장시설과 실제 지원시설이 일치할 때만 `DIRECT`를
  유지합니다. 직접 일치하지 않으면 `COMPLEMENTARY`로 내려 참고용 사업임을 명시합니다.
- 사업의 지원시설 정보가 비어 있으면 Gemini가 반환한 유효한 `DIRECT`를 유지하지만, `지원시설
  정보 확인 필요`를 추가하고 `matchStatus`를 `NEEDS_CONFIRMATION`으로 반환합니다. 이는 검증된
  시설 일치를 뜻하지 않습니다.
- 이 결과는 지원 승인이나 공식 접수를 뜻하지 않습니다.

## 응답 필드

- `matchStatus`: `MATCHED` 또는 추가 확인이 필요한 `NEEDS_CONFIRMATION`
- `recommendation.itemType`: 현재 실제 DB 사업을 뜻하는 `PROGRAM`
- `programId`: 서버 DB에서 검증한 지원사업 ID
- `matchType`: `DIRECT` 또는 `COMPLEMENTARY`
- `matchedActionIds`: 직접 일치 근거가 된 전문가 권장조치 ID
- `evidence`: 응답 판단에 사용한 서버 데이터
- `missingInformation`: 접수 상태·기간·자격 등 추가 확인 항목
- `guidanceId`, `guidance`: 예약 필드이므로 현재 `null`

## 성공 응답 요약

`200 SUPPORT_200_3`

```json
{
  "supportApplicationId": 12,
  "matchStatus": "NEEDS_CONFIRMATION",
  "recommendation": {
    "itemType": "PROGRAM",
    "programId": 3,
    "guidanceId": null,
    "matchType": "DIRECT",
    "reason": "출입구 침수 위험에 물막이판 설치 지원이 관련됩니다.",
    "matchedActionIds": [],
    "evidence": [],
    "missingInformation": ["접수상태 확인 필요", "신청 자격 확인 필요"],
    "program": {
      "name": "물막이판 설치 지원",
      "description": "침수 예방을 위한 출입구 물막이판 설치 비용을 지원합니다.",
      "targetDescription": "부천시 침수 취약 주택",
      "applicationStatus": "UNKNOWN"
    },
    "guidance": null
  }
}
```

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | `COMMON_400_3` | 0 이하 또는 잘못된 ID 형식 |
| 400 | `SUPPORT_400_2` | 점검 결과가 주민에게 공개되지 않음 |
| 403 | `APPLICATION_403_6` | 다른 주민의 현장점검 신청 |
| 404 | `APPLICATION_404_2` | 현장점검 신청 없음 |
| 500 | `SUPPORT_500_1` | 후보 사업·서버 설정 등 매칭 입력 오류 |
| 503 | `SUPPORT_503_1` | Gemini 일시 장애·시간 초과·유효하지 않은 응답 |
