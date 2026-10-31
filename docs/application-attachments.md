# 신청 첨부사진 조회

`GET /api/admin/applications/{applicationId}/attachments`

운영자 Bearer 토큰과 양의 정수 신청 ID가 필요합니다. 본인 관할 신청만 조회하며 현재 지원 관할은 부천시입니다.
요청 본문과 쿼리 파라미터는 없습니다. Swagger 그룹은 `운영자-GIS 신청관리`입니다.

## 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "APPLICATION_200_3",
  "message": "신청 첨부사진을 조회했습니다.",
  "result": [
    {
      "attachmentId": 1,
      "originalFilename": "출입구.jpg",
      "imageUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg?X-Amz-Signature=..."
    }
  ],
  "timeStamp": "2026-10-31 12:00:00"
}
```

- 첨부 ID 오름차순으로 반환하며 사진이 없으면 `result`는 `[]`입니다.
- 신청자가 등록한 사진만 반환합니다. 파일명은 화면 표시용입니다.
- `imageUrl`은 비공개 S3 객체의 서명된 GET URL이며 최대 15분간 유효합니다. 임시 AWS 자격증명이 먼저 만료되면 URL도 먼저 만료됩니다.
- 프론트에서 `imageUrl`을 이미지 주소로 사용하고, 만료 시 이 API를 다시 호출합니다.
- 응답은 `Cache-Control: no-store`이며 URL을 영구 저장하거나 로그에 남기지 않습니다.
- 기존 업로드와 동일한 `AWS_REGION`, `AWS_S3_BUCKET`, AWS 자격증명을 사용합니다. 서버 자격증명에 해당 객체의 `s3:GetObject` 권한이 필요합니다.
- 조회는 신청·첨부사진 데이터를 변경하지 않습니다.

## 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_3 | 신청 ID 형식 오류 또는 0 이하 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락 또는 유효하지 않은 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | APPLICATION_403_4 | 관할 미설정·미지원 또는 관할 밖 신청 |
| 404 | APPLICATION_404_2 | 신청 없음 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
| 500 | APPLICATION_500_3 | 이미지 URL 발급 실패 |
