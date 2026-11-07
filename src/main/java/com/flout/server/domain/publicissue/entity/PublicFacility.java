package com.flout.server.domain.publicissue.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
