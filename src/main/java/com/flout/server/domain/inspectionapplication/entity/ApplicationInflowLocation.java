package com.flout.server.domain.inspectionapplication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "application_inflow_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationInflowLocation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "inflow_location_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private InspectionApplication application;

  @Enumerated(EnumType.STRING)
  @Column(name = "inflow_location", nullable = false, length = 20)
  private InflowLocation inflowLocation;

  public static ApplicationInflowLocation of(
      InspectionApplication application, InflowLocation inflowLocation) {
    ApplicationInflowLocation location = new ApplicationInflowLocation();
    location.application = application;
    location.inflowLocation = inflowLocation;
    return location;
  }
}
