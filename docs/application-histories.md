# 운영자 신청 상세 — 처리 이력 조회

GET `/api/admin/applications/{applicationId}/histories`

부천시 관할 ADMIN이 현장점검 신청부터 지원·시공까지 처리 이력을 조회합니다. 경로는 현장점검 신청 ID이며 요청 본문·쿼리 파라미터는 없습니다. 지원 자격 검토 화면용 `/api/admin/support-applications/{supportApplicationId}/histories`와 다른 API입니다.

200 `APPLICATION_200_7`: `신청의 처리 이력을 조회했습니다.`

result는 `applicationId`, `receiptNo`, `applicantName`, `totalCount`, `histories`입니다. 이력이 없으면 totalCount=0, histories=[]로 200을 반환합니다.

각 histories 항목은 다음 값을 반환합니다.

```json
{
  "eventId": "construction:3",
  "eventType": "CONSTRUCTION_COMPLETED",
  "statusLabel": "시공완료",
  "title": "시공 완료",
  "description": "최종 설치 결과를 저장하고 전체 완료했습니다.",
  "occurredAt": "2026-11-10T11:30:00+09:00",
  "actorType": "ADMIN",
  "actorId": 6,
  "actorName": "김운영",
  "inspectionId": 19,
  "supportApplicationId": 201,
  "constructionId": 301
}
```

발생 시각 최신순이며 같은 시각이면 단계 역순, 지원 신청 ID 역순, eventId 역순으로 정렬합니다. eventId는 화면 목록의 안정적인 키로 사용할 수 있습니다. 금액·주소·이름을 임의 생성하거나 마스킹하지 않습니다.

## 단계

- APPLICATION_RECEIVED → 접수: 현장점검 신청의 실제 submittedAt. 신청자 RESIDENT.
- INSPECTION_SCHEDULED → 점검 예정: 담당자·일정 배정의 실제 상태 변경 이력. ADMIN.
- INSPECTION_IN_PROGRESS → 점검 진행: 방문 시각 도래에 따른 실제 전환 이력. 자동 전환은 SYSTEM.
- INSPECTION_COMPLETED → 점검 완료: 실제 PUBLISHED 결과의 publishedAt. 단순 초안 저장·사진 업로드·공개되지 않은 과거 COMPLETED 상태만으로 완료 이력을 만들지 않습니다.
- SUPPORT_SUBMITTED → 지원 검토중: 실제 지원 submittedAt. TENANT의 DRAFT 생성은 제외하며 동의서 제출로 접수된 시각을 사용합니다. 별도 검토 시작 단계는 없습니다.
- SUPPORT_APPROVED / SUPPORT_REJECTED → 지원 승인 / 지원 미승인: 저장된 결정 이력과 처리자.
- CONSTRUCTION_SCHEDULED → 시공 예정: 최초 배정·일정 저장 시각과 운영자.
- CONSTRUCTION_IN_PROGRESS → 시공 진행: 확정 시각이 되어 실제 자동 전환된 시각. SYSTEM.
- CONSTRUCTION_COMPLETED → 시공완료: 전체 완료 POST 성공 시각과 운영자.

항상 모든 단계가 존재하는 것은 아닙니다. 진행 전·미승인 후·진행 전 전체 완료 등 실제로 발생하지 않은 단계는 생략합니다. 여러 지원사업에 신청했다면 접수된 모든 지원 신청의 실제 이력을 함께 반환하며 supportApplicationId로 구분합니다. 지원·현장점검 DRAFT를 처리 이력으로 노출하지 않습니다.

## 처리자와 과거 데이터

actorType은 RESIDENT·ADMIN·SYSTEM·UNKNOWN입니다. 시스템은 actorId=null, actorName=시스템입니다. 과거 기록에 실제 처리자가 없으면 UNKNOWN과 null을 반환하며 담당자를 처리자로 추정하지 않습니다.

점검 공개 처리자를 새로 저장해 이미 COMPLETED였던 점검의 결과 공개도 실제 공개자로 표시합니다. 기존 결과는 공개 시각과 정확히 일치하는 완료 이력이 있을 때만 해당 처리자를 사용하고, 없으면 UNKNOWN입니다.

V40 이전에 변환된 RECEIVED 이력만으로 실제 신청 접수 시각을 단정할 수 없어 submittedAt이 없는 접수는 생성하지 않습니다. 시공은 V45부터 배정·자동 진행·전체 완료 이력을 상태 변경과 같은 트랜잭션에 저장합니다. V45 이전에는 실제 completedAt이 있는 완료 건만 LEGACY 이력으로 보존하고, 예정 시각·mutable updatedAt으로 배정·진행 시각을 만들어내지 않습니다.

## 오류

- 400 COMMON_400_3: 잘못된 신청 ID.
- 401 COMMON_401_1 / COMMON_401_3: 미인증 / 만료·유효하지 않은 토큰.
- 403 COMMON_403_1: 운영자 권한 없음.
- 403 APPLICATION_403_4: 본인 관할 지역의 신청만 조회할 수 있습니다.
- 404 APPLICATION_404_2: 신청 없음 또는 현장점검 DRAFT.
- 500 COMMON_500_1: 서버·DB 처리 오류.

Cache-Control은 no-store입니다. 조회로 처리 상태나 이력을 변경하지 않으며 이 구현은 알림 목록 저장·푸시 발송을 연결하지 않습니다.
