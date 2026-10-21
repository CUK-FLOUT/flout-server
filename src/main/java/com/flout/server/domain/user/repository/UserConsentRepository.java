package com.flout.server.domain.user.repository;

import com.flout.server.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {}
