# API 공통 규약

## API 응답

프로젝트의 성공/오류 응답은 각 Controller와 공통 `SuccessResponse`, 오류 코드 정의를 기준으로 합니다. 단일 문서에서 모든 오류 코드가 동일하다고 가정하지 않습니다.

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "<도메인별_성공_코드>",
  "message": "<성공 메시지>",
  "result": {},
  "timeStamp": "2026-11-17 12:00:00"
}
```

`status`는 HTTP 상태 코드와 같은 정수이고, `timeStamp`는 **서울 시간(Asia/Seoul)** 기준 응답 생성 시각입니다. 형식은 `yyyy-MM-dd HH:mm:ss`이며 UTC 오프셋은 포함하지 않습니다. `204 No Content`를 반환하는 API는 envelope 본문이 없을 수 있으므로 Controller의 실제 반환형을 확인합니다.

## 인증

일반적으로 보호된 API에 `Authorization: Bearer <accessToken>`을 전달합니다. 서버는 JWT 주체로 사용자 ID를 식별하며 클라이언트가 임의의 관리 대상 사용자 ID를 보내도록 설계하지 않습니다. 관리자 API는 역할 및 관할 검증을 추가합니다.

## 날짜·시간

- 타임스탬프 응답은 `+09:00` 포함 여부를 DTO별로 확인합니다.
- `LocalDateTime`을 UTC로 저장하는지, 서울 현지 시각으로 저장하는지 운영 DB·JVM 정책을 혼용하면 9시간 편차가 발생할 수 있습니다.
- `SeoulDateTimes.toOffset`은 입력 `LocalDateTime`을 서울 현지 시각으로 해석합니다.

## 신청 접수번호

- 현장점검 신청의 `applicationId`는 숫자형 PK입니다.
- 일부 API의 `receiptNo` / `receiptNumber`는 이를 **최소 6자리**로 표시한 문자열입니다 (`10 → "000010"`).
- `inspection_applications.receipt_no`에는 `IA-...` 형태의 별도 식별자가 저장될 수 있습니다.
- 표시값으로 검색하거나 고객센터에서 조회하는 기능은 **API별 계약**을 확인해야 합니다.

## 페이지네이션·파일

- 페이지 관련 `page/size` 범위, 정렬, 필터는 각 조회 Controller의 실제 검증을 따릅니다.
- 사진 등록 API는 `multipart/form-data` 또는 별도의 업로드 API를 사용하므로 JSON-only 호출로 가정하지 마세요.
- S3 서명 URL은 유효기간이 있는 임시 링크입니다. 영구 객체 키와 구별하세요.
