# 운영자 점검 상태 변경

## PATCH /api/admin/inspections/{inspectionId}/status

새 신청은 제출 즉시 신청·점검을 모두 `RECEIVED`(접수)로 생성합니다.
점검 ID는 신청 성공 응답의 `inspectionId`로 확인합니다. [신청 흐름](inspection-application.md)을 참고하세요.

Swagger 그룹은 `운영자-현장점검 관리`입니다. 운영자 Access Token을
`Authorization: Bearer {accessToken}` 헤더로 전달합니다.
`inspectionId`는 신청 ID가 아닌 `inspections.inspection_id`이며 양의 정수입니다.

```json
{
  "status": "SCHEDULED"
}
```

| 요청 상태 | 화면 표시 |
| --- | --- |
| SCHEDULED | 점검 예정 |
| IN_PROGRESS | 점검 진행 |
| COMPLETED | 점검 완료 |

- 초기 `RECEIVED`(접수) 상태에서 `SCHEDULED`(점검 예정) → `IN_PROGRESS`(점검 진행) → `COMPLETED`(점검 완료) 순서로 변경합니다.
- 단계 건너뛰기와 이전 상태로 되돌리기는 409를 반환합니다.
- **담당자와 방문 일정을 배정하지 않아도 예정으로 변경할 수 있습니다.**
- 진행으로 수동 변경하려면 `scheduled_at`에 확정 방문 일시가 있어야 하며, 한국 시간 기준 해당 일시 이상이어야 합니다. 미지정 또는 시간이 되기 전에는 409입니다.
- 서버는 기본 5초 주기로 확정 방문 일시가 지난 예정 점검을 `IN_PROGRESS`로 저장합니다. 서버 중지 중 시간이 지난 경우에도 다음 실행 시 처리합니다.
- 자동 처리에도 변경 이력을 남기며 `change_source=AUTO`, 처리자 ID는 NULL입니다. 수동 처리는 `MANUAL`과 운영자 ID를 저장합니다.
- `POST /api/admin/inspections/{inspectionId}/assignment`로 담당자·방문 일정·방문 전 안내를 저장합니다. DB에 예정 상태가 먼저 저장되어 있어야 합니다. [배정 API 설명](inspection-assignment.md)을 참고하세요.
- 같은 상태로 재요청하면 200을 반환하고 시각을 갱신하거나 이력을 중복 저장하지 않습니다.
- 점검 행 잠금으로 동시 요청을 순차 처리합니다. 동시에 같은 상태를 요청하면 모두 200이지만 실제 변경 이력은 한 건만 생성됩니다.
- 변경 전후 상태, 처리자 ID, 처리 시각을 `inspection_status_histories`에 기록합니다.
- 상태 변경과 이력은 같은 트랜잭션이며 이력 저장 실패 시 상태도 롤백합니다.
- 자동 전환 실패도 상태와 이력을 롤백하고 다음 주기에 재시도합니다. 배치당 최대 100건을 처리합니다.
- 신청 상태, 내부 메모, 점검 결과 또는 주민 공개 상태를 변경하지 않습니다. 알림도 발송하지 않습니다.

### 권한과 관할

현재 DB 계정이 운영자이고 관할이 부천시인 경우만 허용합니다.
점검에 연결된 신청 주소가 `부천시`, `경기 부천시`, `경기도 부천시`로 시작하는지 확인합니다.
행정구역 이름 뒤의 공백 또는 문자열 끝을 확인하며 다른 지역·불명확한 주소·관할 미지정은 403입니다.

### 성공 응답

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "INSPECTION_200_1",
  "message": "점검 상태가 저장되었습니다.",
  "result": {
    "inspectionId": 1,
    "status": "SCHEDULED",
    "updatedAt": "2026-10-28T19:00:00"
  },
  "timeStamp": "2026-10-28 19:00:00"
}
```

### 오류

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 400 | COMMON_400_2 | 상태 누락·null·알 수 없는 값·RECEIVED 복귀 요청 또는 잘못된 본문 |
| 400 | COMMON_400_3 | 점검 ID 형식·범위 오류 |
| 401 | COMMON_401_1 | 토큰 누락 |
| 401 | COMMON_401_3 | 유효하지 않거나 만료된 토큰 |
| 403 | COMMON_403_1 | 운영자 권한 없음 |
| 403 | INSPECTION_403_1 | 관할 외 점검 또는 관할 미지정 |
| 404 | INSPECTION_404_1 | 점검 없음 |
| 409 | INSPECTION_409_1 | 허용되지 않은 상태 전이 |
| 409 | INSPECTION_409_2 | 확정 방문 일시 미지정 또는 방문 시간 전 진행 변경 |
| 406 | COMMON_406_1 | 지원하지 않는 응답 형식 |
| 415 | COMMON_415_1 | 지원하지 않는 요청 본문 형식 |
| 500 | COMMON_500_1 | 서버 내부 오류 |

### DB

V13에서 점검과 점검 상태 이력 테이블을 생성합니다.
V16은 과거 `SUBMITTED` 신청과 `WAITING` 점검·이력을 `RECEIVED`(접수)로 통일합니다.
기존 점검의 일정·변경 시각과 예정·진행·완료 상태는 보존합니다.
점검이 없는 기존 신청에만 접수 점검을 생성합니다.
새 신청은 제출 트랜잭션에서 `Inspection.received(application)`을 함께 저장합니다.
이 상태 변경 API는 없는 점검을 자동 생성하지 않습니다.
확정 방문 일시는 별도 배정·일정 저장 API에서 `inspections.scheduled_at`에 저장해야 합니다.

### 자동 전환 설정

- `INSPECTION_AUTO_START_ENABLED`: 기본 true
- `INSPECTION_AUTO_START_INTERVAL_MILLIS`: 기본 5000 (5초)
- 방문 일시 해석과 자동 전환 판정 시간대: Asia/Seoul
