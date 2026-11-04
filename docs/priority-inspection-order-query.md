# 우선점검 선정 순서 조회

`GET /api/admin/priority-inspections/order`

Swagger 그룹: `운영자-우선점검 관리`

운영자 Bearer 토큰이 필요합니다. 현재 부천시 관할 운영자만 조회합니다.
요청 본문과 쿼리 파라미터는 없습니다.

선정 순서 변경 모달을 열 때 호출합니다. 관할 내 지정 대상 중 DRAFT와 점검 완료(COMPLETED) 건을 제외한 전체를 반환합니다.
검색·필터·페이지는 적용하지 않으며 selectionOrder, applicationId 오름차순입니다.
저장된 순서를 그대로 반환하므로 완료 건이 빠진 자리는 다시 번호를 매기지 않습니다.
조회는 순서와 변경 시각을 변경하지 않습니다. 대상이 없으면 applications는 빈 배열입니다.

## 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PRIORITY_INSPECTION_200_6",
  "message": "우선점검 선정 순서를 조회했습니다.",
  "result": {
    "applications": [
      {
        "applicationId": 3,
        "receiptNo": "000003",
        "selectionOrder": 1,
        "address": "경기도 부천시 원미구 길주로 210",
        "riskLevel": "HIGH",
        "floodExperience": "YES",
        "requestReason": "최근 배수구 역류",
        "nearbyApplicationCount": 3,
        "designated": true
      }
    ]
  },
  "timeStamp": "2026-11-03 12:00:00"
}
```

인근 신청 건수는 목록 검색 조건과 무관하며, 동일 관할 내 본인 제외 1km 이내 신청을 집계합니다. DRAFT는 제외하고 완료 신청은 포함합니다.

## 오류

- 401: 토큰 누락 (`COMMON_401_1`), 만료·유효하지 않은 토큰 (`COMMON_401_3`).
- 403: 운영자 권한 없음 (`COMMON_403_1`), 관할 없음·지원하지 않는 관할 (`PRIORITY_INSPECTION_403_1`).
- 406: 응답 형식 미지원.
- 500: 서버 내부 오류.

모달에서 순서를 변경한 후 전체 applicationId를 원하는 순서대로
`PUT /api/admin/priority-inspections/order`의 applicationIds로 전달합니다.
조회 후 지정·취소 또는 점검 완료로 대상이 바뀌면 저장 시 409가 반환되므로 다시 조회합니다.
