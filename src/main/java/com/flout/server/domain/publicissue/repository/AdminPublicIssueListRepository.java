package com.flout.server.domain.publicissue.repository;

import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueListResponse;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueMapResponse;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueStatusResponse;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPublicIssueListRepository {
  private static final int RELATED_RADIUS_METERS = 1000;
  private static final String BUCHEON = "^(?:(?:경기도|경기)\\s+)?부천시";
  private static final String FILTER =
      """
      FROM public_issues p
      WHERE REGEXP_LIKE(TRIM(p.address), :region)
        AND (:status = '' OR CONCAT('', p.status) = :status)
        AND (:issueType = '' OR CONCAT('', p.issue_type) = :issueType)
        AND (:keyword = '' OR LOCATE(:keyword, p.address) > 0
          OR LOCATE(:keyword, p.description) > 0
          OR LOCATE(:keyword, p.field_findings) > 0)
      """;
  private static final String RELATED_APPLICATIONS =
      """
      FROM inspection_applications a
      WHERE a.status <> 'DRAFT'
        AND REGEXP_LIKE(TRIM(a.address), :jurisdiction)
        AND 6371000.0 * 2 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
          POWER(SIN(RADIANS(a.latitude - p.latitude) / 2), 2)
          + COS(RADIANS(p.latitude)) * COS(RADIANS(a.latitude))
          * POWER(SIN(RADIANS(a.longitude - p.longitude) / 2), 2)
        )))) <= :radius
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AdminPublicIssueListResponse search(
      String district, String status, String issueType, String keyword, int page, int size) {
    var params =
        new MapSqlParameterSource()
            .addValue(
                "region", BUCHEON + (district.isEmpty() ? "" : "\\s+" + district) + "(?:\\s|$)")
            .addValue("jurisdiction", BUCHEON + "(?:\\s|$)")
            .addValue("status", status)
            .addValue("issueType", issueType)
            .addValue("keyword", keyword)
            .addValue("radius", RELATED_RADIUS_METERS)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    long total = jdbc.queryForObject("SELECT COUNT(*) " + FILTER, params, Long.class);
    var items =
        jdbc.query(
            """
            SELECT p.public_issue_id, p.issue_type, p.status,
              (SELECT COUNT(*)
            """
                + RELATED_APPLICATIONS
                + """
                ) AS related_application_count,
              (SELECT COUNT(*)
            """
                + RELATED_APPLICATIONS
                + """
                  AND a.flood_experience = 'YES') AS flood_history_count
            """
                + FILTER
                + " ORDER BY p.public_issue_id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, rowNum) ->
                new AdminPublicIssueListResponse.Item(
                    rs.getLong("public_issue_id"),
                    PublicIssueType.valueOf(rs.getString("issue_type")),
                    rs.getLong("related_application_count"),
                    rs.getLong("flood_history_count"),
                    PublicIssueStatus.valueOf(rs.getString("status"))));
    return new AdminPublicIssueListResponse(items, page, size, total, Math.ceilDiv(total, size));
  }

  public AdminPublicIssueStatusResponse status(String district, String issueType, String keyword) {
    var params =
        new MapSqlParameterSource()
            .addValue(
                "region", BUCHEON + (district.isEmpty() ? "" : "\\s+" + district) + "(?:\\s|$)")
            .addValue("issueType", issueType)
            .addValue("keyword", keyword);
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) AS registered_count,
          COUNT(CASE WHEN p.status IN ('RECEIVED','UNDER_REVIEW','INSPECTION_REQUIRED') THEN 1 END)
            AS pending_or_under_review_count,
          COUNT(CASE WHEN p.status = 'ACTION_IN_PROGRESS' THEN 1 END)
            AS action_in_progress_count,
          COUNT(CASE WHEN p.status = 'COMPLETED' THEN 1 END) AS completed_count
        FROM public_issues p
        WHERE REGEXP_LIKE(TRIM(p.address), :region)
          AND (:issueType = '' OR CONCAT('', p.issue_type) = :issueType)
          AND (:keyword = '' OR LOCATE(:keyword, p.address) > 0
            OR LOCATE(:keyword, p.description) > 0
            OR LOCATE(:keyword, p.field_findings) > 0)
        """,
        params,
        (rs, rowNum) ->
            new AdminPublicIssueStatusResponse(
                rs.getLong("registered_count"),
                rs.getLong("pending_or_under_review_count"),
                rs.getLong("action_in_progress_count"),
                rs.getLong("completed_count")));
  }

  public AdminPublicIssueMapResponse markers(
      String district, String status, String issueType, String keyword) {
    var params =
        new MapSqlParameterSource()
            .addValue(
                "region", BUCHEON + (district.isEmpty() ? "" : "\\s+" + district) + "(?:\\s|$)")
            .addValue("status", status)
            .addValue("issueType", issueType)
            .addValue("keyword", keyword);
    var markers =
        jdbc.query(
            "SELECT p.public_issue_id, p.latitude, p.longitude, p.status "
                + FILTER
                + " ORDER BY p.public_issue_id DESC",
            params,
            (rs, rowNum) ->
                new AdminPublicIssueMapResponse.Marker(
                    rs.getLong("public_issue_id"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    PublicIssueStatus.valueOf(rs.getString("status"))));
    return new AdminPublicIssueMapResponse(markers);
  }
}
