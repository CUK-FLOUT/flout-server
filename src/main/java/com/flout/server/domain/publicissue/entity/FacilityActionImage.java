package com.flout.server.domain.publicissue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facility_action_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityActionImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "image_id")
  private Long id;

  @Column(name = "image_url", nullable = false, length = 500)
  private String imageUrl;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "action_id", nullable = false)
  private PublicIssueAction action;
}
