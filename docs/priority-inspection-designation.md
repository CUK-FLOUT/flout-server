# 우선점검 대상 지정·취소

한 건과 여러 건 모두 `PUT /api/admin/priority-inspections/designation`으로 처리합니다.
신청 ID를 경로에 넣던 기존 단건 API는 제거했습니다.

```json
{"applicationIds": [5], "designated": true}
```

여러 신청은 `applicationIds`에 ID를 함께 전달합니다. 한 건이라도 실패하면 전체 롤백합니다.
요청·응답·오류와 선정 순서 규칙은 [일괄 지정·취소](priority-inspection-bulk-designation.md)를 참고합니다.

V18은 지정 테이블을 생성하고 V19는 사용하지 않는 selection_memo 컬럼을 제거합니다.
기존 신청의 internal_memo와는 별개이며 메모는 기존 메모 API로 관리합니다.
