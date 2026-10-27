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
