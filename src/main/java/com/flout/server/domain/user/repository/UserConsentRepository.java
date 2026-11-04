package com.flout.server.domain.user.repository;

import com.flout.server.domain.user.entity.ConsentType;
import com.flout.server.domain.user.entity.UserConsent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
  Optional<UserConsent> findByUserIdAndConsentTypeAndVersion(
      Long userId, ConsentType type, String version);
}
