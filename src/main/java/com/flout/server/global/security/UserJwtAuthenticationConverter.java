package com.flout.server.global.security;

import com.flout.server.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final UserRepository users;

  @Override
  public AbstractAuthenticationToken convert(Jwt token) {
    try {
      var user =
          users
              .findById(Long.valueOf(token.getSubject()))
              .orElseThrow(() -> new BadCredentialsException("Invalid token"));
      if (!user.getRole().name().equals(token.getClaimAsString("role"))) {
        throw new BadCredentialsException("Invalid token");
      }
      return new JwtAuthenticationToken(
          token, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    } catch (NumberFormatException e) {
      throw new BadCredentialsException("Invalid token");
    }
  }
}
