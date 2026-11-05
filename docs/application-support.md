# 운영자 신청 상세 — 지원 정보 조회

GET `/api/admin/applications/{applicationId}/support`

부천시 관할 ADMIN이 GIS 신청 상세의 지원 정보 탭을 조회합니다. 경로는 현장점검 신청 ID이며 지원 신청·점검·시공 ID와 구분합니다. 요청 본문과 쿼리 파라미터는 없습니다.

200 `APPLICATION_200_5`: `신청의 지원 정보를 조회했습니다.`

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "APPLICATION_200_5",
  "message": "신청의 지원 정보를 조회했습니다.",
  "result": {
    "applicationId": 101,
    "receiptNo": "000101",
    "applicantName": "홍길동",
    "address": "경기도 부천시 원미구 길주로 53",
    "detailAddress": "101호",
    "housingType": "SEMI_BASEMENT",
    "support": {
      "supportApplicationId": 201,
      "programId": 1,
      "programName": "침수 예방시설 설치 지원",
      "status": "APPROVED",
      "requestedFacility": "출입구 물막이판",
      "supportScope": "출입구 물막이판 설치 지원",
      "landlordConsentRequired": true,
      "landlordConsentDocumentSubmitted": true,
      "constructionId": 301,
      "constructionStatus": "IN_PROGRESS",
      "rejectionReason": null
    }
  },
  "timeStamp": "2026-11-05 14:00:00"
}
```

- 미신청: `support: null`로 200을 반환합니다. 지원 DRAFT만 있는 경우도 접수 전이므로 동일합니다. 기존 현장점검 신청은 있어야 합니다.
- 검토 중: `support.status`에 실제 SUBMITTED 상태를 반환합니다. 승인 범위·미승인 사유·시공 연계는 null입니다.
- 승인: `status: APPROVED`, `supportScope`에 DB 저장된 승인 범위를 반환합니다. 시공 건이 있으면 실제 constructionId·constructionStatus를 반환하고 없으면 둘 다 null입니다. 승인만으로 진행 중 상태를 추정하지 않습니다.
- 미승인: `status: REJECTED`, `rejectionReason`에 운영자가 저장한 사유를 반환합니다. 승인 범위·시공 연계는 null입니다. 고정 미승인 문구를 서버에서 임의 생성하지 않습니다.

화면 제목·주소는 기존 GIS 신청 정보 탭과 같은 원본 신청 값을 사용합니다. `housingType`은 지원 신청 여부와 관계없이 연결된 침수 취약도 분석에 저장된 거주층이며, 미입력 시 null입니다. 여러 사업에 신청했으면 DRAFT를 제외하고 접수 시각 내림차순, 같으면 지원 신청 ID 내림차순의 한 건을 선택합니다.

임대인 동의 필요 여부는 지원 신청 당시 TENANT이면 true, OWNER·ETC이면 false입니다. `landlordConsentDocumentSubmitted`는 해당 지원 신청에 LANDLORD_CONSENT 파일이 실제 존재하는지 나타내며 운영자 확인 완료 상태를 의미하지 않습니다. 현재 승인 로직처럼 동의서 제출과 지원 승인 결과를 구분합니다. 파일 URL은 이 API에 포함하지 않습니다.

지원 자격 검토로 이동할 때 `supportApplicationId`를 사용해 기존 `/api/admin/support-applications/{supportApplicationId}/histories`를 호출할 수 있습니다. 시공 상태는 PENDING=미배정, SCHEDULED=예정, IN_PROGRESS=진행 중, COMPLETED=전체 완료입니다.

- 400 `COMMON_400_3`: 신청 ID 형식 오류.
- 401 `COMMON_401_1` / `COMMON_401_3`: 미인증 / 만료·유효하지 않은 토큰.
- 403 `COMMON_403_1`: 운영자 권한 없음.
- 403 `APPLICATION_403_4`: 본인 관할 지역의 신청만 조회할 수 있습니다.
- 404 `APPLICATION_404_2`: 현장점검 신청을 찾을 수 없습니다. 현장점검 DRAFT도 운영자 상세에서 제외합니다.
- 500 `COMMON_500_1`: 서버·DB 처리 오류.

응답 헤더는 `Cache-Control: no-store`이며 조회로 지원 승인·미승인·동의서 제출·시공 상태를 변경하지 않습니다.
