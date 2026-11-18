# 운영자 지역·공공 문제 조회 및 등록 API

Swagger 그룹: `운영자-공공인프라`

모든 API는 부천시 관할 ADMIN 전용이며 조회로 데이터를 변경하지 않습니다.

| 기능 | Method | Path |
| --- | --- | --- |
| 목록 조회·검색 | GET | `/api/admin/public-issues` |
| 현황 건수 조회 | GET | `/api/admin/public-issues/status` |
| 지도 마커 조회 | GET | `/api/admin/map/public-issues` |
| 상세 조회 | GET | `/api/admin/public-issues/{publicIssueId}` |
| 동일 주소 주민 신청 조회 | GET | `/api/admin/public-issues/{publicIssueId}/resident-applications` |

## 검색 조건

목록과 지도는 `district`, `status`, `issueType`, `keyword`를 조합합니다. 목록에는
0부터 시작하는 `page`와 1~100 범위의 `size`가 추가됩니다. 현황은 `status`를 제외한
같은 조건으로 등록 건수(`registeredCount`), 검토 대기·검토 중 건수
(`pendingOrUnderReviewCount`), 조치 완료 건수(`completedCount`)를 반환합니다.

- `district`: 빈 값, `원미구`, `소사구`, `오정구`
- `status`: `RECEIVED`, `UNDER_REVIEW`, `COMPLETED`
- `issueType`: `REPEATED_FLOODING`, `SEWAGE_BACKFLOW`,
  `POOR_ROAD_DRAINAGE`, `BLOCKED_STORM_DRAIN`
- `keyword`: 주소·문제 상세·현장 확인 내용 부분 검색, 최대 255자

목록의 관련 신청과 침수 이력은 문제 위치에서 직선거리 1km 이하이면서 주소가 부천시인
접수 신청을 기준으로 집계합니다. 경계 지역에서 1km 안에 있더라도 부천시 밖 주소의 신청은
제외됩니다. 동일 주소 주민 신청 API는 주소 공백과 `경기/경기도` 표기를 정규화한 뒤
정확히 같은 주소만 반환하므로 목록의 반경 집계와 범위가 다릅니다.
동일 주소 신청 응답의 `fieldFindings`에는 현재 현장점검 신청의 `requestReason`을 반환합니다.
이는 운영자가 작성한 점검 결과의 현장 확인 내용이 아닙니다.

상세 응답은 등록 출처, 원본 현장점검의 공개 사진(`referencePhotos`), 반경 1km 관련
신청·유입 위치, 검토·연결 공공시설, 완료 조치와 조치 사진(`actions[].images`)을
포함합니다. 원본 점검 결과가 임시저장(`DRAFT`) 상태이면 사진을 노출하지 않으며, 공개
완료(`PUBLISHED`)된 결과에 사진이 없으면 `referencePhotos`는 빈 배열입니다. 각 원본
사진은 `photoId`, `fileName`, `imageUrl`, `size`, `contentType`을 반환합니다. 검토나 연결
시설이 없으면 해당 필드는 `null`이며, 사진 URL은 권한 확인 후 발급하는 15분 유효
비공개 임시 URL입니다.

## 동일 주소 주민 신청 조회

`GET /api/admin/public-issues/{publicIssueId}/resident-applications`

공공 문제 주소와 현장점검 신청 주소를 다음 규칙으로 정규화한 뒤 정확히 같은 신청을 조회합니다.

- 주소 앞뒤 공백을 제거합니다.
- 연속된 공백을 하나로 합칩니다.
- 주소 앞의 `경기 `를 `경기도 `로 통일합니다.
- `DRAFT` 임시저장 신청은 제외합니다.
- `created_at` 내림차순, 같은 시각이면 `application_id` 내림차순으로 반환합니다.
- 조회 결과가 없으면 `totalCount`는 `0`, `applications`는 빈 배열입니다.

`applications`의 각 항목은 `applicationId`, 최소 6자리 `receiptNo`, `address`, `vulnerabilityGrade`, `fieldFindings`를 반환합니다. `fieldFindings`는 신청자가 현장점검 신청에 입력한 요청 사유이며 입력이 없으면 `null`입니다.

이 API는 실행 시 DB 내용을 변경하지 않으며, 반경 1km 기준의 목록·상세 집계와 다른 결과를 반환할 수 있습니다.

## 지역·공공 문제 등록

`POST /api/admin/public-issues`는 관할 원본 점검에 연결된 문제를 `RECEIVED`로 등록합니다.
운영자 Bearer 토큰과 JSON 요청 본문이 필요합니다. `inspectionId`는 현장점검 ID이며
신청 ID와 다릅니다.

```json
{
  "inspectionId": 1,
  "issueType": "BLOCKED_STORM_DRAIN",
  "description": "빗물받이 주변 배수 상태 확인이 필요합니다.",
  "fieldFindings": "낙엽과 토사가 쌓여 있습니다."
}
```

`issueType`은 위 네 유형 중 하나이며 `description`과 `fieldFindings`는 필수이고 각각
최대 4000자입니다. 선택 필드 `address`를 생략하거나 null로 보내면 원본 신청 주소를
사용합니다. 입력하면 공백이 아닌 최대 255자여야 합니다. 정규화한 주소가 원본 주소와
같으면 원본 좌표를 사용하고, 다르면 주소 좌표 서비스를 호출합니다. 외부 좌표 조회는
DB 트랜잭션 밖에서 수행하며 저장 시 원본 점검 권한을 다시 확인합니다.
같은 점검에 여러 문제를 등록할 수 있습니다. 성공은 200 `PUBLIC_ISSUE_200_1`입니다.
등록 시 잘못된 본문은 `400 / COMMON_400_2`, 허용하지 않은 문제 유형은
`400 / PUBLIC_ISSUE_400_2`, 좌표를 확인할 수 없는 주소는 `400 / PUBLIC_ISSUE_400_1`입니다.
다른 주소의 외부 좌표 서비스 오류 또는 시간 초과는 `502 / ADDRESS_502_1`로 변환됩니다.
등록 후 검토와 조치 완료는 각각 [공공시설 검토](facility-review-save.md)와
[조치 사진·완료](public-issue-action-registration.md)를 따릅니다.

## 오류

- 400 `COMMON_400_3`: ID·검색·필터·페이지 조건 오류
- 401 `COMMON_401_1` / `COMMON_401_3`: 인증 누락·유효하지 않은 토큰
- 403 `COMMON_403_1`: 운영자 권한 없음
- 403 `INSPECTION_403_1`: 공공 문제 등록·검토·조치 및 동일 주소 신청 조회의 관할 권한 없음
- 403 `INSPECTION_403_2`: 목록·현황·지도·상세의 관할 조회 권한 없음
- 404 `PUBLIC_ISSUE_404_1`: 공공시설 문제 없음
- 406 `COMMON_406_1`: 지원하지 않는 응답 형식
- 500 `COMMON_500_1`: 공통 서버 오류
