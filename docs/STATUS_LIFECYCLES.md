# 상태 전이와 표시 정책

## 현장점검

`RECEIVED` → `SCHEDULED` → `IN_PROGRESS` → `COMPLETED`가 기본 흐름입니다. 자동 시작·결과 공개는 별도 서비스와 트랜잭션으로 처리합니다.

## 공공인프라 문제

- 현재 공개 상태: `RECEIVED`, `UNDER_REVIEW`, `COMPLETED`.
- Flyway V65: `INSPECTION_REQUIRED` 및 `ACTION_IN_PROGRESS`의 과거 데이터는 `UNDER_REVIEW`로 정규화.
- 시설 검토의 입력 상태·조치 완료 상태는 실제 서비스 전이 검증을 따릅니다.

## 지원사업 신청

- 기본 상태: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`.
- 신청자의 거주 형태 및 증빙 조건에 따라 접수 또는 초안 상태가 달라질 수 있습니다.
- 지원 승인과 시공 완료를 동일 상태로 표시하지 않습니다.

## 시공

- 실제 `construction.status`, 일정·완료 일시와 사진 저장 결과를 기준으로 표시합니다.
- 목록의 상태와 화면용 통합 진행 단계는 서로 다를 수 있으므로 응답 정의를 확인합니다.

> 전이·사전조건·동시성 제어는 `entity`, `service`, `repository` 및 테스트가 최종 소스입니다. UI에서 임의로 상태를 추정하지 마세요.
