# 인증·인가·접근 제어

| 영역 | 주체 | 확인 사항 |
|---|---|---|
| 주민 API `/api/resident/**` | RESIDENT | 본인 데이터 소유권, JWT 주체, 원본 신청 권한 |
| 운영자 API `/api/admin/**` | ADMIN | 역할 확인; 신청·점검 등 관할 데이터는 각 서비스에서 관할 범위 검증 |
| 인증 API `/api/auth/**` | 비로그인/로그인 혼재 | 개별 엔드포인트의 공개 범위 및 refresh 정책 |
| 외부 AI 연동 | 서버 간 호출 | API 키·타임아웃·응답 검증·오류 매핑 |

- 운영자에게 단순히 ADMIN 역할이 있다는 이유만으로 모든 지역의 데이터 접근을 허용하지 않습니다.
- 주민용 신청, 분석, 첨부파일, 지원사업 조회는 소유권 검증이 필수입니다.
- 파일 다운로드 URL은 인증된 사용자에게 권한 확인 후 발급합니다.
- 로그에 JWT, refresh token, Google ID Token, S3 서명 URL, 개인정보를 기록하지 않습니다.
- 상세 오류 코드는 각 API 및 `response.code` 클래스를 확인합니다.

관련 문서: [google-login.md](google-login.md), [refresh-token.md](refresh-token.md), [jurisdictions.md](jurisdictions.md).
