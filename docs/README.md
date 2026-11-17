# 📚 FLOUT Server 문서

이 디렉터리는 Swagger/OpenAPI에서 다루기 어려운 업무 규칙, 상태 전이, 멱등성, 배포·마이그레이션 절차를 보충합니다. 실제 요청·응답 스키마와 현재 경로는 실행 중인 서버의 `/swagger-ui/index.html` 또는 `/v3/api-docs`를 최우선 기준으로 삼습니다.

## 문서 작성 원칙

- API 경로, HTTP 메서드, 권한, 상태 전이, 오류 코드를 구현과 함께 갱신합니다.
- 현재 동작과 일회성 배포·데이터 정리 절차를 구분합니다.
- 실제 개인정보, 인증 토큰, 서명 URL, 운영 자격 증명을 예시에 사용하지 않습니다.
- 응답은 `isSuccess`, `status`, `code`, `message`, `result`, `timeStamp` 공통 envelope를 기준으로 설명합니다.

## 전체 구조·공통 규약

| 문서 | 내용 |
| --- | --- |
| [서버 아키텍처](ARCHITECTURE.md) | 시스템 구성, 외부 서비스, 배포 경계 |
| [API 경로 카탈로그](API_CATALOG.md) | Controller 매핑 기준 메서드·경로·소스 |
| [API 공통 규약](API_CONVENTIONS.md) | 응답, 인증, 날짜·시간, 접수번호, 페이징·파일 |
| [인증·인가·접근 제어](SECURITY_AND_ROLES.md) | 주민 소유권과 운영자 관할 검증 |
| [상태 전이](STATUS_LIFECYCLES.md) | 점검, 공공 문제, 지원, 시공 상태 |
| [외부 서비스 연동](INTEGRATIONS.md) | Python, Gemini, S3, FCM, 주소 API |
| [DB·Flyway](DATABASE_AND_MIGRATIONS.md) | 스키마 변경과 배포 전 확인 |
| [운영 런북](OPERATIONS_RUNBOOK.md) | 배포 전·후 점검과 장애 대응 |
| [시연 데이터 정책](DEMO_DATA_POLICY.md) | 실제 행정 데이터와 시연용 데이터 구분 |
| [문서–소스 정합성 점검](DOCS_AUDIT.md) | 자동 점검 결과와 수동 검증 항목 |

## 인증·회원

| 문서 | 내용 |
| --- | --- |
| [Google 로그인](google-login.md) | 주민·운영자 Google 로그인과 로컬 테스트 |
| [이메일 회원가입](signup.md) | 주민 회원가입과 필수 동의 |
| [토큰 재발급·로그아웃](refresh-token.md) | Refresh Token 회전과 폐기 |
| [운영자 관할](jurisdictions.md) | 관할 데이터와 `GET /api/admin/me` |

## 취약도·예방 가이드

| 문서 | 내용 |
| --- | --- |
| [AI 취약도 분석 API](risk-assessment-ai.md) | Spring–Python 계약과 오류 처리 |
| [AI 모델 파이프라인](AI_MODEL_PIPELINE.md) | 데이터 분리, 후보 모델, 평가 기준 |
| [맞춤 예방 가이드](prevention-guide-generation.md) | Gemini 생성, 저장, 재조회 |

## 현장점검 신청

| 문서 | 내용 |
| --- | --- |
| [신청과 점검 생성](inspection-application.md) | 임시저장, 사진 접수, 점검 생성 |
| [GIS 신청 상세](application-detail.md) | 운영자 신청 상세 표시 규칙 |
| [첨부사진 조회](application-attachments.md) | S3 서명 URL과 조회 오류 |
| [처리 이력](application-histories.md) | 신청·점검·지원·시공 이력 |
| [시공 정보](application-construction.md) | 신청 상세의 시공 정보 |
| [지원 정보](application-support.md) | 신청 상세의 지원 정보 |
| [운영자 내부 메모](internal-memo.md) | 관할 검증과 메모 저장 |
| [중복 신청 배포 점검](inspection-application-assessment-uniqueness.md) | V26 적용 전 데이터 검증 |

## 점검·우선점검

| 문서 | 내용 |
| --- | --- |
| [점검 목록](inspection-list.md) | 검색·필터·페이징 |
| [점검 상세](inspection-detail.md) | 배정 화면 응답 |
| [점검 배정·일정](inspection-assignment.md) | 저장 순서와 상태 전이 |
| [점검 상태](inspection-status.md) | `RECEIVED`–`SCHEDULED`–`IN_PROGRESS`–`COMPLETED` |
| [점검 결과 조회](admin-inspection-result-query.md) | 초안·공개 결과 조회 |
| [결과 권장 조치](inspection-result-recommendations.md) | 초안·공개 권장 조치 구조 |
| [결과 메모](inspection-result-optional-memo.md) | 내부 메모 선택 입력 |
| [초안 사진 삭제](inspection-photo-delete.md) | 공개 전 사진 삭제 |
| [우선점검 후보](priority-inspection-candidates.md) | 후보 검색·필터 |
| [우선점검 상세](priority-inspection-detail.md) | 신청·취약도 정보 |
| [우선점검 지정](priority-inspection-designation.md) | 단건·일괄 지정 API 안내 |
| [지정·취소](priority-inspection-bulk-designation.md) | 단건·일괄 지정 |
| [선정 순서 조회](priority-inspection-order-query.md) | 저장된 우선순위 조회 |
| [선정 순서 저장](priority-inspection-order.md) | 전체 순서 갱신 |

## 지원사업·시공

| 문서 | 내용 |
| --- | --- |
| [지원사업 AI 매칭](support-program-matching.md) | Gemini 후보 선정과 검증 |
| [지원 자격 검토](support-application-review.md) | 운영자 검토 화면 응답 |
| [임대인 동의서 변경](landlord-consent-api-change.md) | 동의 상태 제거와 첨부 기준 |
| [운영자 시공 조회](admin-construction-query.md) | 목록·상세 |
| [시공 배정·일정](construction-schedule.md) | 승인 후 시공 일정 저장 |
| [시공 사진](construction-photos.md) | 멱등 업로드 |
| [시공 완료](construction-completion.md) | 사진 검증과 전체 완료 |

## 공공시설 문제

| 문서 | 내용 |
| --- | --- |
| [공공 문제 조회](admin-public-issue-query.md) | 관할 목록·상세·지도 |
| [공공시설 검토 저장](facility-review-save.md) | 검토 내용과 시설 연결 |
| [조치 사진·완료](public-issue-action-registration.md) | 첨부 검증과 상태 변경 |

## 알림·주민 진행 현황

| 문서 | 내용 |
| --- | --- |
| [FCM 토큰 등록](fcm-token-registration.md) | 기기 토큰 등록·갱신 |
| [FCM 발송](fcm-delivery.md) | 공통 발송, 재시도, 비활성 토큰 |
| [진행 현황 카드 문구](resident-progress-card-copy.md) | `currentStage`·`nextAction`·`title` 표시 규칙 |

## 배포·운영

| 문서 | 내용 |
| --- | --- |
| [EC2 배포](EC2_DEPLOYMENT.md) | GitHub OIDC, systemd, HTTPS, health check, 복구 |

## 현재 코드와의 대응

| 문서 영역 | 주요 소스 패키지 |
| --- | --- |
| 인증·회원 | `domain/user` |
| 신청·점검·우선점검 | `domain/inspectionapplication`, `domain/inspection`, `domain/priorityinspection` |
| 지원·시공 | `domain/supportapplication`, `domain/supportprogram`, `domain/construction` |
| 취약도·예방 | `domain/address`, `domain/riskassessment`, `domain/preventionguide` |
| 공공 문제·알림 | `domain/publicissue`, `domain/notification`, `domain/residentprogress` |
| 공통 인프라 | `global/config`, `global/security`, `global/response`, `global/time` |

[루트 README로 돌아가기](../README.md)
