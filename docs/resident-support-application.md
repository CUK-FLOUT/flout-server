# 주민 지원 신청과 이어쓰기

주민은 공개된 현장점검 결과를 바탕으로 지원사업에 신청합니다. 추천 API의 안내는 접수나
승인이 아닙니다. 추천 규칙과 경로 변수의 의미는 [지원사업 AI 매칭](support-program-matching.md)을 참고하세요.

## 작성 중 폼

- `PUT /api/resident/support-form-drafts/{draftId}`: 클라이언트가 생성한 UUID와 본인 현장점검 신청 ID로 폼 전체를 저장합니다. 신규 저장에는 공개된 점검 결과가 필요합니다.
- `GET /api/resident/support-applications/drafts`: 작성 중이거나 신청에 연결된 폼을 최근 수정순으로 조회합니다.

최초 저장의 `version`은 생략하거나 null로 보내고, 갱신에는 마지막 응답의 version을 보냅니다.
`fields`는 최대 16000자의 JSON 객체이며 갱신 시 전체 교체합니다. 최초 저장을 같은 UUID·원본 ID·직렬화된 fields로 재시도하고 폼이 EDITING 버전 1이면 기존 결과를 반환합니다. 기존 신청이나 다른 작성 중 폼이 있거나 버전이 맞지 않으면 409입니다.
신청 제출 시 `X-Form-Draft-Id`에 해당 UUID를 보내면 같은 폼 ID와 동일 요청의 재시도에서 기존 결과를 반환합니다. 폼과 아래 지원 신청의 DRAFT는 서로 다른 상태입니다.

## 지원 신청 등록

`POST /api/resident/support-applications`에 주민 Bearer 토큰과 JSON을 보냅니다.

```json
{
  "applicationId": 101,
  "programId": 1,
  "requestedFacility": "출입구 물막이판",
  "preferredVisitDate": "2030-12-01",
  "preferredVisitTime": "MORNING"
}
```

`applicationId`는 본인 현장점검 신청 ID이고 `programId`는 지원사업 ID입니다. 시설명은
필수이며 최대 255자입니다. 희망일은 오늘 이후이며 시간대는 `MORNING`, `AFTERNOON`, `ANY`입니다.
희망 일정은 확정 시공 일정과 다릅니다. 신청자 정보는 현장점검 신청에서 복사합니다.

서버는 소유권, 공개된 점검 결과, 사업 접수 상태·기간을 검증합니다. 사업 유형에 따라
자격 정보와 신청 시설의 지원·권장 적합성도 검증합니다. 현장점검 신청당 지원 신청은 한 건입니다.
사업 유형이 비어 있는 기존 데이터에는 추가 자격·시설 검증을 적용하지 않습니다. 시설 사업은
접수 상태 OPEN, 시작·종료일과 자격 조건이 확인되어야 하며 신청 시설도 사업 지원시설 및 공개된
점검 결과의 권장시설과 맞아야 합니다.
성공은 201이며 result에는 `supportApplicationId`, `applicationId`, `programId`, `status`,
`submittedAt`, `receiptNumber`, `applicantName`, `programName`, `createdAt`이 포함됩니다.
`receiptNumber`는 원본 현장점검 신청 ID를 최소 6자리로 표시합니다.

## 서류 접수와 조회

TENANT는 신청이 `DRAFT`로 생성되며 `submittedAt`이 null입니다. 본인의
`POST /api/resident/support-applications/{supportApplicationId}/attachments`에 multipart
`file`로 임대인 동의서 한 개를 제출하면 `SUBMITTED`로 접수됩니다. PDF/JPG/JPEG/PNG,
최대 15 MiB이며 중복 제출 또는 DRAFT가 아닌 신청은 409입니다. OWNER·ETC는 바로
`SUBMITTED`이며 이 업로드를 사용할 수 없습니다.

`GET /api/resident/support-applications/{supportApplicationId}/attachments`는 신청 상태와
관계없이 본인의 제출 서류와 15분 유효 열람 URL을 반환하며, 없으면 빈 배열입니다. URL 발급은
객체의 실제 존재를 보장하지 않습니다. 동의서와 승인 정책은 [임대인 동의서 변경](landlord-consent-api-change.md)을 참고하세요.

## 진행 결과

본인 지원 신청에 대해 `/review`는 `SUBMITTED`, `/approval`과 `/schedule`은 `APPROVED`,
`/rejection`은 `REJECTED`일 때 각각 조회합니다. 경로의 접두사는
`/api/resident/support-applications/{supportApplicationId}`이며 해당 상태가 아니면 409입니다.
승인 시 확정 시공 일정이 아직 없으면 날짜·시간은 null입니다. 주민 홈의 통합 단계와 다음 행동은
`GET /api/resident/progress`에서 확인합니다.
