# 운영자 관할 지역

`jurisdictions` 테이블에서 관할 지역을 관리하고, `users.jurisdiction_id`로 운영자의 관할 지역을 연결합니다.

- V8 마이그레이션에서 `부천시`를 등록하고 기존 `ADMIN` 계정을 모두 연결합니다.
- 주민 계정의 `jurisdiction_id`는 `NULL`입니다.
- 이후 운영자 계정을 추가할 때는 아래와 같이 관할 지역도 지정합니다. 주민 회원가입에는 관할 지역을 지정하지 않습니다.

```sql
UPDATE users
SET jurisdiction_id = (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시')
WHERE user_id = :userId AND role = 'ADMIN';
```

## V8 배포 및 중단 시 복구

배포 전에는 운영 DB 백업과 복원 가능 여부를 확인하고, 운영과 동일한 MySQL 및 Flyway 버전의 별도 DB에서 V7까지 적용한 뒤 V8을 검증합니다. 기존 운영자 전체가 부천시에 연결되고 주민은 미지정으로 유지되는지 확인합니다.

`JurisdictionMigrationTest`는 H2에서 정상 완료된 V8이 다시 실행되지 않는지를 확인합니다. MySQL에서 실행 도중 실패했을 때의 복구를 검증하는 테스트는 아닙니다. MySQL의 DDL은 암묵적으로 커밋될 수 있으므로 V8 전체가 자동으로 롤백된다고 가정하면 안 됩니다.

V8 실행이 중단되거나 실패하면 다음 절차로 대응합니다.

1. 애플리케이션 배포와 DB 쓰기를 중단하고, 실패 로그 및 `flyway_schema_history`의 V8 상태를 보관합니다.
2. `SHOW CREATE TABLE jurisdictions`, `SHOW CREATE TABLE users`로 테이블, `jurisdiction_id` 컬럼 및 외래 키의 실제 반영 여부를 확인합니다. 부천시 데이터와 ADMIN 계정의 연결 상태도 확인합니다. 첫 DDL 이전에 실패했다면 테이블이 없을 수도 있습니다.
3. 기본 복구 방법은 배포 전 백업을 별도 DB에 복원하여 V7 상태 및 기존 데이터를 확인한 뒤, 그 DB에 V8을 다시 적용하고 검증 후 전환하는 것입니다. 백업 이후 쓰기가 있었다면 전환 전에 별도 데이터 복구 계획이 필요합니다.
4. 백업 복원 대신 현 DB를 복구해야 한다면 실제 반영된 DDL과 데이터를 기준으로 담당자가 복구 SQL을 검토하고, 같은 실패 상태를 재현한 별도 DB에서 먼저 검증합니다. `repair`는 스키마나 데이터를 원복하지 않으므로 이것만 실행한 뒤 V8을 재실행하지 않습니다. V8 실행 전 상태로 되돌린 사실을 확인한 후에만 동일한 migration 경로와 DB 설정으로 실패 이력을 복구하고 다시 적용합니다.
5. 성공 이력, 운영자 전체의 부천시 연결, 주민의 미지정 상태 및 `/api/admin/me` 응답을 확인한 뒤 서비스를 재개합니다.

이미 성공한 환경의 V8 파일은 변경하지 않습니다. 추가 스키마 수정은 새 버전의 마이그레이션으로 적용합니다.

## GET /api/admin/me

운영자 Access Token을 `Authorization: Bearer {accessToken}` 헤더로 전달합니다. 요청 본문과 파라미터는 없습니다.
토큰으로 로그인한 운영자를 식별하고 현재 DB의 이름과 관할 지역을 반환합니다.

성공 응답의 `result`에는 아래 두 필드만 포함합니다.

```json
{
  "name": "김담당",
  "jurisdiction": "부천시"
}
```

- 성공: `200 / USER_200_3`
- 관할 지역 미지정: `jurisdiction: null`
- 미인증: `401 / COMMON_401_1`
- 유효하지 않거나 만료된 토큰: `401 / COMMON_401_3`
- 주민 계정 접근: `403 / COMMON_403_1`
