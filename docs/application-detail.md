# GIS 신청 상세정보 조회

`GET /api/admin/applications/{applicationId}`

운영자 Bearer 토큰이 필요하며 현재 지원 관할은 부천시입니다. 신청 ID는 양의 정수이며 점검 ID와 구분합니다.
Swagger 그룹은 `운영자-GIS 신청관리`입니다. 요청 본문·쿼리 파라미터는 없습니다.

## 응답 예시

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "APPLICATION_200_2",
  "message": "현장점검 신청 상세정보를 조회했습니다.",
  "result": {
    "applicationId": 1,
    "receiptNo": "000001",
    "applicantName": "김주민",
    "contact": "010-1234-1234",
    "address": "경기도 부천시 원미구 길주로 53",
    "detailAddress": "101호",
    "occupancyType": "TENANT",
    "riskLevel": "HIGH",
    "floodExperience": "YES",
    "inflowLocations": ["BATHROOM", "DRAIN"],
    "existingFacility": false,
    "preferredVisitDate": "2026-11-03",
    "requestReason": "최근 배수구 역류가 있어 점검을 희망합니다.",
    "preferredTimeSlot": "MORNING"
  },
  "timeStamp": "2026-10-31 10:00:00"
}
```

## 화면 표시 규칙

| 필드 | 의미·표시 예시 |
| --- | --- |
| receiptNo | 신청 ID를 최소 6자리로 표시. 긴 ID는 자르지 않음 |
| applicantName / contact | 신청자 계정 이름 / 신청에 입력한 연락처 |
| address / detailAddress | 신청에 저장된 전체 주소 / 상세주소 |
| occupancyType | OWNER=자가, TENANT=세입자, ETC=기타 |
| riskLevel | 연결된 분석 결과. HIGH=높음, CAUTION=주의, LOW=낮음 |
| floodExperience | YES=침수 경험 있음, NO=침수 경험 없음, UNKNOWN=모름 |
| inflowLocations | WINDOW=창문, ENTRANCE=출입구, BATHROOM=화장실, DRAIN=배수구, ETC=기타, UNKNOWN=모름 |
| existingFacility | true=기존 시설 있음, false=기존 시설 없음 |
| preferredVisitDate | 주민 방문 희망일. yyyy-MM-dd |
| requestReason | 주민 점검 요청 사유 |
| preferredTimeSlot | MORNING=오전, AFTERNOON=오후, ANY=상관없음 |

- 반지하는 프론트 기본 표시이며 별도 층수 필드는 반환하지 않습니다.
- 화면의 시간 범위 표시는 프론트에서 처리하며 별도 시각 데이터는 반환하지 않습니다.
- 상세주소·희망일·시간대·요청 사유가 없으면 null, 유입 위치가 없으면 빈 배열입니다.
- 희망 일정은 확정 방문 일정과 다릅니다. 조회는 데이터를 변경하지 않습니다.
- 첨부사진·처리 이력·지원·시공 정보는 이 API의 응답에 포함하지 않습니다.

## 오류

| HTTP | 코드 | 메시지 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 요청 매개변수의 형식이 잘못되었습니다. |
| 401 | COMMON_401_1 | 인증되지 않은 유저는 해당 리소스에 접근할 수 없습니다. |
| 401 | COMMON_401_3 | 유효하지 않은 접근 토큰입니다. |
| 403 | COMMON_403_1 | 해당 리소스에 대한 접근 권한이 없습니다. |
| 403 | APPLICATION_403_4 | 본인 관할 지역의 신청만 조회할 수 있습니다. |
| 404 | APPLICATION_404_2 | 현장점검 신청을 찾을 수 없습니다. |

관할 미설정·미지원 관할 또는 관할 밖 주소인 경우 APPLICATION_403_4를 반환합니다.
