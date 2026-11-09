package com.flout.server.domain.publicissue.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPublicIssueDetailRepository {
  private static final int RELATED_RADIUS_METERS = 1000;
  private static final String BUCHEON = "^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)";
  private final NamedParameterJdbcTemplate jdbc;

  public Optional<Header> findHeader(Long publicIssueId) {
    var rows =
        jdbc.query(
            """
            SELECT p.public_issue_id, p.issue_type, p.status, p.address, p.latitude, p.longitude,
              p.description, p.field_findings, p.created_at,
              i.inspection_id, a.application_id, a.receipt_no, a.applicant_name,
              r.review_id, r.review_target, r.review_status, r.inspection_note, r.review_reason,
              r.user_id AS reviewer_user_id, reviewer.name AS reviewer_name, r.reviewed_at,
              f.facility_id, f.facility_name, f.facility_type, f.address AS facility_address,
              f.latitude AS facility_latitude, f.longitude AS facility_longitude
            FROM public_issues p
            JOIN inspections i ON i.inspection_id = p.source_inspection_id
            JOIN inspection_applications a ON a.application_id = i.application_id
            LEFT JOIN facility_review r ON r.local_problem_id = p.public_issue_id
            LEFT JOIN users reviewer ON reviewer.user_id = r.user_id
            LEFT JOIN public_facility f ON f.facility_id = r.facility_id
            WHERE p.public_issue_id = :publicIssueId
            """,
            new MapSqlParameterSource("publicIssueId", publicIssueId),
            (rs, rowNum) ->
                new Header(
                    rs.getLong("public_issue_id"),
                    rs.getString("issue_type"),
                    rs.getString("status"),
                    rs.getString("address"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    rs.getString("description"),
                    rs.getString("field_findings"),
                    rs.getObject("created_at", LocalDateTime.class),
                    rs.getLong("inspection_id"),
                    rs.getLong("application_id"),
                    rs.getString("receipt_no"),
                    rs.getString("applicant_name"),
                    rs.getObject("review_id", Long.class),
                    rs.getString("review_target"),
                    rs.getString("review_status"),
                    rs.getString("inspection_note"),
                    rs.getString("review_reason"),
                    rs.getObject("reviewer_user_id", Long.class),
                    rs.getString("reviewer_name"),
                    rs.getObject("reviewed_at", LocalDateTime.class),
                    rs.getObject("facility_id", Long.class),
                    rs.getString("facility_name"),
                    rs.getString("facility_type"),
                    rs.getString("facility_address"),
                    rs.getBigDecimal("facility_latitude"),
                    rs.getBigDecimal("facility_longitude")));
    return rows.stream().findFirst();
  }

  public List<RelatedApplicationRow> findRelatedApplications(
      BigDecimal latitude, BigDecimal longitude) {
    var params =
        new MapSqlParameterSource()
            .addValue("latitude", latitude)
            .addValue("longitude", longitude)
            .addValue("jurisdiction", BUCHEON)
            .addValue("radius", RELATED_RADIUS_METERS);
    return jdbc.query(
        """
        SELECT a.application_id, a.receipt_no, a.applicant_name, a.address, a.detail_address,
          r.risk_level, a.flood_experience, a.existing_facility, a.request_reason
        FROM inspection_applications a
        JOIN risk_assessments r ON r.assessment_id = a.assessment_id
        WHERE a.status <> 'DRAFT' AND REGEXP_LIKE(TRIM(a.address), :jurisdiction)
          AND 6371000.0 * 2 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
            POWER(SIN(RADIANS(a.latitude - :latitude) / 2), 2)
            + COS(RADIANS(:latitude)) * COS(RADIANS(a.latitude))
            * POWER(SIN(RADIANS(a.longitude - :longitude) / 2), 2)
          )))) <= :radius
        ORDER BY a.application_id DESC
        """,
        params,
        (rs, rowNum) ->
            new RelatedApplicationRow(
                rs.getLong("application_id"),
                rs.getString("receipt_no"),
                rs.getString("applicant_name"),
                rs.getString("address"),
                rs.getString("detail_address"),
                rs.getString("risk_level"),
                rs.getString("flood_experience"),
                rs.getBoolean("existing_facility"),
                rs.getString("request_reason")));
  }

  public Map<Long, List<String>> findInflowLocations(List<Long> applicationIds) {
    if (applicationIds.isEmpty()) return Map.of();
    return jdbc
        .query(
            """
            SELECT application_id, inflow_location
            FROM application_inflow_locations
            WHERE application_id IN (:applicationIds)
            ORDER BY application_id, inflow_location
            """,
            new MapSqlParameterSource("applicationIds", applicationIds),
            (rs, rowNum) ->
                Map.entry(rs.getLong("application_id"), rs.getString("inflow_location")))
        .stream()
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }

  public List<ActionRow> findActions(Long publicIssueId) {
    return jdbc.query(
        """
        SELECT a.action_id, a.user_id, u.name AS operator_name, a.action_description,
          a.action_status, a.created_at, a.completed_at
        FROM facility_action a
        JOIN facility_review r ON r.review_id = a.review_id
        JOIN users u ON u.user_id = a.user_id
        WHERE r.local_problem_id = :publicIssueId
        ORDER BY a.created_at, a.action_id
        """,
        new MapSqlParameterSource("publicIssueId", publicIssueId),
        (rs, rowNum) ->
            new ActionRow(
                rs.getLong("action_id"),
                rs.getLong("user_id"),
                rs.getString("operator_name"),
                rs.getString("action_description"),
                rs.getString("action_status"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class)));
  }

  public Map<Long, List<ImageRow>> findImages(List<Long> actionIds) {
    if (actionIds.isEmpty()) return Map.of();
    return jdbc
        .query(
            """
            SELECT action_id, image_id, image_url, uploaded_at
            FROM facility_action_image
            WHERE action_id IN (:actionIds)
            ORDER BY action_id, image_id
            """,
            new MapSqlParameterSource("actionIds", actionIds),
            (rs, rowNum) ->
                new ImageRow(
                    rs.getLong("action_id"),
                    rs.getLong("image_id"),
                    rs.getString("image_url"),
                    rs.getObject("uploaded_at", LocalDateTime.class)))
        .stream()
        .collect(
            Collectors.groupingBy(ImageRow::actionId, LinkedHashMap::new, Collectors.toList()));
  }

  public record Header(
      Long publicIssueId,
      String issueType,
      String status,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String description,
      String fieldFindings,
      LocalDateTime createdAt,
      Long inspectionId,
      Long applicationId,
      String receiptNo,
      String applicantName,
      Long reviewId,
      String reviewTarget,
      String reviewStatus,
      String inspectionNotes,
      String reviewReason,
      Long reviewerUserId,
      String reviewerName,
      LocalDateTime reviewedAt,
      Long facilityId,
      String facilityName,
      String facilityType,
      String facilityAddress,
      BigDecimal facilityLatitude,
      BigDecimal facilityLongitude) {}

  public record RelatedApplicationRow(
      Long applicationId,
      String receiptNo,
      String applicantName,
      String address,
      String detailAddress,
      String riskLevel,
      String floodExperience,
      boolean existingFacility,
      String requestReason) {}

  public record ActionRow(
      Long actionId,
      Long operatorUserId,
      String operatorName,
      String actionDescription,
      String status,
      LocalDateTime createdAt,
      LocalDateTime completedAt) {}

  public record ImageRow(Long actionId, Long imageId, String objectKey, LocalDateTime uploadedAt) {}
}
