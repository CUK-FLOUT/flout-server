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
동의 여부·버전·시각은 유지하며 로그인 시 동의를 다시 받지 않습니다.
