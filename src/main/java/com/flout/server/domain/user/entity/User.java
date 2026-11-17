package com.flout.server.domain.user.entity;

import com.flout.server.global.time.SeoulDateTimes;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long id;

  @Column(nullable = false, length = 100)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "google_subject", unique = true, length = 255)
  private String googleSubject;

  @Column(nullable = false, length = 50)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "jurisdiction_id", foreignKey = @ForeignKey(name = "fk_users_jurisdiction"))
  private Jurisdiction jurisdiction;

  @Column(name = "last_login_at")
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime updatedAt;

  public static User resident(String email, String passwordHash, String name) {
    User user = new User();
    user.email = email;
    user.passwordHash = passwordHash;
    user.name = name;
    user.role = UserRole.RESIDENT;
    return user;
  }

  public void connectGoogle(String subject) {
    this.googleSubject = subject;
  }

  public void recordLogin() {
    this.lastLoginAt = SeoulDateTimes.now();
  }

  @PrePersist
  void onCreate() {
    createdAt = SeoulDateTimes.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = SeoulDateTimes.now();
  }
}
