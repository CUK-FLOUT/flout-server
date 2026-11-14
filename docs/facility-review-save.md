# 공공시설 검토 내용 저장

`POST /api/admin/public-issues/{publicIssueId}/review`

운영자가 지역 문제의 검토 대상과 검토 내용을 저장합니다. 같은 지역 문제의 기존 검토가 있으면 새 행을 만들지 않고 동일한 `reviewId`로 수정합니다.

## 요청

```json
{
  "reviewTarget": "빗물받이",
  "reviewStatus": "UNDER_REVIEW",
  "inspectionNotes": "빗물받이 막힘 여부와 주변 도로 배수 상태 현장 확인",
  "reviewReason": "인근 신청 3건과 침수 이력 2건 확인"
}
```

- `reviewTarget`: 화면의 검토 대상, 필수 문자열, 앞뒤 공백 제거 후 1~100자
- `reviewStatus`: `PENDING`, `UNDER_REVIEW`, `INSPECTION_REQUIRED` 중 하나
- `inspectionNotes`: 필수, 앞뒤 공백 제거 후 1~4000자
- `reviewReason`: 선택, 입력 시 앞뒤 공백 제거 후 최대 4000자

## 동작

- 최초 저장은 `facility_review`를 생성합니다.
- 다시 저장하면 기존 검토의 검토 대상, 상태, 점검 필요사항, 검토 사유, 검토자와 검토 일시를 수정합니다.
- 검토자는 현재 로그인한 운영자로 기록합니다.
- 검토 상태를 지역 문제 상태와 동기화합니다. `PENDING`은 기존 지역 문제 상태값 `RECEIVED`로 대응합니다.
- 완료된 지역 문제의 검토 내용은 다시 저장할 수 없습니다.

## 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| 400 | COMMON_400_2 | 요청 본문 형식 오류 또는 필수값 검증 실패 |
| 400 | COMMON_400_3 | 공공시설 문제 ID 형식 오류 또는 0 이하 |
| 400 | PUBLIC_ISSUE_400_3 | 저장할 수 없는 검토 상태 |
| 401 | COMMON_401_1 / COMMON_401_3 | 인증 누락 또는 유효하지 않은 토큰 |
| 403 | COMMON_403_1 / INSPECTION_403_1 | 운영자 권한 없음 또는 관할 밖 문제 |
| 404 | PUBLIC_ISSUE_404_1 | 공공시설 문제 없음 |
| 409 | PUBLIC_ISSUE_409_3 | 완료된 문제의 검토 저장 시도 |
