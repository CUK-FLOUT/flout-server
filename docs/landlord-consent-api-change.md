# 임대인 동의서 API 변경

동의서 제출과 운영자의 지원 승인 여부는 별개입니다. 별도 임대인 동의 확인 단계는 없습니다.

운영자 `GET /api/admin/support-applications`의 각 항목 및 `GET /api/admin/support-applications/{supportApplicationId}` 응답에서 `landlordConsentStatus`가 제거되고 다음 두 필드가 추가됩니다. 검토 화면용 `GET /api/admin/support-applications/{supportApplicationId}/histories`에도 제출 여부 필드가 추가됩니다.

```json
{
  "landlordConsentRequired": true,
  "landlordConsentDocumentSubmitted": true
}
```

`landlordConsentRequired`는 지원 신청의 점유 유형이 TENANT일 때만 true입니다. `landlordConsentDocumentSubmitted`는 해당 신청의 LANDLORD_CONSENT 첨부 레코드가 있을 때 true입니다. 두 값은 독립적이며 기존 데이터의 점유 유형과 첨부 상태를 그대로 반영합니다. 첨부 여부는 저장소 객체의 실시간 존재 검사가 아닙니다. 목록의 `landlordConsentStatus` 쿼리 필터는 제거되었습니다. 기존 필터 값을 보내도 제출 여부 필터로 해석되지 않습니다. 기존 현장점검 지원정보 API의 동일 필드는 유지됩니다.

운영자 시공 상세, 주민 지원 검토·승인·일정·첨부 응답과 주민 신청 진행 요약에서도 `landlordConsentStatus`가 제거됩니다. 주민의 서류 제출 여부는 `GET /api/resident/support-applications/{supportApplicationId}/attachments`의 `attachments`에서 `attachmentType: LANDLORD_CONSENT`를 확인합니다. 이 API는 첨부가 없으면 빈 배열을 반환합니다.

TENANT는 지원 신청 생성 후 DRAFT 상태에서 첨부파일 제출에 성공하면 SUBMITTED가 됩니다. OWNER·ETC는 바로 SUBMITTED이며 첨부파일을 제출하지 않습니다. 운영자 목록·상세·집계는 DRAFT를 제외하고, 승인 시에는 TENANT의 동의서 첨부 존재 여부를 검사합니다. 승인·미승인은 SUBMITTED 상태에서만 가능합니다. 기존 동의 확인 PATCH API는 제공하지 않습니다.

Flyway V57은 기존 신청 및 첨부 행을 보존하면서 사용하지 않는 `landlord_consent_status` 컬럼을 삭제합니다. 배포 시 해당 필드와 목록 필터를 사용하는 클라이언트를 함께 갱신해야 합니다.
