# 점검 배정·일정 저장

`POST /api/admin/inspections/{inspectionId}/assignment`

Swagger 그룹: `운영자-현장점검 관리`

운영자 Access Token을 `Authorization: Bearer {accessToken}` 헤더로 보내고
`Content-Type: application/json`으로 요청합니다. `inspectionId`는 양의 정수인 점검 ID입니다.

## 저장 순서

1. 상태 저장 API(`PATCH /api/admin/inspections/{inspectionId}/status`)에 `SCHEDULED`를 보내 예정 상태를 저장합니다. 이 단계에는 담당자·일정이 없어도 됩니다.
2. 아래 요청으로 담당자·확정 방문일·방문 시간·주민 방문 전 안내를 모두 전달합니다.
3. DB 상태가 `SCHEDULED`인 경우에만 저장합니다. 대기·진행·완료이면 409이며 기존 내용은 유지됩니다.

```json
{
  "inspectorId": 2,
  "visitDate": "2030-01-03",
  "visitTime": "14:00",
  "preVisitNotice": "방문 전 배수구 주변을 정리해주세요."
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| inspectorId | O | 같은 관할 지역 운영자 ID, 양의 정수 |
| visitDate | O | 확정 방문일, YYYY-MM-DD |
| visitTime | O | 방문 시간, HH:mm |
| preVisitNotice | O | 주민 방문 전 안내, 공백만 입력 불가, 최대 2,000자, 앞뒤 공백 제거 |

방문 일시는 한국 시간 기준 현재 이상이어야 합니다. 현재는 부천시 관할 운영자가
부천시 주소의 신청에 연결된 점검만 저장할 수 있습니다. 주민은 담당자로 배정할 수 없습니다.

기존 배정이 있으면 담당자·일정·안내를 교체하며 상태는 예정으로 유지합니다.
자동 진행 전환과 같은 점검 행 잠금을 사용합니다. 자동 전환이 먼저 완료되면 배정 저장은
409를 반환하고, 일정 변경이 먼저 완료되면 자동 전환은 변경된 방문 일시를 기준으로 판단합니다.
신청 상태·내부 메모·점검 결과·상태 변경 이력은 변경하지 않습니다.
알림 체크박스와 알림 발송 기능은 이번 API에 포함하지 않습니다.

## 200 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "INSPECTION_200_2",
  "message": "점검 배정과 일정이 저장되었습니다.",
  "result": {
    "inspectionId": 1,
    "inspectorId": 2,
    "inspectorName": "김담당",
    "visitDate": "2030-01-03",
    "visitTime": "14:00",
    "preVisitNotice": "방문 전 배수구 주변을 정리해주세요.",
    "status": "SCHEDULED",
    "updatedAt": "2030-01-02T14:00:00"
  },
  "timeStamp": "2030-01-02 14:00:00"
}
```

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_2 | 필수값 누락, 잘못된 날짜·시간·JSON, 안내 2,000자 초과 |
| 400 | COMMON_400_3 | 점검 ID 형식·범위 오류 |
| 400 | INSPECTION_400_1 | 주민 또는 다른 관할 운영자를 담당자로 지정 |
| 400 | INSPECTION_400_2 | 과거 방문 일시 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락 또는 유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | INSPECTION_403_1 | 관할 지역 접근 권한 없음 |
| 404 | INSPECTION_404_1 | 점검 없음 |
| 404 | INSPECTION_404_2 | 담당자 계정 없음 |
| 409 | INSPECTION_409_3 | 예정 상태가 DB에 저장되어 있지 않음 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
| 415 | COMMON_415_1 | 지원하지 않는 요청 본문 형식 |

## DB

V14 마이그레이션은 `inspections.inspector_user_id`(users 외래키)와
`inspections.pre_visit_notice`를 추가합니다. 방문 일시는 기존 `scheduled_at`에 저장합니다.
기존 점검 행과 상태·일정은 보존하며, 신규 두 컬럼은 기존 행에서 NULL입니다.
서버 실행 시 Flyway가 적용합니다. 이미 적용된 V13 파일은 변경하지 않습니다.
