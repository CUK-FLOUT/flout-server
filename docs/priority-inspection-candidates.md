# 우선점검 후보 검색·목록 조회

`GET /api/admin/priority-inspections?keyword=&page=0&size=10`

운영자 Bearer 토큰이 필요합니다. 현재 지원 관할은 부천시이며 기존 지정 API와 동일한 주소 기준을 적용합니다.
지정 여부와 관계없이 관할 신청을 조회합니다. 접수번호·주소만 부분 검색하며 신청 ID 내림차순입니다. 요청 사유는 검색 대상에서 제외하고 목록 응답에는 포함합니다.
page는 0 이상, size는 1~100(기본 10), keyword는 최대 255자입니다.

## 화면 필터

| 필터 | 요청 파라미터 | 값 |
| --- | --- | --- |
| 지역 | district | 원미구, 소사구, 오정구 |
| 취약도 | riskLevel | HIGH(높음), CAUTION(주의), LOW(낮음) |
| 우선점검 지정 여부 | designated | true(지정), false(미지정) |

각 필터를 생략하면 전체를 조회합니다. 지역은 빈 값도 전체로 처리합니다.
검색어·지역·취약도·지정 여부는 AND 조건으로 조합하며 전체 건수와 페이지 수에도 동일하게 적용합니다.
지역은 주소의 부천시 다음 행정구역으로 판별하므로 도로명에 포함된 구 이름은 매칭하지 않습니다.
허용되지 않은 필터 값은 400입니다. 인근 신청 건수는 필터를 적용하기 전 관할 전체 신청으로 집계합니다.
필터 변경·초기화 시 프론트는 page를 0으로 되돌립니다. 초기화하면 keyword와 세 필터를 모두 생략합니다.

```http
GET /api/admin/priority-inspections?district=원미구&riskLevel=HIGH&designated=false&page=0&size=10
```

## 인근 신청

신청 좌표 사이의 구면 직선거리를 Haversine 공식(지구 반지름 6,371,000m)으로 계산합니다.
**1,000m 이하**인 같은 관할의 다른 신청을 집계합니다. 본인은 제외하며 같은 좌표의 다른 신청은 포함합니다.
검색어나 페이지 범위는 집계 대상에 적용하지 않습니다. 신청 상태·기간·우선점검 지정 여부도 제한하지 않습니다.
페이지의 신청 ID들을 모아 한 번의 집계 쿼리로 계산합니다. 지도 API는 호출하지 않습니다.
대량 데이터에서 성능 개선이 필요하면 좌표 인덱스와 주변 좌표 범위 필터를 검토합니다.

## 응답

성공 코드는 `PRIORITY_INSPECTION_200_4`이며 공통 응답의 result는 다음 형식입니다.

```json
{
  "applications": [
    {
      "applicationId": 1,
      "receiptNo": "000001",
      "address": "경기도 부천시 원미구 길주로 210",
      "riskLevel": "HIGH",
      "floodExperience": "YES",
      "requestReason": "최근 배수구 역류",
      "nearbyApplicationCount": 3,
      "designated": false
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

접수번호는 신청 ID를 최소 6자리로 표시하며 긴 ID를 자르지 않습니다.
조회 결과가 없거나 페이지 범위를 초과하면 200과 빈 applications 목록을 반환합니다.
잘못된 페이지·크기·검색어는 400, 인증 오류는 401, 운영자 권한이나 관할 권한이 없으면 403입니다.
