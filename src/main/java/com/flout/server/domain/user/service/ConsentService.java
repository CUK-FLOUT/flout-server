package com.flout.server.domain.user.service;

import com.flout.server.domain.user.entity.*;
import com.flout.server.domain.user.repository.UserConsentRepository;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {
  private final UserRepository users;
  private final UserConsentRepository consents;
  private final String thirdPartyVersion;
  private final String notificationVersion;

  public ConsentService(
      UserRepository users,
      UserConsentRepository consents,
      @Value("${app.consent.third-party-provision-version:1.0}") String thirdPartyVersion,
      @Value("${app.consent.status-notification-version:1.0}") String notificationVersion) {
    this.users = users;
    this.consents = consents;
    this.thirdPartyVersion = thirdPartyVersion;
    this.notificationVersion = notificationVersion;
  }

  @Transactional(readOnly = true)
  public boolean requiresConsent(User user) {
    return user.getRole() == UserRole.RESIDENT
        && (!isAgreed(user.getId(), ConsentType.THIRD_PARTY_PROVISION, thirdPartyVersion)
            || !isAgreed(user.getId(), ConsentType.STATUS_NOTIFICATION, notificationVersion));
  }

  private boolean isAgreed(Long userId, ConsentType type, String version) {
    return consents
        .findByUserIdAndConsentTypeAndVersion(userId, type, version)
        .map(UserConsent::isAgreed)
        .orElse(false);
  }

  @Transactional
  public void agree(Long userId) {
    User user =
        users
            .findForConsentUpdate(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    LocalDateTime now = LocalDateTime.now();
    save(user, ConsentType.THIRD_PARTY_PROVISION, thirdPartyVersion, now);
    save(user, ConsentType.STATUS_NOTIFICATION, notificationVersion, now);
  }

  private void save(User user, ConsentType type, String version, LocalDateTime now) {
    var existing = consents.findByUserIdAndConsentTypeAndVersion(user.getId(), type, version);
    if (existing.isPresent()) {
      existing.get().confirm(now);
    } else {
      consents.save(UserConsent.agree(user, type, version, now));
    }
  }
}
