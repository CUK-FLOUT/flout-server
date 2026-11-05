# 운영자 신청 상세 — 시공 정보 조회

GET `/api/admin/applications/{applicationId}/construction`

부천시 관할 ADMIN이 GIS 신청 상세의 시공 정보 탭을 조회합니다. 경로에는 현장점검 신청 ID를 사용합니다. 요청 본문과 쿼리 파라미터는 없습니다.

200 `APPLICATION_200_6`: `신청의 시공 정보를 조회했습니다.`

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "APPLICATION_200_6",
  "message": "신청의 시공 정보를 조회했습니다.",
  "result": {
    "applicationId": 101,
    "receiptNo": "000101",
    "applicantName": "홍길동",
    "address": "경기도 부천시 원미구 길주로 53",
    "detailAddress": "101호",
    "supportApplicationId": 201,
    "supportStatus": "APPROVED",
    "construction": {
      "constructionId": 301,
      "status": "COMPLETED",
      "requestedFacility": "출입구 물막이판",
      "supportScope": "출입구 물막이판 설치 지원",
      "preferredConstructionDate": "2026-11-10",
      "preferredTimeSlot": "MORNING",
      "constructionDate": "2026-11-10",
      "constructionTime": "10:00",
      "managerId": 6,
      "managerName": "김운영",
      "completedAt": "2026-11-10T11:30:00+09:00",
      "finalInstallationResult": "출입구 물막이판 설치와 통행 확인을 완료했습니다."
    }
  },
  "timeStamp": "2026-11-10 11:40:00"
}
```

- 시공 미연계: `construction: null`로 200을 반환합니다. 지원 미신청·지원 DRAFT만 있으면 supportApplicationId와 supportStatus도 null입니다. 접수 후 검토·미승인 상태라면 해당 지원 ID와 실제 지원 상태는 반환합니다.
- 승인됐지만 실제 시공 건이 없는 경우: supportStatus는 APPROVED, construction은 null입니다. 조회로 시공을 생성하지 않습니다.
- 미배정: construction은 존재하고 status는 PENDING입니다. 주민 희망 일정은 반환하되 확정 날짜·시간·담당자는 DB 값이 없으면 null입니다.
- 예정·진행: 실제 SCHEDULED·IN_PROGRESS 상태와 저장된 확정 일정을 반환합니다. 조회 시각으로 상태를 추정하거나 수정하지 않습니다.
- 전체 완료: status는 COMPLETED이며 completedAt과 finalInstallationResult에 실제 저장된 값을 반환합니다. 프론트의 ‘최종 처리: 전체 완료’ 표시는 COMPLETED를 기준으로 합니다.

완료 전에는 completedAt·finalInstallationResult를 null로 반환합니다. 과거 데이터에 완료 시각이 없어도 예정 시각으로 대체하지 않습니다. 날짜·완료 시각은 한국 시간 기준이며 constructionTime은 HH:mm입니다. preferredTimeSlot은 MORNING=오전 9~12시, AFTERNOON=오후 13~18시, ANY=상관없음입니다.

requestedFacility는 지원 신청에 저장된 시설이고 supportScope는 승인된 지원 범위입니다. 실제 설치 조치 내용은 finalInstallationResult로 구분합니다. 주택 유형·층수·설치 완료일을 임의 추정하지 않습니다. 제목·주소는 기존 GIS 신청 정보 탭과 같은 원본 값이며 마스킹하지 않습니다.

여러 사업에 신청했다면 지원 정보 탭과 동일하게 DRAFT를 제외하고 접수 시각 내림차순, 같으면 지원 신청 ID 내림차순의 최신 한 건을 사용합니다. 다른 지원 건의 과거 시공을 섞지 않습니다. 시공관리 이동에는 constructionId를 사용합니다. 사진 목록·다운로드 URL은 이 탭 API에 포함하지 않습니다.

- 400 `COMMON_400_3`: 신청 ID 형식 오류.
- 401 `COMMON_401_1` / `COMMON_401_3`: 미인증 / 만료·유효하지 않은 토큰.
- 403 `COMMON_403_1`: 운영자 권한 없음.
- 403 `APPLICATION_403_4`: 본인 관할 지역의 신청만 조회할 수 있습니다.
- 404 `APPLICATION_404_2`: 현장점검 신청을 찾을 수 없습니다. 현장점검 DRAFT도 운영자 상세에서 제외합니다.
- 500 `COMMON_500_1`: 서버·DB 처리 오류.

응답 헤더는 `Cache-Control: no-store`이며 지원·시공·임대인 동의 상태를 변경하지 않습니다. 새 테이블과 DB 마이그레이션은 필요하지 않습니다.
