# 운영자 시공 배정·일정 저장

`POST /api/admin/constructions/{constructionId}/schedule`

지원 승인된 시공 건에 확정 날짜·시간을 저장하고 로그인한 부천시 관할 운영자를 담당자로 배정합니다. 경로 이름은 `constructionId`이며 지원 신청 ID·점검 ID와 다릅니다.

## 승인과 시공 연계

- 지원 승인 시 `constructions`에 PENDING 시공 건을 한 개 생성하고 승인 응답에 `constructionId`를 추가합니다. 승인·처리 이력·시공 생성은 같은 트랜잭션으로 처리합니다.
- 기존 APPROVED 신청은 V41 마이그레이션에서 PENDING 시공 건을 생성합니다. 희망 날짜를 확정 일정으로 복사하거나 담당자를 임의 지정하지 않습니다.
- 기존 승인 건의 ID는 다음 읽기 쿼리로 확인할 수 있습니다. 별도의 시공 목록 조회는 이 변경 범위에 포함하지 않습니다.

```sql
SELECT construction_id, support_application_id, status, scheduled_at
FROM constructions
ORDER BY construction_id DESC;
```

## 요청

```json
{
  "constructionDate": "2030-01-03",
  "constructionTime": "10:00"
}
```

두 값 모두 필수입니다. 날짜는 yyyy-MM-dd, 시간은 HH:mm 형식이며 한국 시간으로 해석합니다. 담당자 ID는 요청받지 않고 JWT 로그인 운영자를 사용합니다.

## 200 응답의 result 예시

```json
{
  "constructionId": 301,
  "supportApplicationId": 201,
  "applicationId": 19,
  "receiptNo": "000019",
  "applicantName": "홍길동",
  "managerId": 6,
  "managerName": "김담당",
  "constructionDate": "2030-01-03",
  "constructionTime": "10:00",
  "status": "SCHEDULED",
  "updatedAt": "2030-01-02T09:30:00+09:00"
}
```

공통 응답 형식을 사용하며 성공 코드는 `CONSTRUCTION_200_1`, 메시지는 `시공 일정이 저장되었습니다.`입니다.

- PENDING과 SCHEDULED 상태에서만 저장할 수 있습니다. 시작 전 재저장을 허용하며 기존 시공 건을 수정합니다.
- 과거 날짜뿐 아니라 오늘 이미 지난 시간도 거부합니다. 검사 시각과 같은 시각은 허용합니다.
- 지원 신청 상태는 APPROVED를 유지합니다. 주민 희망 일정·원본 현장점검 일정·신청 정보는 변경하지 않습니다.
- 주민 `GET /api/resident/support-applications/{supportApplicationId}/approval`의 `scheduledConstructionDate/Time`에 저장한 일정을 반환합니다. 일정이 없으면 null입니다.
- 사진 업로드·시공 결과 공개·자동 시작 전환·푸시 발송은 포함하지 않습니다.

## 오류

- 400 `COMMON_400_2`: 필수값 누락, 잘못된 JSON, 잘못된 날짜·시간 형식
- 400 `COMMON_400_3`: 양의 Long이 아닌 시공 ID
- 400 `CONSTRUCTION_400_1`: 현재 시각보다 과거인 확정 일정
- 401 `COMMON_401_1` / `COMMON_401_3`: 미인증 / 유효하지 않거나 만료된 토큰
- 403 `COMMON_403_1`: 운영자 권한 또는 부천시 관할 없음
- 403 `CONSTRUCTION_403_1`: 다른 관할의 지원 신청
- 404 `CONSTRUCTION_404_1`: 없는 시공 건
- 409 `CONSTRUCTION_409_1`: 연결된 지원 신청이 APPROVED가 아님
- 409 `CONSTRUCTION_409_2`: 시공이 IN_PROGRESS 또는 COMPLETED

변경 경로의 잠금 순서는 지원 신청 → 시공입니다. 향후 시공 진행·결과 변경 기능에서도 동일한 순서를 유지해야 합니다.
