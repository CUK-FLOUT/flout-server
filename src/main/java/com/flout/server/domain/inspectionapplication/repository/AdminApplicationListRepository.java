package com.flout.server.domain.inspectionapplication.repository;

import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationListResponse;
import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationMapResponse;
import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationStatsResponse;
import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStage;
import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminApplicationListRepository {
  private static final String BUCHEON = "^(?:(?:경기도|경기)\\s+)?부천시";

  // Use the same latest submitted support application as the support and construction detail tabs.
  private static final String JURISDICTION_APPLICATIONS =
      """
      FROM (
        SELECT a.application_id, a.applicant_name, a.address, a.detail_address, a.latitude, a.longitude,
          LPAD(CONCAT('', a.application_id), GREATEST(6, LENGTH(CONCAT('', a.application_id))), '0') AS receipt_no,
          CONCAT('', r.risk_level) AS risk_level,
          CASE
            WHEN s.status = 'APPROVED' AND c.status = 'COMPLETED' THEN 'CONSTRUCTION_COMPLETED'
            WHEN s.status = 'APPROVED' AND c.status = 'IN_PROGRESS' THEN 'CONSTRUCTION_IN_PROGRESS'
            WHEN s.status = 'APPROVED' AND c.status = 'SCHEDULED' THEN 'CONSTRUCTION_SCHEDULED'
            WHEN s.status = 'APPROVED' THEN 'SUPPORT_APPROVED'
            WHEN s.status = 'REJECTED' THEN 'SUPPORT_REJECTED'
            WHEN s.status = 'SUBMITTED' THEN 'SUPPORT_REVIEWING'
            WHEN i.status = 'COMPLETED' OR a.status = 'INSPECTION_COMPLETED' THEN 'INSPECTION_COMPLETED'
            WHEN i.status = 'IN_PROGRESS' THEN 'INSPECTION_IN_PROGRESS'
            WHEN i.status = 'SCHEDULED' THEN 'INSPECTION_SCHEDULED'
            ELSE 'RECEIVED'
          END AS processing_status
        FROM inspection_applications a
        JOIN risk_assessments r ON r.assessment_id = a.assessment_id
        LEFT JOIN inspections i ON i.application_id = a.application_id
        LEFT JOIN support_applications s ON s.support_application_id = (
          SELECT latest.support_application_id
          FROM support_applications latest
          WHERE latest.application_id = a.application_id AND latest.status <> 'DRAFT'
          ORDER BY latest.submitted_at DESC, latest.support_application_id DESC
          LIMIT 1
        )
        LEFT JOIN constructions c ON c.support_application_id = s.support_application_id
        WHERE a.status <> 'DRAFT' AND REGEXP_LIKE(TRIM(a.address), :region)
      ) applications
      """;

  private static final String FILTERED_APPLICATIONS =
      JURISDICTION_APPLICATIONS
          + """
      WHERE (:riskLevel = '' OR risk_level = :riskLevel)
        AND (:processingStage = ''
          OR LOCATE(CONCAT(:processingStage, '_'), processing_status) = 1
          OR (:processingStage = 'INSPECTION' AND processing_status = 'RECEIVED'))
        AND (:keyword = '' OR LOCATE(:keyword, CONCAT_WS(' ', address, detail_address)) > 0
          OR LOCATE(:keyword, receipt_no) > 0)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AdminApplicationListResponse search(
      String district,
      String keyword,
      RiskLevel riskLevel,
      ApplicationProcessingStage processingStage,
      int page,
      int size) {
    var params =
        filters(district, keyword, riskLevel, processingStage)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    long total =
        jdbc.queryForObject("SELECT COUNT(*) " + FILTERED_APPLICATIONS, params, Long.class);
    var items =
        jdbc.query(
            "SELECT application_id, receipt_no, applicant_name, address, detail_address, risk_level, processing_status "
                + FILTERED_APPLICATIONS
                + " ORDER BY application_id DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, rowNum) ->
                new AdminApplicationListResponse.Item(
                    rs.getLong("application_id"),
                    rs.getString("receipt_no"),
                    rs.getString("applicant_name"),
                    rs.getString("address"),
                    rs.getString("detail_address"),
                    RiskLevel.valueOf(rs.getString("risk_level")),
                    ApplicationProcessingStatus.valueOf(rs.getString("processing_status"))));
    return new AdminApplicationListResponse(items, page, size, total, Math.ceilDiv(total, size));
  }

  public AdminApplicationStatsResponse stats() {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) AS total_count,
          COUNT(CASE WHEN processing_status = 'RECEIVED' THEN 1 END) AS assignment_pending_count,
          COUNT(CASE WHEN processing_status = 'INSPECTION_IN_PROGRESS' THEN 1 END) AS inspection_in_progress_count,
          COUNT(CASE WHEN processing_status IN (
            'SUPPORT_REVIEWING', 'SUPPORT_APPROVED', 'CONSTRUCTION_SCHEDULED', 'CONSTRUCTION_IN_PROGRESS'
          ) THEN 1 END) AS support_construction_in_progress_count
        """
            + JURISDICTION_APPLICATIONS,
        new MapSqlParameterSource("region", BUCHEON + "(?:\\s|$)"),
        (rs, rowNum) ->
            new AdminApplicationStatsResponse(
                rs.getLong("total_count"),
                rs.getLong("assignment_pending_count"),
                rs.getLong("inspection_in_progress_count"),
                rs.getLong("support_construction_in_progress_count")));
  }

  public AdminApplicationMapResponse markers(
      String district,
      String keyword,
      RiskLevel riskLevel,
      ApplicationProcessingStage processingStage) {
    var markers =
        jdbc.query(
            "SELECT application_id, latitude, longitude, risk_level, processing_status "
                + FILTERED_APPLICATIONS
                + " ORDER BY application_id DESC",
            filters(district, keyword, riskLevel, processingStage),
            (rs, rowNum) ->
                new AdminApplicationMapResponse.Marker(
                    rs.getLong("application_id"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    RiskLevel.valueOf(rs.getString("risk_level")),
                    ApplicationProcessingStatus.valueOf(rs.getString("processing_status"))));
    return new AdminApplicationMapResponse(markers);
  }

  private MapSqlParameterSource filters(
      String district,
      String keyword,
      RiskLevel riskLevel,
      ApplicationProcessingStage processingStage) {
    return new MapSqlParameterSource()
        .addValue("region", BUCHEON + (district.isEmpty() ? "" : "\\s+" + district) + "(?:\\s|$)")
        .addValue("keyword", keyword)
        .addValue("riskLevel", riskLevel == null ? "" : riskLevel.name())
        .addValue("processingStage", processingStage == null ? "" : processingStage.name());
  }
}
