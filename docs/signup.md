# 주민 이메일 회원가입

`POST /api/auth/signup` (인증 헤더 불필요)

```json
{
  "email": "resident@example.com",
  "password": "password123",
  "passwordConfirm": "password123",
  "name": "홍길동"
}
```

가입 성공 시 201을 반환하고 역할은 RESIDENT로 고정합니다. 가입 요청에는 동의 필드가
없으며 가입과 동의 저장은 별도입니다. 로그인한 주민은 `requiresConsent`가 true이면
`POST /api/resident/consents`에 Bearer 토큰과 다음 본문을 보내 동의합니다.

```json
{
  "thirdPartyProvisionAgreed": true,
  "statusNotificationAgreed": true
}
```

두 값이 모두 true여야 하며 false·null·누락이면 400 / COMMON_400_2입니다. 성공 시
`result.requiresConsent`는 false입니다. 현재 설정된 버전의 `THIRD_PARTY_PROVISION`,
`STATUS_NOTIFICATION` 동의가 같은 트랜잭션에 저장됩니다. 버전은 각각
`THIRD_PARTY_PROVISION_VERSION`, `STATUS_NOTIFICATION_VERSION`으로 설정합니다.
Google 로그인 주민도 같은 동의 API를 사용합니다.

## 기존 동의 이력

기존 회원의 동의 이력은 V6에서 TERMS → THIRD_PARTY_PROVISION, PRIVACY → STATUS_NOTIFICATION으로 유형만 변경합니다.
동의 여부·버전·시각은 유지하며 V6 이관 자체로 재동의를 강제하지 않습니다.
로그인 응답의 `requiresConsent`는 현재 설정된 버전의 두 필수 동의를 확인합니다.
설정 버전이 변경되어 해당 버전의 동의가 없으면 주민은 로그인 후
`POST /api/resident/consents`에서 두 항목에 다시 동의할 수 있습니다.
같은 회원의 동일 버전 새 유형 기록과 충돌하면 새 기록을 유지합니다.
V6에서 사용했던 임시 보관 테이블 `user_consent_migration_conflicts`는 현재 기능에서 사용하지 않아 V17에서 삭제합니다.
현재 사용하는 `user_consents`의 동의 여부·버전·시각은 변경하지 않습니다.
