# 현장점검 상태 흐름

수동 점검 상태 변경 API는 제거되었습니다.

- `POST /api/admin/inspections/{inspectionId}/assignment`: 미배정 접수·예정 점검의 배정과 일정을 저장하고 SCHEDULED로 변경합니다.
- 확정 방문 시간이 되면 기존 스케줄러가 SCHEDULED를 IN_PROGRESS로 자동 변경합니다.
- `POST /api/admin/inspections/{inspectionId}/result`: 현장사진이 있는 결과를 공개하면서 점검을 COMPLETED, 신청을 INSPECTION_COMPLETED로 변경합니다. 선행 상태 변경 요청은 필요하지 않습니다.

상태 전환과 이력은 각 업무 저장과 동일한 트랜잭션으로 처리합니다.
이미 공개된 결과의 재공개와 이미 배정된 점검의 재배정은 지원하지 않습니다.
결과 공개는 우선점검 선정 순서 저장과 같은 관할 잠금을 사용하여 완료 건의 순위 정보가 덮어써지는 것을 방지합니다.
