package com.flout.server.domain.user.repository;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<User> findByGoogleSubject(String subject);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.email = :email")
  Optional<User> findForGoogleByEmail(@Param("email") String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> findForConsentUpdate(@Param("userId") Long userId);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      update User u set u.lastLoginAt = :loginAt, u.updatedAt = :loginAt
      where u.id = :userId and u.role = :role
        and u.email = :email and u.passwordHash = :passwordHash
      """)
  int updateLoginTime(
      @Param("userId") Long userId,
      @Param("role") UserRole role,
      @Param("email") String email,
      @Param("passwordHash") String passwordHash,
      @Param("loginAt") LocalDateTime loginAt);
}
