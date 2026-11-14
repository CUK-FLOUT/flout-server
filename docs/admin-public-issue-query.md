# 운영자 지역·공공 문제 조회 API

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
같은 조건으로 등록·검토·조치·완료 건수를 반환합니다.

- `district`: 빈 값, `원미구`, `소사구`, `오정구`
- `status`: `RECEIVED`, `UNDER_REVIEW`, `INSPECTION_REQUIRED`,
  `ACTION_IN_PROGRESS`, `COMPLETED`
- `issueType`: `REPEATED_FLOODING`, `SEWAGE_BACKFLOW`,
  `POOR_ROAD_DRAINAGE`, `BLOCKED_STORM_DRAIN`
- `keyword`: 주소·문제 상세·현장 확인 내용 부분 검색, 최대 255자

목록의 관련 신청과 침수 이력은 문제 위치에서 직선거리 1km 이하이면서 주소가 부천시인
접수 신청을 기준으로 집계합니다. 경계 지역에서 1km 안에 있더라도 부천시 밖 주소의 신청은
제외됩니다. 동일 주소 주민 신청 API는 주소 공백과 `경기/경기도` 표기를 정규화한 뒤
정확히 같은 주소만 반환하므로 목록의 반경 집계와 범위가 다릅니다.

상세 응답은 등록 출처, 반경 1km 관련 신청·유입 위치, 검토·연결 공공시설, 완료 조치와
사진을 포함합니다. 검토나 연결 시설이 없으면 해당 필드는 `null`이며, 사진 URL은 권한
확인 후 발급하는 비공개 임시 URL입니다.

## 오류

- 400 `COMMON_400_3`: ID·검색·필터·페이지 조건 오류
- 401 `COMMON_401_1` / `COMMON_401_3`: 인증 누락·유효하지 않은 토큰
- 403 `COMMON_403_1`: 운영자 권한 없음
- 403 `INSPECTION_403_1` / `INSPECTION_403_2`: 관할 변경·조회 권한 없음
- 404 `PUBLIC_ISSUE_404_1`: 공공시설 문제 없음
- 406 `COMMON_406_1`: 지원하지 않는 응답 형식
- 500 `COMMON_500_1`: 공통 서버 오류
