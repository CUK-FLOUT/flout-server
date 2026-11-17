package com.flout.server.domain.publicissue.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "public_facility")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicFacility {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "facility_id")
  private Long id;

  @Column(name = "facility_name", nullable = false, length = 100)
  private String facilityName;

  @Column(name = "facility_type", nullable = false, length = 50)
  private String facilityType;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime updatedAt;
}
