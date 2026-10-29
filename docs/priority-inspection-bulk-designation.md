# 우선점검 대상 일괄 지정·취소

`PUT /api/admin/priority-inspections/designation`

운영자 Bearer 토큰과 `Content-Type: application/json`이 필요합니다.
한 건과 여러 건 모두 이 API를 사용합니다. 기존 신청 ID 경로의 단건 API는 제거했습니다.
한 건만 처리하려면 `applicationIds`에 ID 하나만 전달합니다.
복수 체크 후 지정·취소 버튼을 누르면 선택된 신청 ID들을 한 번에 전달합니다.
내부 메모는 기존 메모 API로 별도 저장합니다.

## 요청

```json
{"applicationIds": [5, 6, 7], "designated": true}
```

`applicationIds`는 신청 ID이며 점검 ID가 아닙니다. `designated`는 true이면 지정, false이면 취소합니다.
목록 누락·빈 목록·null 항목·0 이하 ID·중복 ID 및 지정 여부 누락·null은 400입니다.

## 처리

- 인증된 운영자의 관할 잠금 후 신청을 ID 오름차순으로 잠그고 전체를 검증합니다.
- 현재는 부천시 관할 신청만 처리합니다. 없는 신청은 404, 관할 밖 신청은 403입니다.
- 한 트랜잭션으로 처리하며 검증·저장 중 실패하면 전체를 롤백합니다.
- 기존 지정의 최초 운영자·시각·선정 순서는 유지합니다.
- 신규 지정은 요청 ID 순서대로 관할 선정 목록 마지막에 추가합니다.
- 취소 시 미지정 신청은 성공 처리하고 남은 신청의 상대 순서를 유지하며 순위를 1부터 정리합니다.
- 동일 요청을 반복해도 지정·순서 변경 운영자와 시각을 불필요하게 갱신하지 않습니다.
- 신청·내부 메모·점검 상태·담당자·방문 일정·처리 이력은 변경하지 않습니다.

## 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PRIORITY_INSPECTION_200_1",
  "message": "우선점검 대상으로 지정되었습니다.",
  "result": {
    "applications": [
      {"applicationId": 5, "receiptNo": "000005", "designated": true, "designatedAt": "2026-10-29T10:00:00"},
      {"applicationId": 6, "receiptNo": "000006", "designated": true, "designatedAt": "2026-10-29T10:00:00"},
      {"applicationId": 7, "receiptNo": "000007", "designated": true, "designatedAt": "2026-10-29T10:00:00"}
    ]
  },
  "timeStamp": "2026-10-29 10:00:00"
}
```

결과는 요청 ID 순서입니다. 취소 성공 코드는 `PRIORITY_INSPECTION_200_2`이며 각 결과는 designated=false, designatedAt=null입니다.
접수번호는 신청 ID를 최소 6자리로 표시하며 긴 ID를 자르지 않습니다.

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_2 | 목록·지정 여부·요청 본문 오류 |
| 400 | PRIORITY_INSPECTION_400_1 | 중복 신청 ID |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락·유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | PRIORITY_INSPECTION_403_1 | 관할 접근 권한 없음 |
| 404 | APPLICATION_404_2 | 신청 없음 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
| 415 | COMMON_415_1 | 지원하지 않는 요청 본문 형식 |
