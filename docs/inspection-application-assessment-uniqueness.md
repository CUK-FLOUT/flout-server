# 현장점검 신청 중복 확인 및 V26 배포 절차

V26은 `inspection_applications.assessment_id`에 UNIQUE 제약조건을 추가합니다. 기존 신청을 삭제하거나 병합하지 않습니다. **중복이 하나라도 있으면 V26은 실패하므로**, 각 대상 환경에서 배포 전에 읽기 전용으로 확인하고 중복을 해결해야 합니다.

## 1. 중복 확인

V26 적용 대상 DB에서 실행합니다. 결과가 없어야 합니다.

```sql
SELECT assessment_id, COUNT(*) AS application_count
FROM inspection_applications
GROUP BY assessment_id
HAVING COUNT(*) > 1
ORDER BY assessment_id;
```

중복 그룹과 각 신청의 접수번호, 상태, 생성 시각을 확인합니다.

```sql
SELECT a.assessment_id, a.application_id, a.receipt_no,
       a.applicant_user_id, a.status, a.created_at
FROM inspection_applications a
JOIN (
    SELECT assessment_id
    FROM inspection_applications
    GROUP BY assessment_id
    HAVING COUNT(*) > 1
) duplicates ON duplicates.assessment_id = a.assessment_id
ORDER BY a.assessment_id, a.application_id;
```

## 2. 연관 데이터 확인

중복 그룹마다 아래 `?`에 확인할 `assessment_id`를 바인딩하여 실행합니다. 각 쿼리는 해당 그룹의 모든 신청에 연결된 행을 반환합니다. 신청 이력, 유입 위치, 우선점검 지정, 점검과 점검 이력, 첨부파일, 점검 결과와 결과 상세를 확인합니다.

```sql
SELECT i.* FROM inspections i
JOIN inspection_applications a ON a.application_id = i.application_id
WHERE a.assessment_id = ?;

SELECT h.* FROM inspection_status_histories h
JOIN inspections i ON i.inspection_id = h.inspection_id
JOIN inspection_applications a ON a.application_id = i.application_id
WHERE a.assessment_id = ?;

SELECT t.* FROM application_attachments t
JOIN inspection_applications a ON a.application_id = t.application_id
WHERE a.assessment_id = ?;

SELECT h.* FROM application_status_histories h
JOIN inspection_applications a ON a.application_id = h.application_id
WHERE a.assessment_id = ?;

SELECT l.* FROM application_inflow_locations l
JOIN inspection_applications a ON a.application_id = l.application_id
WHERE a.assessment_id = ?;

SELECT p.* FROM priority_inspection_designations p
JOIN inspection_applications a ON a.application_id = p.application_id
WHERE a.assessment_id = ?;

SELECT r.* FROM inspection_results r
JOIN inspections i ON i.inspection_id = r.inspection_id
JOIN inspection_applications a ON a.application_id = i.application_id
WHERE a.assessment_id = ?;

SELECT v.* FROM inspection_vulnerabilities v
JOIN inspection_results r ON r.result_id = v.result_id
JOIN inspections i ON i.inspection_id = r.inspection_id
JOIN inspection_applications a ON a.application_id = i.application_id
WHERE a.assessment_id = ?;

SELECT p.* FROM prevention_recommendations p
JOIN inspection_results r ON r.result_id = p.result_id
JOIN inspections i ON i.inspection_id = r.inspection_id
JOIN inspection_applications a ON a.application_id = i.application_id
WHERE a.assessment_id = ?;
```

첨부파일의 `object_key`에 해당하는 객체와 신청 접수번호를 외부에서 참조하는 경우도 확인합니다. 위 조회 결과에는 개인정보가 포함될 수 있으므로 접근 권한과 보관 정책에 따라 다룹니다. 환경별로 추가된 테이블이나 외부 연동이 있다면 함께 조사합니다.

## 3. 중복이 있는 환경의 배포 전 처리

1. V26 적용을 보류하고 대상 환경의 백업과 복구 가능 여부를 확인합니다. 신청 접수를 중지하거나 쓰기를 통제해 점검과 정리 중 새 중복이 생기지 않게 합니다.
2. 각 중복 그룹과 연관 데이터를 검토하여 담당자가 보존할 신청, 접수번호와 이력·점검 결과·첨부 객체의 처리 방침을 결정하고 승인합니다. 단순히 오래되거나 ID가 작은 신청을 남기는 식으로 자동 결정하지 않습니다.
3. 승인된 별도 정리 계획을 복구 가능한 환경에서 검증한 뒤 대상 환경에 적용합니다. 데이터 이전, 보존 또는 삭제가 필요한지는 검토 결과에 따르며, 이 문서는 정리 SQL을 자동 실행하지 않습니다.
4. 쓰기가 통제된 상태에서 중복 조회 결과가 0건인지 다시 확인하고, 그 상태를 유지한 채 V26을 적용합니다. 적용 성공과 UNIQUE 제약조건을 확인한 후 신청 접수를 재개합니다.

중복이 없는 환경도 마지막 확인과 V26 적용 사이에는 쓰기를 통제해야 합니다. 이미 V26 적용이 실패했다면 자동 재시도하거나 `repair`를 먼저 실행하지 말고, DB의 실제 스키마와 Flyway 스키마 이력을 확인하세요. MySQL의 DDL은 자동 커밋될 수 있으므로 실패 상태를 조사하고 승인된 복구 절차에 따라 조치한 뒤 재적용합니다.
