# 운영자 시공 사진 업로드

`POST /api/admin/constructions/{constructionId}/photos`

지원 승인된 시공 건에 사진을 추가합니다. 부천시 관할 ADMIN만 호출할 수 있으며 경로에는 시공 ID를 입력합니다.

## 요청

`multipart/form-data`의 `files`에 사진을 최소 1장 첨부합니다. 여러 장은 같은 files 키로 전송합니다.

- files 누락·빈 파일·빈 목록은 400입니다. 정상 사진과 빈 파일을 섞어도 전체 요청을 거부합니다.
- JPG/JPEG/PNG, 파일당 최대 10MiB, 요청 전체 최대 80MiB입니다.
- 확장자와 실제 이미지 내용을 검증하며 업로드 시 실제 형식에 맞는 contentType을 저장합니다.
- 경로 문자가 있는 파일명, 손상된 이미지, 확장자와 실제 형식이 다른 이미지는 거부합니다.

## 200 응답의 result 예시

```json
{
  "constructionId": 301,
  "photos": [
    {
      "photoId": 1,
      "fileName": "설치완료.jpg",
      "url": "https://example.test/private-signed-url",
      "size": 102400,
      "contentType": "image/jpeg"
    }
  ]
}
```

공통 응답 형식이며 코드 `CONSTRUCTION_200_2`, 메시지 `시공 사진이 업로드되었습니다.`를 사용합니다. Cache-Control은 no-store입니다.

- photos는 이번 요청으로 추가된 사진만 반환합니다. 기존 사진을 교체하거나 삭제하지 않습니다.
- URL은 15분 유효한 비공개 서명 URL이며 DB에 저장하지 않습니다.
- 사진 등록으로 시공·지원 상태, 담당자, 확정 일정을 변경하거나 주민에게 공개하지 않습니다.
- PENDING/SCHEDULED/IN_PROGRESS에서 등록할 수 있고 전체 완료(COMPLETED) 이후에는 거부합니다.
- 업로드와 URL 발급은 DB 트랜잭션 밖에서 처리하고, 저장 직전에 지원 승인·권한·완료 상태를 잠금 안에서 다시 검증합니다. 잠금 순서는 지원 신청 → 시공입니다.
- 여러 장 중 하나라도 실패하면 새 사진 메타데이터는 모두 롤백하고 업로드 객체는 정리합니다. 정리 실패는 기존 첨부파일 정리 기능으로 재시도합니다.

## 오류

- 400 COMMON_400_3: files 파트 누락 또는 잘못된 시공 ID
- 400 CONSTRUCTION_400_2: 빈 사진 목록 또는 빈 파일
- 400 CONSTRUCTION_400_3: 사진 형식·내용·파일명 오류
- 401 COMMON_401_1 / COMMON_401_3: 미인증 / 유효하지 않거나 만료된 토큰
- 403 COMMON_403_1 / CONSTRUCTION_403_1: 운영자 권한 또는 관할 오류
- 404 CONSTRUCTION_404_1: 시공 건 없음
- 409 CONSTRUCTION_409_1: 지원 승인 상태가 아님
- 409 CONSTRUCTION_409_3: 전체 완료된 시공
- 413 COMMON_413_1: 업로드 용량 초과
- 500 CONSTRUCTION_500_1: 저장소 업로드·URL 발급·DB 저장 실패

Swagger의 운영자-시공관리 → 시공 사진 업로드에서 files를 선택해 테스트할 수 있습니다. 저장된 사진 삭제나 결과 공개 API는 이 변경 범위에 포함하지 않습니다.
