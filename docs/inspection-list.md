# 현장점검 검색·목록 조회

`GET /api/admin/inspections`

운영자 Bearer 토큰이 필요합니다. 현재 지원 관할은 부천시이며 본인 관할의 점검만 조회합니다.
신청 ID 내림차순, 같은 신청 ID에서는 점검 ID 내림차순입니다. 조회 중 데이터는 변경하지 않습니다.

## 검색·필터

| 파라미터 | 의미 | 기본값 |
| --- | --- | --- |
| status | ALL(전체) / RECEIVED(배정 대기) / SCHEDULED(예정) / IN_PROGRESS(진행) / COMPLETED(완료) | ALL |
| district | 원미구 / 소사구 / 오정구 | 전체 |
| inspectorUserId | 특정 담당자의 사용자 ID. 양의 정수 | 전체 |
| visitDate | 주민 방문 희망일 하나, yyyy-MM-dd | 방문일 전체 |
| keyword | 원본 전체 신청 주소·접수번호 부분 일치, 최대 255자. 요청 사유·상세주소는 검색하지 않음 | 빈 값 |
| page | 0부터 시작하는 페이지 번호 | 0 |
| size | 페이지 크기 1~100 | 10 |

모든 검색·필터는 AND 조건이며 전체 건수와 페이지 수에도 동일하게 적용합니다.
Swagger 표시 순서는 상태 → 지역 → 담당자 → 방문일 → 주소·접수번호 검색 → 페이지 → 크기입니다.
상태를 생략하거나 ALL로 전달하면 전체입니다. ALL은 조회 전용 옵션이며 실제 점검 상태에는 저장하지 않습니다.
담당자 전체는 inspectorUserId를 생략하고 특정 담당자 선택 시 해당 ID를 전달합니다. 존재하지 않는 담당자 ID는 빈 목록입니다.
방문일 필터는 **확정 일정이 아닌 주민의 방문 희망일** 기준입니다.
visitDate는 날짜 하나를 전달하며 정확히 같은 희망일의 점검만 조회합니다. 날짜를 생략하면 방문일 전체입니다.
희망일이 없는 신청은 날짜 필터 적용 시 제외하고, 날짜 필터가 없으면 포함합니다.
상태와 담당자 배정 여부는 별개입니다. SCHEDULED라도 담당자는 아직 미배정일 수 있습니다.
초기화 시 프론트는 검색·필터를 생략하고 page를 0으로 되돌립니다.

```http
GET /api/admin/inspections?district=원미구&status=RECEIVED&visitDate=2026-11-03&page=0&size=10
```

## 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "INSPECTION_200_3",
  "message": "현장점검 목록을 조회했습니다.",
  "result": {
    "inspections": [
      {
        "inspectionId": 2,
        "applicationId": 5,
        "receiptNo": "000005",
        "address": "원미구",
        "designated": true,
        "preferredVisitDate": "2026-11-03",
        "preferredTimeSlot": "MORNING",
        "inspectorUserId": null,
        "inspectorName": null,
        "status": "RECEIVED"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  },
  "timeStamp": "2026-10-30 10:00:00"
}
```

- receiptNo는 신청 ID를 최소 6자리로 표시하며 긴 ID를 자르지 않습니다. inspectionId와 구분합니다.
- address는 행정구역의 원미구·소사구·오정구만 반환합니다. 구를 식별할 수 없으면 null이며 전체 주소로 대체하지 않습니다.
- 도로명 속 구 이름은 추출하지 않습니다. 원본 신청 주소는 수정하지 않습니다.
- 희망일·희망 시간대·담당자 정보가 없으면 각 필드는 null입니다. 미배정·희망일 미입력 등의 화면 문구는 프론트에서 표시합니다.
- preferredTimeSlot은 MORNING / AFTERNOON / ANY이며 확정 방문 시간과 구분합니다.
- 결과가 없으면 200과 빈 inspections, totalElements=0, totalPages=0입니다.
- 페이지 범위를 초과하면 inspections는 비지만 전체 검색 결과 수와 전체 페이지 수는 유지합니다.
- 오른쪽 상세 패널의 개인정보·배정·상태 변경은 이 목록 API의 응답에 포함하지 않습니다.

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 잘못된 검색·필터·페이지·날짜·담당자 조건 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락·유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | INSPECTION_403_2 | 지원되지 않거나 없는 운영자 관할 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
