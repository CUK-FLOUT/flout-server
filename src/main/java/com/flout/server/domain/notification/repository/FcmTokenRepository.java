package com.flout.server.domain.notification.repository;

import com.flout.server.domain.notification.entity.FcmToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
  Optional<FcmToken> findByTokenHash(String tokenHash);

  // 토큰 중복 생성 방지 및 현재 로그인 사용자로 연결 갱신
  @Modifying
  @Query(
      value =
          """
      INSERT INTO fcm_tokens (user_id, token, token_hash, created_at, updated_at)
      VALUES (:userId, :token, :tokenHash, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      ON DUPLICATE KEY UPDATE user_id = :userId, token = :token, updated_at = CURRENT_TIMESTAMP
      """,
      nativeQuery = true)
  void register(
      @Param("userId") Long userId,
      @Param("token") String token,
      @Param("tokenHash") String tokenHash);

  // 본인에게 연결된 이전 토큰만 정리
  long deleteByUserIdAndTokenHash(Long userId, String tokenHash);
}
