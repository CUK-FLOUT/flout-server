# 시공 결과 확인 후 전체 완료

POST `/api/admin/constructions` — 운영자-시공관리

지원 승인 응답의 `constructionId`를 사용합니다. 먼저 일정 저장 API로 시공 일정을 저장하고, `POST /api/admin/constructions/{constructionId}/photos`로 사진을 최소 1장 업로드한 다음 호출합니다. 사진은 여러 장이어도 됩니다. 화면에서 파일을 선택하기만 한 상태는 업로드로 인정하지 않습니다.

```json
{
  "constructionId": 301,
  "finalInstallationResult": "출입구 물막이판 설치와 작동 확인을 완료했습니다."
}
```

두 필드는 필수입니다. 최종 설치 결과는 공백을 제외한 내용이 있어야 하며 최대 4000자입니다. 사진은 이 요청에 다시 첨부하지 않습니다.

부천시 관할 ADMIN만 처리할 수 있습니다. 한국 시간 기준 확정 방문 시각이 도래한 SCHEDULED 또는 IN_PROGRESS 시공을 COMPLETED로 변경하고 주민 공개용 최종 설치 결과와 완료 시각을 함께 저장합니다. 자동 상태 전환 전이라도 방문 시각이 도래했다면 완료할 수 있습니다. 지원 신청의 APPROVED 상태는 유지합니다. 완료 후 일정 변경·사진 추가·중복 완료는 거절합니다.

200 `CONSTRUCTION_200_3`, 메시지: `시공 결과가 공개되고 전체 완료되었습니다.`

result: `constructionId`, `supportApplicationId`, `applicationId`, `receiptNo`, `applicantName`, `status`(COMPLETED), `finalInstallationResult`, `completedAt`(한국 시간 +09:00).

- 400 COMMON_400_2: 본문·필수 값 누락, 잘못된 JSON, 양수가 아닌 ID, 결과 공백·4000자 초과.
- 400 CONSTRUCTION_400_4: 전체 완료하려면 시공 사진을 최소 1장 업로드해주세요.
- 401: 인증 토큰 누락·만료·유효하지 않음.
- 403 COMMON_403_1: 운영자 권한·관할 없음.
- 403 CONSTRUCTION_403_1: 관할 지역의 시공 건만 변경할 수 있습니다.
- 404 CONSTRUCTION_404_1: 시공 건을 찾을 수 없습니다.
- 409 CONSTRUCTION_409_1: 지원 승인된 신청만 시공 업무를 처리할 수 있습니다.
- 409 CONSTRUCTION_409_4: 이미 전체 완료된 시공입니다.
- 409 CONSTRUCTION_409_5: 시공 일정을 저장한 후 전체 완료할 수 있습니다.
- 409 CONSTRUCTION_409_8: 확정 시공 방문 시각부터 전체 완료할 수 있습니다.
- 500: 공통 서버 오류. 결과·상태·완료 시각은 함께 롤백됩니다.
