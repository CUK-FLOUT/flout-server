# 우선점검 대상 지정·취소

`PUT /api/admin/priority-inspections/{applicationId}/designation`

Swagger 그룹: `운영자-우선점검 관리`

운영자 토큰을 `Authorization: Bearer {accessToken}`으로 전달합니다.
`applicationId`는 양의 정수인 현장점검 신청 ID이며 점검 ID와 구분합니다.
`Content-Type: application/json`과 지정 여부를 담은 요청 본문이 필요합니다.

## 요청

```http
PUT /api/admin/priority-inspections/1/designation
Authorization: Bearer {accessToken}
Content-Type: application/json

{"designated": true}
```

지정은 `{"designated": true}`로 요청하고 취소는 `{"designated": false}`로 요청합니다.
`designated`는 필수 Boolean이며 누락 또는 null은 400을 반환합니다.
프론트는 체크박스 변경 내용을 선택 상태로 관리하고 저장 버튼을 누르면 선택한 신청 ID와 지정 여부를 전송합니다.
한 번의 요청은 한 신청만 처리합니다. 여러 신청은 각 신청 ID로 요청하고 성공한 항목만 화면에 반영합니다.

화면의 메모는 기존 조회 응답으로 표시합니다.
이 API는 메모를 입력받거나 저장하지 않으며 요청 본문을 보내도 사용하지 않습니다.

## 처리

- 점검 신청 목록에서 선택한 기존 신청을 우선점검 대상으로 지정하며 새 신청이나 점검을 생성하지 않습니다.
- 현재는 부천시 관할 운영자가 `부천시`, `경기 부천시`, `경기도 부천시` 주소의 신청만 지정할 수 있습니다.
- `부천시민로`나 `서울특별시 부천시`처럼 행정구역이 다른 주소는 허용하지 않습니다.
- 지정 운영자는 인증된 로그인 계정이며 클라이언트가 따로 지정하지 않습니다.
- 신청당 한 건만 저장하며 지정 시각은 한국 시간 기준입니다.
- 중복 요청은 기존 결과를 200으로 반환하고 최초 지정 운영자·지정 시각을 유지합니다.
- 취소는 지정 정보만 삭제하며 미지정 신청에 취소를 요청해도 200을 반환합니다.
- 취소 후 재지정하면 현재 운영자와 지정 시각으로 새 지정 정보를 저장합니다.
- 신청 행 잠금과 READ_COMMITTED 격리 수준으로 동시 요청을 순차 처리하고 최신 지정 결과를 조회합니다.
- 신청 상태·내부 메모·처리 이력·점검 상태·담당자·방문 일정은 변경하지 않습니다.
- 선정 순서 변경·메모 작성·주민 알림은 이번 API에 포함하지 않습니다.

## 200 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PRIORITY_INSPECTION_200_1",
  "message": "우선점검 대상으로 지정되었습니다.",
  "result": {
    "applicationId": 1,
    "receiptNo": "000001",
    "designated": true,
    "designatedAt": "2026-10-29T10:00:00"
  },
  "timeStamp": "2026-10-29 10:00:00"
}
```

`receiptNo`는 신청 ID를 최소 6자리 문자열로 표시한 값이며 6자리가 넘으면 자르지 않습니다.

취소 성공 응답:

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PRIORITY_INSPECTION_200_2",
  "message": "우선점검 대상 지정이 취소되었습니다.",
  "result": {
    "applicationId": 1,
    "receiptNo": "000001",
    "designated": false,
    "designatedAt": null
  },
  "timeStamp": "2026-10-29 10:00:00"
}
```

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 신청 ID 형식·범위 오류 |
| 400 | COMMON_400_2 | 지정 여부 누락·null 또는 잘못된 요청 본문 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락 또는 유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | PRIORITY_INSPECTION_403_1 | 관할 지역 접근 권한 없음 |
| 404 | APPLICATION_404_2 | 신청 없음 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
| 415 | COMMON_415_1 | 지원하지 않는 요청 본문 형식 |

## DB

V18은 `priority_inspection_designations` 테이블을 추가합니다.
`application_id`를 기본키이자 신청 테이블의 외래키로 사용해 중복 지정을 제한합니다.
지정 운영자 외래키·지정 시각을 저장하며 기존 신청·점검 데이터는 변경하지 않습니다.
V19는 잘못 추가했던 `selection_memo` 컬럼을 제거합니다. 기존 신청의 내부 메모와는 별개의 컬럼입니다.
