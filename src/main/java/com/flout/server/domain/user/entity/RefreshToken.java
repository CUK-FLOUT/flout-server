package com.flout.server.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
    indexes = @Index(name = "idx_refresh_tokens_expiry", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "refresh_token_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static RefreshToken create(
      User user, String hash, LocalDateTime createdAt, LocalDateTime expiresAt) {
    var token = new RefreshToken();
    token.user = user;
    token.tokenHash = hash;
    token.createdAt = createdAt;
    token.expiresAt = expiresAt;
    return token;
  }
}
