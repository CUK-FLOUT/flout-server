# FCM 공통 발송 서비스

기존 토큰 등록 API로 저장한 사용자 기기로 푸시를 보냅니다. 공개 발송 API, 앱 내 알림 목록·읽음 처리 및 개별 업무 상태 연결은 포함하지 않습니다.

## 호출

업무 서비스에서 `NotificationService`를 주입받아 호출합니다.

```java
notificationService.send(
    residentUserId,
    NotificationType.INSPECTION_RESULT_PUBLISHED,
    applicationId,
    "inspection-result-published:" + resultId
);
```

- `userId`는 알림을 받을 주민 ID이며, 작업 중인 운영자 ID와 구분합니다.
- `applicationId`는 현장점검 신청 ID입니다. 지원·시공 알림도 원래 신청 ID를 전달합니다.
- 마지막 `eventId`는 1~128자의 영문·숫자·`_ . : -` 문자열입니다. 동일 이벤트 재호출 시 같은 값을 사용해야 기기별 중복 대기 작업을 방지합니다. 서로 다른 수신자·종류·상태 변경에는 서로 다른 이벤트 ID를 사용합니다.
- 세 인자 오버로드도 제공하지만 매 호출마다 새 이벤트 ID를 생성하므로 업무 이벤트 재처리에는 네 인자 메서드를 사용합니다.
- 트랜잭션 안에서 호출하면 커밋 후 대기 작업을 저장하고, 롤백하면 저장·발송하지 않습니다. 트랜잭션 밖에서 호출하면 즉시 대기 작업을 저장합니다.
- 토큰이 없는 사용자는 건너뜁니다. 요청 시점에 등록된 기기만 대상으로 합니다.
- 결과 공개 알림은 임시 저장이 아닌 실제 주민 공개 기능에서 호출해야 합니다.

## 발송 데이터

FCM `notification`에는 종류별 `title`, `body`를 넣습니다. `data` 값은 모두 문자열입니다.

```json
{
  "notificationType": "INSPECTION_RESULT_PUBLISHED",
  "applicationId": "1",
  "target": "INSPECTION_RESULT",
  "eventId": "inspection-result-published:12"
}
```

프론트는 `target`과 `applicationId`를 실제 라우트로 변환합니다. 서버가 임의의 웹 주소를 푸시에 넣지는 않습니다. 프론트에서 표시하는 접수번호는 신청 ID를 최소 6자리로 변환하면 됩니다.

| notificationType | target |
| --- | --- |
| INSPECTION_RECEIVED / INSPECTION_SCHEDULED / INSPECTION_STARTED | INSPECTION_DETAIL |
| INSPECTION_RESULT_PUBLISHED | INSPECTION_RESULT |
| SUPPORT_RECEIVED / SUPPORT_REVIEW_STARTED / SUPPORT_APPROVED / SUPPORT_REJECTED | SUPPORT_DETAIL |
| CONSTRUCTION_SCHEDULED / CONSTRUCTION_STARTED / CONSTRUCTION_COMPLETED | CONSTRUCTION_DETAIL |
| CONSTRUCTION_RESULT_PUBLISHED | CONSTRUCTION_RESULT |

방문·시공 일시 등 상세값은 이번 공통 메시지에 포함하지 않습니다. 실제 화면 이동 경로는 프론트와 협의해야 합니다.

## 설정

- Firebase Admin SDK는 `GOOGLE_APPLICATION_CREDENTIALS`가 가리키는 파일 등 Google Application Default Credentials로 인증합니다.
- 키 JSON은 저장소 밖에 보관합니다. 프론트 설정값·VAPID 공개키와 다른 서버 전용 인증정보입니다.
- `FIREBASE_PROJECT_ID`: 선택 사항. 비어 있으면 SDK가 인증정보·환경에서 프로젝트를 찾습니다. 프론트와 같은 프로젝트여야 합니다.
- `FCM_ENABLED`: 기본 true. false면 자동 발송 작업을 중지합니다. 대기 작업 저장은 유지됩니다.
- `FCM_INTERVAL_MILLIS`: 발송 확인 주기. 기본 5000ms.
- Firebase 클라이언트는 최초 발송 시 초기화되므로 인증정보 없이도 서버 시작·토큰 API·자동 테스트는 동작합니다. 잘못된 인증정보의 발송은 실패 처리됩니다.
- 발송은 전용 스케줄러에서 수행하며 DB 트랜잭션을 유지하지 않습니다.

## 오류 및 재시도

- `UNREGISTERED`만 사용자 연결을 다시 확인하고 토큰을 삭제합니다.
- `INVALID_ARGUMENT`는 메시지 오류일 수도 있으므로 토큰을 삭제하지 않습니다.
- `UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`와 해당 일시 SDK 오류는 최초 시도 포함 최대 3회 시도합니다.
- 재시도는 최소 60초부터 지수 증가·분산 지연을 적용하고, `Retry-After`가 더 길면 그 시간을 따릅니다. 스레드를 재우지 않고 DB의 다음 시각으로 관리합니다.
- 실패한 한 기기가 다른 기기 발송을 중단하지 않습니다. 로그에는 토큰 원문·인증정보를 남기지 않습니다.
- 발송 직전에 토큰의 현재 사용자 연결을 확인하여 계정이 바뀌었거나 교체된 토큰은 건너뜁니다.
- 작업은 `fcm_deliveries`에 저장됩니다. 서버 중단 시 10분 선점 시간이 만료된 작업을 복구합니다.
- 동일 이벤트·기기의 대기 작업은 한 번 생성되지만, 원격 발송 성공 후 응답 유실·DB 저장 실패·선점 만료가 발생하면 중복 수신할 수 있습니다. 프론트도 `eventId`로 중복 표시를 방지할 수 있습니다. 이미 FCM에 제출된 푸시는 취소할 수 없습니다.
- 커밋 후 대기 작업 저장 자체가 실패하면 오류를 기록하고 기존 업무의 성공은 유지합니다. 이 경우 작업이 자동 복원되지는 않으므로 원인을 해결한 뒤 같은 이벤트 ID로 다시 요청해야 합니다.
- 이 테이블은 발송 작업용이며 주민 알림 내역 테이블을 대신하지 않습니다.

## 검증

자동 테스트는 Firebase 호출을 대체하여 수행합니다. 실제 수신 테스트에는 주민 프론트에서 발급한 FCM 토큰과 서버 인증정보가 필요합니다.

공식 문서: [Admin SDK 설정](https://firebase.google.com/docs/admin/setup), [메시지 발송](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk), [오류 처리](https://firebase.google.com/docs/cloud-messaging/error-codes).
