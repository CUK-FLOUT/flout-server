package com.flout.server.domain.inspection.dto.request;

import com.flout.server.domain.inspection.entity.InspectionStatus;
import java.time.LocalDate;

/**
 * 형식 변환이 끝난 목록 조회 조건. 필터는 AND로 결합하며 생략된 조건은 조회 범위를 추가로 제한하지 않는다.
 *
 * @param keyword 원본 신청 주소·최소 6자리 접수번호에서 연속 부분 일치로 찾을 검색어
 * @param district 빈 문자열이면 관할 내 전체 구
 * @param status null이면 모든 점검 상태
 * @param inspectorUserId null이면 담당자 전체(미배정 포함)
 * @param visitDate 확정 일정과 구분되는 주민 방문 희망일. null이면 날짜 제한 없음
 * @param page 0부터 시작하는 페이지 번호
 * @param size 페이지 크기. 서비스에서 1~100으로 제한
 */
public record SearchInspectionsRequest(
    String keyword,
    String district,
    InspectionStatus status,
    Long inspectorUserId,
    LocalDate visitDate,
    int page,
    int size) {}
