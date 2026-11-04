# 운영자 점검 결과 조회

`GET /api/admin/inspections/{inspectionId}/result`

Swagger 그룹: `운영자-현장점검 관리`

## API 설명

부천시 관할 ADMIN이 점검 결과 편집 화면의 저장 내용을 조회합니다.
DRAFT가 있으면 초안을, 없으면 PUBLISHED 공개본을 반환합니다.
두 결과의 내용과 사진을 합치지 않으며, 저장된 결과가 전혀 없으면 `200 / result: null`입니다.
점검 상태에 따른 조회 제한은 없고 조회로 초안을 생성하거나 상태·변경 시각을 변경하지 않습니다.

취약지점과 권장 예방조치는 저장 순서, 현장사진은 등록 순서로 반환합니다.
초안의 미입력 필드는 null일 수 있고 목록은 빈 배열입니다.
사진은 선택된 결과에 연결된 현장사진만 반환하며 주민 신청 첨부사진은 포함하지 않습니다.
사진 객체 경로 대신 임시 열람 URL을 반환합니다. URL은 DB 조회 트랜잭션이 종료된 후 발급합니다.
내부 메모는 운영자 전용입니다. 주민용 API에서 이 응답을 사용하지 않습니다.

## 요청

- Header: `Authorization: Bearer {accessToken}`
- PathVariable: `inspectionId` — 양의 정수인 현장점검 ID. 신청 ID와 구분합니다.
- RequestBody, QueryParam: 없음

## 응답 예시

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "COMMON_200",
  "message": "호출에 성공했습니다.",
  "result": {
    "inspectionId": 1,
    "resultId": 2,
    "resultStatus": "DRAFT",
    "summary": "출입구 빗물 유입에 대비한 조치가 필요합니다.",
    "vulnerabilities": [
      {
        "type": "ENTRANCE",
        "title": "낮은 출입구",
        "expertOpinion": "빗물이 유입될 수 있습니다.",
        "fieldFindings": "차수시설 없음"
      }
    ],
    "recommendedActions": [{ "description": "출입구 물막이판 설치" }],
    "followUpType": "INDIVIDUAL_HOUSE",
    "internalMemo": "지원 범위 확인 필요",
    "savedAt": "2026-11-04T00:30:00+09:00",
    "publishedAt": null,
    "photos": [
      {
        "photoId": 1,
        "fileName": "현장.png",
        "url": "https://example.com/temporary-photo-url",
        "size": 100,
        "contentType": "image/png"
      }
    ]
  },
  "timeStamp": "2026-11-04 00:30:00"
}
```

`savedAt`은 선택된 결과의 마지막 저장 시각입니다. `publishedAt`은 공개 시각이며 초안은 null입니다. 두 값 모두 한국 시간 오프셋으로 반환합니다.
`type`은 ENTRANCE / WINDOW_OPENING / DRAIN / OTHER_STRUCTURE, `followUpType`은 INDIVIDUAL_HOUSE / PUBLIC_AREA입니다.

## 오류

- 400 `COMMON_400_3`: inspectionId가 0 이하이거나 숫자로 변환할 수 없음.
- 401 `COMMON_401_1`: 인증 누락 또는 서비스에서 계정 조회 실패.
- 401 `COMMON_401_3`: 만료·유효하지 않은 토큰.
- 403 `COMMON_403_1`: ADMIN 권한 없음.
- 403 `INSPECTION_403_2`: 관할 미지정·부천시 이외 관할 또는 관할 밖 신청의 점검.
- 404 `INSPECTION_404_1`: 점검 없음. 결과 미저장 상태와 구분합니다.
- 406 `COMMON_406_1`: JSON 응답을 허용하지 않는 Accept.
- 500 `COMMON_500_1`: DB 조회·사진 URL 발급 등 처리되지 않은 서버 오류.
