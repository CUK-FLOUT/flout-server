# 주민 현장점검 신청과 점검 생성

`POST /api/resident/applications`는 신청을 임시저장하는 API입니다.
첫 신청 사진 등록이 성공하면 신청이 접수되고 점검이 생성됩니다. 별도의 운영자 접수 API는 호출하지 않습니다.

## 처리 흐름

1. 본인의 완료된 취약도 분석 결과와 선택한 가이드 연결을 검증합니다.
2. 신청을 `DRAFT`로 저장하고 유입 위치를 저장합니다. 아직 접수 이력과 점검은 생성하지 않습니다.
3. `applicationId`, `inspectionId=null`, `status=DRAFT`, `submittedAt=null`을 반환합니다.
4. `applicationId`로 `POST /api/resident/applications/{applicationId}/attachments`에 사진을 등록합니다.
5. 첫 사진 등록 트랜잭션에서 첨부 저장, 신청의 `RECEIVED` 전환, 접수 이력 기록, `RECEIVED` 점검 생성을 함께 처리합니다.
6. 같은 트랜잭션 안에서 점검 ID를 확인해 사진 목록과 함께 응답합니다. 점검 ID 조회가 실패하면 첨부·접수·이력·점검 생성도 롤백합니다.

파일 저장소 업로드는 DB 트랜잭션 밖에서 수행하며 저장 실패 시 업로드 파일을 정리합니다.
주민 희망 방문일·시간대는 신청에 저장하고 점검의 확정 방문 일시는 아직 지정하지 않습니다.

## 신청 임시저장 201 응답 예시

```json
{
  "isSuccess": true,
  "status": 201,
  "code": "APPLICATION_201_1",
  "message": "현장점검 신청이 임시 저장되었습니다. 사진 등록 후 접수됩니다.",
  "result": {
    "applicationId": 1,
    "inspectionId": null,
    "receiptNo": "000001",
    "status": "DRAFT",
    "submittedAt": null
  },
  "timeStamp": "2026-11-03 00:00:00"
}
```

`receiptNo`는 신청 ID를 최소 6자리로 표시하며 6자리를 초과해도 자르지 않습니다.
동일 취약도 분석 결과에는 신청 한 건만 연결합니다. 같은 내용의 DRAFT 재요청은 기존 신청을 재사용하고,
내용이 다르거나 이미 접수된 신청이면 충돌 오류를 반환합니다.

## 접수 이후 점검 진행

사진 등록 응답의 `result.inspectionId`로 `PATCH /api/admin/inspections/{inspectionId}/status`에
`{"status":"SCHEDULED"}`를 보내고 배정 API로 담당자·확정 일정을 저장합니다.
진행 → 완료 순서로 변경한 뒤 운영자 현장사진이 등록된 초안을 저장·주민 공개합니다.
주민 신청 사진과 운영자 점검 현장사진은 서로 다른 API로 등록합니다.
`applicationId`와 `inspectionId`는 서로 다를 수 있으므로 응답의 식별자를 구분해 사용합니다.

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
