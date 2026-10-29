package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.dto.request.SearchInspectionsRequest;
import com.flout.server.domain.inspection.dto.response.InspectionListResponse;
import com.flout.server.domain.inspection.entity.InspectionStatus;
import com.flout.server.domain.inspectionapplication.entity.PreferredTimeSlot;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InspectionListRepository {
  // 주소 시작의 행정구역만 허용해 '부천시민로'나 다른 시의 주소가 관할로 인식되지 않게 한다.
  private static final String BUCHEON = "^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)";
  // 구 이름 뒤의 공백 또는 문자열 끝까지 확인해 '원미구로' 같은 도로명과 구분한다.
  private static final Pattern DISTRICT =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시\\s+(원미구|소사구|오정구)(?:\\s|$)");
  private final NamedParameterJdbcTemplate jdbc;

  // 목록과 건수에 같은 조건 적용, 검색은 원본 주소로 수행
  public InspectionListResponse search(SearchInspectionsRequest request) {
    // 지역 미선택도 부천시 관할 제한은 유지한다. 구 이름은 서비스에서 허용값을 검증한다.
    // 페이지 번호와 크기의 곱은 int 범위를 넘을 수 있으므로 long으로 계산한다.
    var params =
        new MapSqlParameterSource()
            .addValue(
                "region",
                request.district().isEmpty()
                    ? BUCHEON
                    : "^(?:(?:경기도|경기)\\s+)?부천시\\s+" + request.district() + "(?:\\s|$)")
            .addValue("keyword", request.keyword().strip())
            .addValue("limit", request.size())
            .addValue("offset", (long) request.page() * request.size());
    // 담당자·우선점검 지정이 없는 점검도 목록에 남도록 선택 정보는 LEFT JOIN한다.
    // LOCATE로 검색어를 문자 그대로 찾으므로 '%'나 '_'도 와일드카드로 해석하지 않는다.
    // 접수번호 패딩 길이는 최소 6자리이며 긴 신청 ID는 검색 시에도 잘리지 않는다.
    var filter =
        new StringBuilder(
            """
        FROM inspections i
        JOIN inspection_applications a ON a.application_id = i.application_id
        LEFT JOIN users u ON u.user_id = i.inspector_user_id
        LEFT JOIN priority_inspection_designations d ON d.application_id = a.application_id
        WHERE TRIM(a.address) REGEXP :region
          AND (:keyword = '' OR LOCATE(:keyword, a.address) > 0
            OR LOCATE(:keyword, LPAD(CONCAT('', a.application_id),
              GREATEST(6, LENGTH(CONCAT('', a.application_id))), '0')) > 0)
        """);
    if (request.status() != null) {
      filter.append(" AND i.status = :status");
      params.addValue("status", request.status().name());
    }
    if (request.inspectorUserId() != null) {
      // 담당자를 생략하면 배정 여부와 관계없이 조회한다.
      filter.append(" AND i.inspector_user_id = :inspectorUserId");
      params.addValue("inspectorUserId", request.inspectorUserId());
    }
    if (request.visitDate() != null) {
      // 확정 일정(scheduled_at)이 아니라 주민이 신청한 방문 희망일을 비교한다.
      filter.append(" AND a.preferred_visit_date = :visitDate");
      params.addValue("visitDate", request.visitDate());
    }
    // 건수와 목록이 같은 JOIN·WHERE 절을 공유해야 필터 적용 후 페이지 수가 일치한다.
    long total = jdbc.queryForObject("SELECT COUNT(*) " + filter, params, Long.class);
    // 우선점검을 먼저 배치하고 신청 ID·점검 ID로 페이지 정렬을 일관되게 유지한다.
    var items =
        jdbc.query(
            """
        SELECT i.inspection_id, a.application_id, a.address, d.application_id AS designated_id,
          a.preferred_visit_date, a.preferred_time_slot, i.inspector_user_id, u.name AS inspector_name, i.status
        """
                + filter
                + " ORDER BY CASE WHEN d.application_id IS NOT NULL THEN 0 ELSE 1 END,"
                + " a.application_id DESC, i.inspection_id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, rowNum) -> {
              // 선택 입력값의 SQL NULL을 응답에도 유지한다. 화면용 대체 문구는 만들지 않는다.
              var date = rs.getDate("preferred_visit_date");
              var slot = rs.getString("preferred_time_slot");
              return new InspectionListResponse.Item(
                  rs.getLong("inspection_id"),
                  rs.getLong("application_id"),
                  String.format(Locale.ROOT, "%06d", rs.getLong("application_id")),
                  districtOnly(rs.getString("address")),
                  rs.getObject("designated_id") != null,
                  date == null ? null : date.toLocalDate(),
                  slot == null ? null : PreferredTimeSlot.valueOf(slot),
                  rs.getObject("inspector_user_id", Long.class),
                  rs.getString("inspector_name"),
                  InspectionStatus.valueOf(rs.getString("status")));
            });
    // 전체 건수는 현재 페이지와 독립적이므로 범위를 넘는 페이지에서도 그대로 반환한다.
    return new InspectionListResponse(
        items,
        request.page(),
        request.size(),
        total,
        (total + request.size() - 1) / request.size());
  }

  // 전체 주소 대신 행정구역만 반환, 구를 찾지 못하면 null
  private String districtOnly(String address) {
    // 식별 실패 시 전체 주소로 대체하지 않아 목록 응답에 상세 위치가 노출되지 않게 한다.
    var matcher = DISTRICT.matcher(address.strip());
    return matcher.find() ? matcher.group(1) : null;
  }
}
