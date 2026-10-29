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
  private static final String BUCHEON = "^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)";
  private static final Pattern DISTRICT =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시\\s+(원미구|소사구|오정구)(?:\\s|$)");
  private final NamedParameterJdbcTemplate jdbc;

  // 목록과 건수에 같은 조건 적용, 검색은 원본 주소로 수행
  public InspectionListResponse search(SearchInspectionsRequest request) {
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
      filter.append(" AND i.inspector_user_id = :inspectorUserId");
      params.addValue("inspectorUserId", request.inspectorUserId());
    }
    if (request.visitDate() != null) {
      filter.append(" AND a.preferred_visit_date = :visitDate");
      params.addValue("visitDate", request.visitDate());
    }
    long total = jdbc.queryForObject("SELECT COUNT(*) " + filter, params, Long.class);
    var items =
        jdbc.query(
            """
        SELECT i.inspection_id, a.application_id, a.address, d.application_id AS designated_id,
          a.preferred_visit_date, a.preferred_time_slot, i.inspector_user_id, u.name AS inspector_name, i.status
        """
                + filter
                + " ORDER BY a.application_id DESC, i.inspection_id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, rowNum) -> {
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
    return new InspectionListResponse(
        items,
        request.page(),
        request.size(),
        total,
        (total + request.size() - 1) / request.size());
  }

  // 전체 주소 대신 행정구역만 반환, 구를 찾지 못하면 null
  private String districtOnly(String address) {
    var matcher = DISTRICT.matcher(address.strip());
    return matcher.find() ? matcher.group(1) : null;
  }
}
