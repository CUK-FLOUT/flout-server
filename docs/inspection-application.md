# 주민 현장점검 신청과 점검 생성

`POST /api/resident/applications`는 주민이 제출하는 즉시 접수되는 API입니다.
별도의 운영자 접수 처리 API를 호출하지 않습니다.

## 처리 흐름

1. 본인의 완료된 취약도 분석 결과와 선택한 가이드 연결을 검증합니다.
2. 신청을 `RECEIVED`(접수)로 저장합니다.
3. 유입 위치와 `RECEIVED` 신청 이력을 저장합니다.
4. 신청에 연결된 `RECEIVED`(접수) 점검을 한 건 생성합니다.
5. 생성한 `applicationId`와 `inspectionId`를 함께 반환합니다.

신청·유입 위치·이력·점검은 같은 트랜잭션에서 저장합니다. 하나라도 실패하면 모두 롤백합니다.
접수번호 충돌은 새 트랜잭션으로 재시도하며 실패한 시도의 점검은 남지 않습니다.
주민 희망 방문일·시간대는 신청에만 저장하고 점검의 확정 방문 일시는 아직 지정하지 않습니다.

## 201 응답 예시

```json
{
  "isSuccess": true,
  "status": 201,
  "code": "APPLICATION_201_1",
  "message": "현장점검 신청이 완료되었습니다.",
  "result": {
    "applicationId": 1,
    "inspectionId": 2,
    "receiptNo": "IA-A1B2C3D4E5F678901",
    "status": "RECEIVED",
    "submittedAt": "2026-10-29T01:00:00"
  },
  "timeStamp": "2026-10-29 01:00:00"
}
```

`applicationId`는 신청 내부 메모 등 신청 API에 사용하고,
`inspectionId`는 점검 상태 변경과 배정·일정 저장 API에 사용합니다. 두 ID는 같다고 가정하지 않습니다.

신청 제출 → 접수(신청·점검 생성) → 상태 API로 점검 예정 저장 → 배정 저장 → 점검 진행 → 점검 완료 순서입니다.
신청 성공 후 응답의 `inspectionId`로 `PATCH /api/admin/inspections/{inspectionId}/status`에
`{"status":"SCHEDULED"}`를 보내고, 배정 API로 담당자·일정을 저장합니다.

동일 주민·주소·취약도 분석 결과의 재신청 제한은 추가하지 않습니다.
재신청마다 별도의 신청 ID와 점검 ID가 생성됩니다.
V16 마이그레이션에서 기존 `SUBMITTED` 신청과 `WAITING` 점검 및 해당 이력을
`RECEIVED`(접수)로 통일합니다. 점검이 없던 기존 신청에는 접수 점검을 한 건 생성합니다.
이미 있는 점검은 중복 생성하지 않으며 예정·진행·완료 상태와 기존 일정은 유지합니다.
