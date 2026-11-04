package com.flout.server.domain.inspection.dto.request;

// 조회용 전체 옵션은 실제 점검 상태와 분리
public enum InspectionStatusFilter {
  ALL,
  RECEIVED,
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED
}
