package com.flout.server.domain.inspection.dto.request;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.LocalDate;

// 목록 조회 조건의 AND 결합, 생략 조건 제외
// keyword: 원본 주소·최소 6자리 접수번호 부분 검색
// district: 빈 문자열이면 관할 전체 구
// status: null이면 모든 상태
// inspectorUserId: null이면 미배정 포함 담당자 전체
// visitDate: 주민 방문 희망일, null이면 날짜 제한 없음
// page: 0부터 시작, size: 서비스에서 1~100으로 제한
public record SearchInspectionsRequest(
    String keyword,
    String district,
    InspectionStatus status,
    Long inspectorUserId,
    LocalDate visitDate,
    int page,
    int size) {}
