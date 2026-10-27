package com.flout.server.domain.user.repository;

import com.flout.server.domain.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByTokenHash(String hash);

  @Modifying
  @Query("delete from RefreshToken t where t.tokenHash = :hash and t.expiresAt > :now")
  int consume(@Param("hash") String hash, @Param("now") LocalDateTime now);
}
