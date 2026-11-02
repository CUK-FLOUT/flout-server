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
    "receiptNo": "000001",
    "status": "RECEIVED",
    "submittedAt": "2026-10-29T01:00:00"
  },
  "timeStamp": "2026-10-29 01:00:00"
}
```

`applicationId`는 신청 내부 메모 등 신청 API에 사용하고,
`inspectionId`는 점검 상태 변경과 배정·일정 저장 API에 사용합니다. 두 ID는 같다고 가정하지 않습니다.

응답의 `receiptNo`는 신청 ID를 최소 6자리로 표시한 값입니다. 신청 ID가 1이면 `000001`이며 6자리를 초과해도 자르지 않습니다.

신청 제출 → 접수(신청·점검 생성) → 상태 API로 점검 예정 저장 → 배정 저장 → 점검 진행 → 점검 완료 순서입니다.
신청 성공 후 응답의 `inspectionId`로 `PATCH /api/admin/inspections/{inspectionId}/status`에
`{"status":"SCHEDULED"}`를 보내고, 배정 API로 담당자·일정을 저장합니다.

동일 주민·주소·취약도 분석 결과의 재신청 제한은 추가하지 않습니다.
재신청마다 별도의 신청 ID와 점검 ID가 생성됩니다.
V16 마이그레이션에서 기존 `SUBMITTED` 신청과 `WAITING` 점검 및 해당 이력을
`RECEIVED`(접수)로 통일합니다. 점검이 없던 기존 신청에는 접수 점검을 한 건 생성합니다.
이미 있는 점검은 중복 생성하지 않으며 예정·진행·완료 상태와 기존 일정은 유지합니다.

## 신청자 이름

신청 요청에 `applicantName`을 필수로 전달합니다. 공백만 있는 값과 50자를 초과하는 값은 거절하며, 저장 시 앞뒤 공백을 제거합니다.

```json
{
  "applicantName": "김주민"
}
```

위 예시는 추가할 필드만 표시한 것이며 기존 신청 필수 항목도 함께 전달해야 합니다.
신청자 이름은 계정 이름과 별도로 `inspection_applications.applicant_name`에 저장합니다.
신청 상세·점검 상세·배정·우선점검 상세는 저장된 이름을 사용합니다.
같은 임시 신청을 재요청할 때 이름이 다르면 기존 임시 신청을 재사용하지 않습니다.
V33은 기존 신청의 이름을 연결된 계정 이름으로 채웁니다. 이후 계정 이름이 바뀌어도 신청 당시 이름은 유지됩니다.

## 사진 등록 후 점검 ID

`POST /api/resident/applications/{applicationId}/attachments`의 성공 result는 배열에서 객체로 변경됩니다.

```json
{
  "inspectionId": 12,
  "attachments": [
    { "attachmentId": 5, "filename": "출입구.png", "size": 62267, "contentType": "image/png" }
  ]
}
```

프론트는 사진 목록을 기존 `result` 대신 `result.attachments`에서 읽고, `result.inspectionId`를 후속 점검 요청에 사용합니다.
신청 생성 응답의 inspectionId는 사진 등록 전에는 null이며, 첫 사진 등록으로 점검이 생성됩니다.
추가 사진 등록과 같은 Idempotency-Key를 사용한 재시도는 기존에 연결된 동일한 점검 ID를 반환합니다.
