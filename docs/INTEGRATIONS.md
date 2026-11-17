# 외부 서비스 연동

| 연동 | 사용 목적 | 검증 포인트 |
|---|---|---|
| Python 취약도 API | 위치 기반 격자 조회·지역 상대 취약도 | 타임아웃, 분석 불가, 스키마 검증, 0~1 점수 |
| Google Gemini | 맞춤 예방 가이드·지원사업 매칭 | 모델 응답 파싱, 유효 DB 프로그램 ID, 예외 처리 |
| AWS S3 | 신청·점검·시공 사진 | 권한, 업로드 실패 정리, 서명 URL 만료 |
| Firebase FCM | 주민·운영자 알림 | 토큰 등록, 실패/재시도/무효 토큰 처리 |
| 주소→좌표 변환 | 주소 기반 지도·위치 분석 | 주소 정규화, 지역 제한, 좌표 일관성 |

각 연동의 구체적 요청 스키마·오류 코드는 실제 `client`, `service`, 테스트를 우선합니다. 외부 AI가 반환한 문자열이나 추천 대상 ID를 검증 없이 신뢰하지 않습니다.

관련: [risk-assessment-ai.md](risk-assessment-ai.md), [prevention-guide-generation.md](prevention-guide-generation.md), [support-program-matching.md](support-program-matching.md), [fcm-delivery.md](fcm-delivery.md).
