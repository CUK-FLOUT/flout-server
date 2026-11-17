# 운영 점검 및 장애 대응

## 배포 전

1. 대상 브랜치와 배포 SHA 확인 (`dev` PR 검증 후 `main` 반영).
2. CI / Convention Check 통과, 보안 정보 및 설정 변경 확인.
3. Flyway 신규 파일, 데이터 마이그레이션과 구버전 호환성 검토.
4. DB 백업 및 복원 가능 상태 확보.

## 배포 후

```bash
sudo systemctl status flout --no-pager
sudo journalctl -u flout -n 150 --no-pager
curl -i http://localhost:8080/actuator/health
```

- 8080은 일반적인 예시입니다. 실제 바인딩·포트는 서버 설정에 따릅니다.
- Nginx 프록시, HTTPS 인증서, CORS, 환경 변수 및 외부 호출을 검사합니다.
- 주민 주요 플로우: 로그인 → 분석 → 가이드 → 신청 → 점검/지원 조회.
- 운영자 주요 플로우: 점검 조회 → 배정 → 결과 공개 → 공공인프라 및 시공 관리.

## 장애 시

1. GitHub Actions 단계별 실패 위치 파악 (Gradle/SSM/EC2/Flyway).
2. 실패 당시 커밋·서비스 로그·DB 마이그레이션 적용 이력 확보.
3. SQL DDL이 적용된 상태라면 단순 JAR 롤백이 가능한지 먼저 검증.
4. S3·Python·Gemini·FCM 장애는 서버 장애와 분리해서 재현.
5. 민감정보를 제외한 장애 기록과 조치 결과 공유.

자세한 서버 설정과 배포 명령은 [EC2_DEPLOYMENT.md](EC2_DEPLOYMENT.md)를 따릅니다.
