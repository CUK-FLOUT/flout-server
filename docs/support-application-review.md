# 운영자 지원 자격 검토 정보 조회

`GET /api/admin/support-applications/{supportApplicationId}/histories`

지원 자격 검토 화면의 접수번호·신청자·주소·거주 형태, 점검 결과, 주민 요청사항, 희망 일정, 지원사업과 첨부서류를 한 번에 조회합니다. 이 경로는 검토 화면 데이터이며 상태 변경 이력 목록을 반환하지 않습니다. 부천시 관할 ADMIN만 조회할 수 있습니다.

요청 본문과 쿼리 파라미터는 없습니다. 경로에는 점검 ID가 아닌 지원 신청 ID를 입력합니다.

## 200 응답의 result 예시

```json
{
  "supportApplicationId": 201,
  "applicationId": 2,
  "receiptNo": "000002",
  "applicantName": "이주민",
  "contact": "010-****-0002",
  "address": "경기도 부천시 원미구 길주로 210",
  "detailAddress": "101호",
  "occupancyType": "TENANT",
  "vulnerabilityTypes": ["ENTRANCE", "DRAIN"],
  "inspectionId": 3,
  "inspectionStatus": "COMPLETED",
  "inspectionResultPublished": true,
  "recommendedActions": ["출입구 물막이판 설치", "배수구 정비"],
  "requestedFacility": "출입구 물막이판",
  "residentRequest": "출입구 통행을 확보해주세요",
  "preferredConstructionDate": "2026-11-10",
  "preferredTimeSlot": "MORNING",
  "landlordConsentRequired": true,
  "program": {
    "programId": 1,
    "name": "침수 예방시설 설치 지원",
    "description": "침수 예방시설 설치를 지원합니다.",
    "targetDescription": "부천시 침수 취약 주택",
    "operatingStatus": "OPEN"
  },
  "supportScope": null,
  "attachments": [
    {
      "originalName": "임대인 동의서.pdf",
      "fileUrl": "https://example.test/private-signed-url"
    }
  ],
  "status": "CONSENT_PENDING"
}
```

- 이름·연락처·주소·거주 형태는 지원 신청 당시 저장값입니다. 상세주소와 `residentRequest`는 연결된 현장점검 신청에서 조회하며 요청 사유가 없으면 null입니다. 반지하 등 주택 유형은 반환하지 않습니다.
- 취약지점과 권장조치는 PUBLISHED 결과만 사용하며 초안을 노출하지 않습니다. 배열은 저장 순서를 유지하고 취약지점의 중복 유형은 제외합니다. 점검이나 공개 결과가 없으면 빈 배열입니다.
- `landlordConsentRequired`는 세입자이면 true입니다. 동의서 확인 완료 여부를 뜻하지 않습니다.
- 희망 방문일·시간은 지원 신청에 입력한 설치 희망 일정이며 확정 일정이 아닙니다.
- 첨부파일은 실제 저장된 서류만 조회합니다. 파일명과 15분 유효 비공개 서명 URL을 반환하며, 첨부가 없으면 빈 배열입니다. 신청서 PDF를 임의로 만들지 않습니다.
- 승인된 지원 범위가 없으면 `supportScope`는 null입니다. 기존 SUBMITTED 상태는 상세 조회와 동일하게 REVIEWING으로 표시하며 조회로 저장 상태를 변경하지 않습니다.

## 오류

- 400 `COMMON_400_3`: 양의 Long 형식이 아닌 지원 신청 ID
- 401 `COMMON_401_1` / `COMMON_401_3`: 미인증 / 유효하지 않거나 만료된 토큰
- 403 `COMMON_403_1`: 운영자 권한 또는 지원하는 관할 없음
- 404 `SUPPORT_404_1`: 없는 신청 또는 다른 관할 신청
- 500 `SUPPORT_DETAIL_500_1`: 첨부파일 조회 URL 발급 실패

기존 공통 응답 형식을 사용하며 성공 코드는 `COMMON_200`입니다. 기존 상세 조회와 승인·미승인·임대인 동의 처리 API는 변경하지 않습니다.
