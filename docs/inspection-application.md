# 주민 현장점검 신청과 점검 생성

`POST /api/resident/applications`는 신청을 임시저장하는 API입니다.
첫 신청 사진 등록이 성공하면 신청이 접수되고 점검이 생성됩니다. 별도의 운영자 접수 API는 호출하지 않습니다.

작성 중 폼 임시저장은 실제 신청의 `DRAFT`와 별개입니다. 주민은 클라이언트 UUID로
`PUT /api/resident/application-form-drafts/{draftId}`에 취약도 분석 ID와 폼 전체를 저장하고,
`GET /api/resident/applications/drafts`로 이어 쓸 폼을 조회합니다. 최초 저장의 `version`은
생략 또는 null이고 갱신에는 마지막 응답의 version이 필요합니다. `fields`는 최대 16000자의
JSON 객체로 전체 교체되며 버전 충돌은 409입니다. 폼 제출 시 `X-Form-Draft-Id`로 연결합니다.
신청 생성 후 사진 등록 전까지 실제 신청이 `DRAFT`로 남는 단계와 구분해야 합니다.
폼 저장 시 현장점검 폼은 `assessmentId`, 지원 폼은 `applicationId`만 원본 ID로 사용하며
다른 종류의 원본 ID를 함께 보내거나 `fields`가 JSON 객체가 아니면 `400 / FORM_DRAFT_400_1`입니다.
폼 목록은 완료된 폼을 제외하고 동일 원본의 최근 폼 하나만 반환합니다. 본인 소유가 아닌 폼이나
원본은 `404 / FORM_DRAFT_404_1`로 처리합니다.

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

신청 생성 시 `guideId`와 `requestReason`은 선택입니다. `assessmentId`, 주소·상세 주소,
위도·경도, 연락처, `occupancyType`, `floodExperience`, `existingFacility`, 희망 방문일·시간대,
하나 이상의 `inflowLocations`, 신청자 이름은 필수입니다. 희망 방문일은 오늘 이후여야 합니다.
상세 주소는 분석 결과와 일치해야 하며, 가이드를 전달하면 해당 분석에 연결되어 있어야 합니다.

`PATCH /api/resident/applications/{applicationId}`는 본인의 실제 `DRAFT` 신청만 부분 수정합니다.
생략한 필드는 유지하고 `requestReason`만 null로 지울 수 있습니다. 유입 위치는 전달 시 하나 이상으로
전체 교체하며 수정할 수 없는 필드는 거절합니다. 다른 주민의 신청은 `403 / APPLICATION_403_7`,
DRAFT가 아닌 신청은 `409 / APPLICATION_409_5`입니다.

## 접수 이후 점검 진행

사진 등록 응답의 `result.inspectionId`로 배정 API를 호출하여 담당자·확정 일정을 저장하면 점검이 예정 상태가 됩니다.
방문 시간이 되면 자동으로 진행 상태로 전환합니다. 운영자 현장사진이 등록된 초안을 최종 저장·주민 공개하면 점검과 신청이 함께 완료됩니다.
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

`POST /api/resident/applications/{applicationId}/attachments`의 성공 result는 점검 ID와 사진 목록을 포함하는 객체입니다.

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

## 주민 결과와 권장조치 조회

본인의 신청 ID로 `GET /api/resident/applications/{applicationId}/result`를 호출하면 공개된
결과의 `summary`, `publishedAt`, `vulnerabilities`, `photos`를 조회합니다. 초안과 운영자
내부 메모는 반환하지 않습니다. `GET /api/resident/applications/{applicationId}/recommendations`는
공개된 권장조치 원문을 저장 순서의 `recommendedActions`로 반환합니다.
`GET /api/resident/applications/{applicationId}/recommended-facilities`의 `facilities`는
권장조치에서 추출한 중복 없는 시설명이고, `recommendationCount`는 시설 수가 아닌 원래 권장조치
수입니다. 세 API 모두 공개 결과가 없으면 `400 / APPLICATION_400_5`, 타인 신청이면
`403 / APPLICATION_403_6`, 신청이 없으면 `404 / APPLICATION_404_2`입니다.

## 내 신청 목록과 상세

`GET /api/resident/applications`는 본인의 접수된 신청을 접수 시각 내림차순으로 반환하며
DRAFT를 제외합니다. 과거 데이터에 접수 시각이 없으면 생성 시각으로 정렬하고, 결과가 없으면
빈 배열입니다. `GET /api/resident/applications/{applicationId}`는 본인의 DRAFT도 조회할 수
있으며 이때 `progress.stage`는 null입니다. 상세의 `progress`는 공개된 결과와 접수된 지원 및
시공 상태를 반영하며 지원 DRAFT와 점검 초안은 노출하지 않습니다. 점검이 완료돼도 결과가 공개되지
않았다면 표시 단계는 `INSPECTION_IN_PROGRESS`이고 공개된 경우에만 `INSPECTION_COMPLETED`입니다.
