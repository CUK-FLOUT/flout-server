package com.flout.server.global.security;

import static org.assertj.core.api.Assertions.*;

import com.flout.server.global.exception.BaseException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleTokenVerifierTest {
  final RSAKey key = new RSAKeyGenerator(2048).generate();
  final GoogleTokenVerifier verifier =
      new GoogleTokenVerifier(
          "client-1", NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());

  GoogleTokenVerifierTest() throws Exception {}

  String token(RSAKey signingKey, String issuer, String audience, boolean verified, long expiry)
      throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("google-subject")
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(Instant.now().plusSeconds(expiry)))
                .claim("email", "resident@gmail.com")
                .claim("email_verified", verified)
                .build());
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  @Test
  void verifiesSignedGoogleIdentity() throws Exception {
    assertThat(
            verifier
                .verify(token(key, "https://accounts.google.com", "client-1", true, 600))
                .getSubject())
        .isEqualTo("google-subject");
  }

  @Test
  void rejectsWrongAudienceIssuerExpiryEmailAndSignature() throws Exception {
    for (String token :
        new String[] {
          token(key, "https://accounts.google.com", "other-client", true, 600),
          token(key, "https://attacker.example", "client-1", true, 600),
          token(key, "https://accounts.google.com", "client-1", true, -60),
          token(key, "https://accounts.google.com", "client-1", false, 600),
          token(
              new RSAKeyGenerator(2048).generate(),
              "https://accounts.google.com",
              "client-1",
              true,
              600)
        }) assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BaseException.class);
  }

  @Test
  void missingConfigurationFailsClosed() throws Exception {
    GoogleTokenVerifier missing =
        new GoogleTokenVerifier("", NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());
    assertThatThrownBy(() -> missing.verify("anything")).isInstanceOf(BaseException.class);
  }
}
