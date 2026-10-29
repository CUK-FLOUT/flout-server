package com.flout.server.domain.priorityinspection.repository;

import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionCandidatesResponse;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PriorityInspectionCandidateRepository {
  private static final int NEARBY_RADIUS_METERS = 1000;
  private static final String JURISDICTION_PATTERN = "^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)";
  private static final String FILTER =
      """
      FROM inspection_applications a
      JOIN risk_assessments r ON r.assessment_id = a.assessment_id
      LEFT JOIN priority_inspection_designations d ON d.application_id = a.application_id
      WHERE TRIM(a.address) REGEXP :region
        AND (:riskLevel = '' OR r.risk_level = :riskLevel)
        AND (:designated IS NULL
          OR (:designated = TRUE AND d.application_id IS NOT NULL)
          OR (:designated = FALSE AND d.application_id IS NULL))
        AND (:keyword = '' OR LOCATE(:keyword, a.address) > 0
          OR LOCATE(:keyword, LPAD(CONCAT('', a.application_id),
            GREATEST(6, LENGTH(CONCAT('', a.application_id))), '0')) > 0)
      """;
  private final NamedParameterJdbcTemplate jdbc;

  public PriorityInspectionCandidatesResponse search(
      String keyword,
      String district,
      RiskLevel riskLevel,
      Boolean designated,
      int page,
      int size) {
    var params =
        new MapSqlParameterSource()
            .addValue(
                "region",
                district.isEmpty()
                    ? JURISDICTION_PATTERN
                    : "^(?:(?:경기도|경기)\\s+)?부천시\\s+" + district + "(?:\\s|$)")
            .addValue("riskLevel", riskLevel == null ? "" : riskLevel.name())
            .addValue("designated", designated, Types.BOOLEAN)
            .addValue("keyword", keyword)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    long total = jdbc.queryForObject("SELECT COUNT(*) " + FILTER, params, Long.class);
    var rows =
        jdbc.query(
            """
        SELECT a.application_id, a.address, r.risk_level, a.flood_experience,
          a.request_reason, d.application_id AS designated_id
        """
                + FILTER
                + " ORDER BY a.application_id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, rowNum) ->
                new PriorityInspectionCandidatesResponse.Item(
                    rs.getLong("application_id"),
                    String.format(Locale.ROOT, "%06d", rs.getLong("application_id")),
                    rs.getString("address"),
                    rs.getString("risk_level"),
                    rs.getString("flood_experience"),
                    rs.getString("request_reason"),
                    0,
                    rs.getObject("designated_id") != null));
    if (rows.isEmpty()) {
      return new PriorityInspectionCandidatesResponse(
          List.of(), page, size, total, (total + size - 1) / size);
    }
    var counts =
        nearbyCounts(
            rows.stream().map(PriorityInspectionCandidatesResponse.Item::applicationId).toList());
    var items =
        rows.stream()
            .map(
                row ->
                    new PriorityInspectionCandidatesResponse.Item(
                        row.applicationId(),
                        row.receiptNo(),
                        row.address(),
                        row.riskLevel(),
                        row.floodExperience(),
                        row.requestReason(),
                        counts.getOrDefault(row.applicationId(), 0L),
                        row.designated()))
            .toList();
    return new PriorityInspectionCandidatesResponse(
        items, page, size, total, (total + size - 1) / size);
  }

  // 검색·페이지 조건은 기준 신청에만 적용하고 인근 건수는 관할 전체 신청에서 집계한다.
  private Map<Long, Long> nearbyCounts(List<Long> applicationIds) {
    var params =
        new MapSqlParameterSource()
            .addValue("ids", applicationIds)
            .addValue("jurisdiction", JURISDICTION_PATTERN)
            .addValue("radius", NEARBY_RADIUS_METERS);
    return jdbc
        .query(
            """
        SELECT a.application_id, COUNT(b.application_id) AS nearby_count
        FROM inspection_applications a
        LEFT JOIN inspection_applications b ON b.application_id <> a.application_id
          AND TRIM(b.address) REGEXP :jurisdiction
          AND 6371000.0 * 2 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
            POWER(SIN(RADIANS(b.latitude - a.latitude) / 2), 2)
            + COS(RADIANS(a.latitude)) * COS(RADIANS(b.latitude))
            * POWER(SIN(RADIANS(b.longitude - a.longitude) / 2), 2)
          )))) <= :radius
        WHERE a.application_id IN (:ids)
        GROUP BY a.application_id
        """,
            params,
            (rs, rowNum) -> Map.entry(rs.getLong("application_id"), rs.getLong("nearby_count")))
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
