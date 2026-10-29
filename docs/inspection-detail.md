# 현장점검 배정 상세정보 조회

`GET /api/admin/inspections/{inspectionId}`

운영자 Bearer 토큰이 필요합니다. 본인 관할 점검만 조회하며 데이터를 변경하지 않습니다.
`inspectionId`는 양의 정수인 점검 ID이며 신청 ID와 구분합니다.
Swagger 그룹은 `운영자-현장점검 관리`입니다.

## 응답 예시

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "INSPECTION_200_4",
  "message": "현장점검 배정 상세정보를 조회했습니다.",
  "result": {
    "inspectionId": 1,
    "applicationId": 5,
    "receiptNo": "000005",
    "applicantName": "김주민",
    "address": "경기도 부천시 원미구 길주로 53",
    "detailAddress": "101호",
    "contact": "010-1234-1234",
    "preferredVisitDate": "2026-11-03",
    "preferredTimeSlot": "MORNING",
    "inspectorId": 3,
    "inspectorName": "김담당"
  },
  "timeStamp": "2026-10-30T10:00:00"
}
```

## 응답 규칙

- 접수번호는 신청 ID를 최소 6자리로 표시하며 긴 ID를 자르지 않습니다.
- 이름은 신청 주민의 이름, 연락처는 신청 시 입력한 연락처입니다.
- 주소는 신청에 저장된 전체 주소와 상세주소를 각각 반환합니다.
- 방문 희망 날짜·시간대는 주민의 신청 정보이며 확정 방문 일정과 구분합니다.
- 희망 시간대는 `MORNING`, `AFTERNOON`, `ANY`입니다. 별도 희망 시각은 저장하지 않습니다.
- 희망 날짜·시간대·상세주소가 없으면 null이며 담당자가 미배정이면 담당자 ID·이름은 null입니다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 잘못된 점검 ID 형식·범위 |
| 401 | 공통 인증 오류 | 인증 누락·유효하지 않은 토큰 |
| 403 | 공통 접근 권한 오류 | 운영자가 아님 |
| 403 | INSPECTION_403_2 | 관할 미설정·미지원 관할·관할 밖 점검 |
| 404 | INSPECTION_404_1 | 점검 없음 |
