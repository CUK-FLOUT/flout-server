# 우선점검 신청정보 조회

`GET /api/admin/priority-inspections/{applicationId}`

Swagger 그룹: `운영자-우선점검 관리`

운영자 Bearer 토큰이 필요합니다. applicationId는 양의 정수인 현장점검 신청 ID이며 점검 ID와 구분합니다.
요청 본문·쿼리 파라미터는 없습니다. 우선점검 미지정 신청도 조회합니다.
현재는 부천시 관할 운영자가 부천시 주소의 신청만 조회할 수 있습니다.

## 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PRIORITY_INSPECTION_200_5",
  "message": "우선점검 신청정보를 조회했습니다.",
  "result": {
    "applicationId": 5,
    "receiptNo": "000005",
    "applicantName": "김주민",
    "address": "경기도 부천시 원미구 길주로 53",
    "detailAddress": "101호",
    "inspectionNeed": "우선점검 권장",
    "riskLevel": "HIGH",
    "floodExperience": "YES",
    "requestReason": "최근 배수구 역류",
    "nearbyApplicationCount": 3,
    "internalMemo": "출입구 누수 확인 필요"
  },
  "timeStamp": "2026-10-30 10:00:00"
}
```

- receiptNo는 신청 ID를 최소 6자리로 표시하고 긴 ID를 자르지 않습니다.
- applicantName은 신청자 계정 이름입니다.
- address와 detailAddress는 신청 시 연결된 분석 결과에서 복사해 신청에 저장한 주소·상세주소입니다. 신청 POST에서 별도로 입력받지 않습니다.
- riskLevel은 해당 신청에 연결된 분석 결과이며 이후 생성한 다른 분석을 사용하지 않습니다.
- inspectionNeed는 HIGH → 우선점검 권장, CAUTION → 점검 검토, LOW → 일반 점검입니다. 실제 우선점검 지정 여부와 별개입니다.
- floodExperience는 신청 시 입력한 YES / NO / UNKNOWN을 그대로 반환합니다.
- requestReason과 internalMemo, detailAddress가 없으면 null을 반환합니다.
- internalMemo는 기존 메모 저장 API로 저장된 값이며 상세 조회 시 생성·변경하지 않습니다.
- 인근 신청은 목록과 같은 집계 로직으로 동일 관할의 본인 제외 직선거리 1km 이하 신청을 셉니다. 같은 좌표의 다른 신청도 포함하며 검색·필터·페이지·신청 상태·기간으로 제한하지 않습니다.
- 조회 시 신청·분석·내부 메모·우선점검 지정·선정 순서·점검·이력 데이터를 변경하지 않습니다.

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 잘못된 ID 형식·0 이하·Long 범위 초과 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락·유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | PRIORITY_INSPECTION_403_1 | 운영자 관할 미지원 또는 관할 밖 신청 |
| 404 | APPLICATION_404_2 | 신청 없음 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
