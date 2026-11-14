# 운영자 시공 조회 API

Swagger 그룹: `운영자-시공관리`

부천시 관할 ADMIN만 사용할 수 있으며 두 API 모두 조회 전용입니다.

## 목록 조회·검색

`GET /api/admin/constructions`

| 파라미터 | 규칙 |
| --- | --- |
| `keyword` | 접수번호·신청자 이름 부분 검색, 최대 255자 |
| `status` | 빈 값, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED` |
| `page` | 0부터 시작 |
| `size` | 1~100 |

시공 ID 내림차순으로 반환합니다. 화면의 예정 탭에 맞추기 위해 `SCHEDULED` 필터는 일정
미배정 `PENDING`과 일정 확정 `SCHEDULED`를 함께 조회합니다. 각 항목은 시공 ID,
접수번호, 신청자, 신청 시설, 확정 일정과 현재 상태를 포함합니다.

## 상세 조회

`GET /api/admin/constructions/{constructionId}`

경로에는 지원 신청 ID나 현장점검 ID가 아닌 시공 ID를 사용합니다. 지원 신청 정보,
주민 희망 방문 일정, 운영자가 확정한 시공 일정, 담당자와 업로드 사진을 반환합니다.

- 업로드 사진은 완료 전에도 운영자에게 반환합니다.
- `resultPublished`는 전체 완료와 동시에 주민에게 결과가 공개됐는지를 나타냅니다.
- `finalInstallationResult`와 `completedAt`은 완료 전에는 `null`입니다.
- 사진 URL은 15분 유효한 비공개 URL입니다.
- 주민 희망 일정과 운영자 확정 일정은 서로 다른 필드입니다.

## 오류

- 400 `COMMON_400_3`: ID·페이지·크기·검색어·상태 값 오류
- 401 `COMMON_401_1` / `COMMON_401_3`: 인증 누락·유효하지 않은 토큰
- 403 `COMMON_403_1`: 운영자 권한 또는 관할 설정 없음
- 403 `CONSTRUCTION_403_1`: 관할 밖 시공 상세 조회
- 404 `CONSTRUCTION_404_1`: 시공 건 없음
- 406 `COMMON_406_1`: 지원하지 않는 응답 형식
- 500 `CONSTRUCTION_500_2`: 사진 임시 URL 발급 실패
