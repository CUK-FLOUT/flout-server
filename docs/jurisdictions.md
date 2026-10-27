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

운영자 이름과 관할 지역을 반환하는 조회 API는 이번 DB 변경에 포함하지 않습니다.
