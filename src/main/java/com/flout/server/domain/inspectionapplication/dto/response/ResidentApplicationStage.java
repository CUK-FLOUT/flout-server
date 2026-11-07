package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "내 신청 진행 단계: INSPECTION_RECEIVED=접수, VISIT_SCHEDULED=점검 예정, INSPECTION_IN_PROGRESS=점검 진행, INSPECTION_COMPLETED=점검 완료 및 결과 공개, SUPPORT_REVIEW=지원 검토 중, SUPPORT_APPROVED=지원 승인, SUPPORT_REJECTED=지원 미승인, CONSTRUCTION_SCHEDULED=시공 예정, CONSTRUCTION_IN_PROGRESS=시공 진행, COMPLETED=시공 완료")
public enum ResidentApplicationStage {
  INSPECTION_RECEIVED,
  VISIT_SCHEDULED,
  INSPECTION_IN_PROGRESS,
  INSPECTION_COMPLETED,
  SUPPORT_REVIEW,
  SUPPORT_APPROVED,
  SUPPORT_REJECTED,
  CONSTRUCTION_SCHEDULED,
  CONSTRUCTION_IN_PROGRESS,
  COMPLETED
}
