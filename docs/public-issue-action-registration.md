# 공공시설 조치 사진 등록 및 완료 처리

`POST /api/admin/public-issues/{publicIssueId}/actions`

Swagger 그룹: `운영자-공공인프라`

부천시 관할 ADMIN이 조치 내용과 증빙 사진을 한 번에 제출합니다. 사진 저장과 DB 저장이
모두 성공해야 조치·검토·지역 문제 상태가 `COMPLETED`로 변경됩니다. 별도 사진 업로드
API와 상태 변경 API는 제공하지 않습니다.

## 요청

`Content-Type: multipart/form-data`

### request

`application/json` 파트입니다.

```json
{
  "actionDescription": "빗물받이 퇴적물 제거 및 주변 배수로 정비"
}
```

`actionDescription`은 필수이며 앞뒤 공백 제거 후 1~4000자입니다. 상태는 요청으로 받지
않고 서버가 `COMPLETED`로 저장합니다.

### files

- JPG/JPEG/PNG 사진 1장 이상
- 파일당 최대 10MiB, 요청 전체 최대 80MiB
- 확장자뿐 아니라 실제 이미지 내용을 검증
- 여러 장 중 하나라도 실패하면 요청 전체 실패

## 처리 규칙

- 선행 공공시설 검토가 저장되어 있어야 합니다.
- 이미 완료된 문제는 다시 완료할 수 없습니다.
- 외부 저장소 업로드 중에는 DB 트랜잭션을 유지하지 않습니다.
- 업로드 성공 후 권한·완료 여부를 다시 확인하고 조치·사진·검토·문제 상태를 하나의 DB
  트랜잭션으로 저장합니다.
- 실패한 저장소 객체는 업로드 의도 레코드를 통해 정리합니다.
- 원본 현장점검과 주민 신청 상태는 변경하지 않습니다.
- 응답의 사진 URL은 15분 유효한 비공개 URL이며 `Cache-Control: no-store`를 사용합니다.

## 성공 응답

`200 PUBLIC_ISSUE_200_2`

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PUBLIC_ISSUE_200_2",
  "message": "공공시설 조치 완료 처리 성공",
  "result": {
    "actionId": 1,
    "reviewId": 1,
    "publicIssueId": 1,
    "operatorUserId": 2,
    "actionDescription": "빗물받이 퇴적물 제거 및 주변 배수로 정비",
    "actionStatus": "COMPLETED",
    "reviewStatus": "COMPLETED",
    "publicIssueStatus": "COMPLETED",
    "completedAt": "2026-11-12T11:00:00+09:00",
    "images": [
      {
        "imageId": 11,
        "imageUrl": "https://example.test/private-signed-url",
        "uploadedAt": "2026-11-12T11:00:00+09:00"
      }
    ]
  }
}
```

## 오류

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | `COMMON_400_2`, `COMMON_400_3` | 요청 JSON 또는 문제 ID 형식 오류 |
| 400 | `PUBLIC_ISSUE_400_4` | 사진 미선택·빈 파일 |
| 400 | `PUBLIC_ISSUE_400_5` | 이미지 형식·내용 오류 |
| 401 | `COMMON_401_1`, `COMMON_401_3` | 인증 누락 또는 유효하지 않은 토큰 |
| 403 | `COMMON_403_1`, `INSPECTION_403_1` | 운영자 역할 또는 관할 권한 없음 |
| 404 | `PUBLIC_ISSUE_404_1` | 공공시설 문제 없음 |
| 409 | `PUBLIC_ISSUE_409_1` | 이미 완료된 문제 |
| 409 | `PUBLIC_ISSUE_409_2` | 선행 공공시설 검토 없음 |
| 413 | `COMMON_413_1` | 파일 또는 전체 요청 크기 초과 |
| 500 | `PUBLIC_ISSUE_500_1` | 저장소 업로드·DB 저장 실패 |
| 500 | `COMMON_500_1` | 공통 서버 오류 |
