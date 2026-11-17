# 데이터베이스 및 Flyway 마이그레이션

## 기본 원칙

- 저장소: MySQL 8.4 기반, Flyway 순차 SQL 마이그레이션.
- 이미 운영 DB에 반영된 SQL 파일은 수정하지 않고 새 버전의 마이그레이션으로 보완합니다.
- 운영 반영 전 백업·복구 검증, 운영과 동일한 MySQL 버전에서 사전 검증, CI 통과 및 서비스 호환성 확인이 필요합니다.
- MySQL DDL은 트랜잭션이 자동으로 롤백된다고 가정하지 않습니다.

## 최근 주요 마이그레이션

- `V55`: 주민 폼 임시 저장 관련 테이블
- `V57`: 임대인 동의 상태 컬럼 제거 (구버전 서버 호환성 주의)
- `V60`~`V64`: 지원사업 컬럼 및 참고 데이터 보강
- `V65`: 공공인프라 상태값 정규화 및 ENUM 변경
- `V66`: 시연용 시설 지원사업 3종. **현재 dev에 파일이 존재하는 경우에만 해당**하며, 존재하지 않으면 미반영 상태로 처리합니다.

## 배포 전 확인

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank DESC;
```

- 서비스 기동 전 최신 마이그레이션 적용 가능 여부를 검사합니다.
- 운영 DB에서 테스트 데이터가 실제 지원사업처럼 노출되지 않도록 정책을 적용합니다.
- 스키마 복구가 필요한 경우 Java JAR만 이전 버전으로 되돌리는 것으로는 부족할 수 있습니다.

관련: [EC2_DEPLOYMENT.md](EC2_DEPLOYMENT.md), [DEMO_DATA_POLICY.md](DEMO_DATA_POLICY.md).
