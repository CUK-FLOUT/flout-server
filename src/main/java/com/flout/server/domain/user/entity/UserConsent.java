package com.flout.server.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_consents",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "consent_type", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "consent_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false, length = 20)
  private ConsentType consentType;

  @Column(nullable = false, length = 20)
  private String version;

  @Column(name = "is_agreed", nullable = false)
  private boolean agreed;

  @Column(name = "agreed_at", nullable = false)
  private LocalDateTime agreedAt;

  public void confirm(LocalDateTime time) {
    if (!agreed) {
      agreed = true;
      agreedAt = time;
    }
  }

  public static UserConsent agree(
      User user, ConsentType type, String version, LocalDateTime agreedAt) {
    UserConsent consent = new UserConsent();
    consent.user = user;
    consent.consentType = type;
    consent.version = version;
    consent.agreed = true;
    consent.agreedAt = agreedAt;
    return consent;
  }
}
