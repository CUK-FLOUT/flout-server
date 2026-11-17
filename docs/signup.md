# 주민 이메일 회원가입

`POST /api/auth/signup` (인증 헤더 불필요)

```json
{
  "email": "resident@example.com",
  "password": "password123",
  "passwordConfirm": "password123",
  "name": "홍길동",
  "thirdPartyProvisionAgreed": true,
  "statusNotificationAgreed": true
}
```

- `thirdPartyProvisionAgreed`: 개인정보 제3자 제공 필수 동의
- `statusNotificationAgreed`: 진행상태 알림 수신 필수 동의

두 필드는 모두 true여야 합니다. false, null 또는 누락이면 400 / COMMON_400_2를 반환하며 회원과 동의 이력을 저장하지 않습니다.
기존 `termsAgreed`, `privacyAgreed` 필드는 더 이상 가입 동의로 사용하지 않습니다.

가입 성공 시 201을 반환하고 역할은 RESIDENT로 고정합니다. 동의는 각각
THIRD_PARTY_PROVISION, STATUS_NOTIFICATION 유형으로 버전·동의 시각과 함께 저장합니다.
버전 설정은 Google 가입과 동일한 `THIRD_PARTY_PROVISION_VERSION`, `STATUS_NOTIFICATION_VERSION`을 사용합니다.
기존 회원의 동의 이력은 V6에서 TERMS → THIRD_PARTY_PROVISION, PRIVACY → STATUS_NOTIFICATION으로 유형만 변경합니다.
동의 여부·버전·시각은 유지하며 V6 이관 자체로 재동의를 강제하지 않습니다.
로그인 응답의 `requiresConsent`는 현재 설정된 버전의 두 필수 동의를 확인합니다.
설정 버전이 변경되어 해당 버전의 동의가 없으면 주민은 로그인 후
`POST /api/resident/consents`에서 두 항목에 다시 동의할 수 있습니다.
같은 회원의 동일 버전 새 유형 기록과 충돌하면 새 기록을 유지합니다.
V6에서 사용했던 임시 보관 테이블 `user_consent_migration_conflicts`는 현재 기능에서 사용하지 않아 V17에서 삭제합니다.
현재 사용하는 `user_consents`의 동의 여부·버전·시각은 변경하지 않습니다.
