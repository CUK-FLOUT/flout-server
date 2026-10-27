package com.flout.server.global.config;

import com.flout.server.global.response.ErrorResponse;
import com.flout.server.global.response.code.GeneralErrorCode;
import com.flout.server.global.security.UserJwtAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
  private final ObjectMapper mapper;
  private final UserJwtAuthenticationConverter authenticationConverter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/google",
                        "/api/auth/admin/google",
                        "/api/auth/admin/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/dev/google-login")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, e) ->
                            writeError(response, GeneralErrorCode.UNAUTHORIZED_ERROR))
                    .accessDeniedHandler(
                        (request, response, e) ->
                            writeError(response, GeneralErrorCode.ACCESS_DENIED_REQUEST)))
        .oauth2ResourceServer(
            resource ->
                resource
                    .authenticationEntryPoint(
                        (request, response, e) ->
                            writeError(response, GeneralErrorCode.INVALID_ACCESS_TOKEN))
                    .accessDeniedHandler(
                        (request, response, e) ->
                            writeError(response, GeneralErrorCode.ACCESS_DENIED_REQUEST))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));
    return http.build();
  }

  private void writeError(HttpServletResponse response, GeneralErrorCode code) throws IOException {
    response.setStatus(code.getStatus());
    if (code.getStatus() == 401) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(response.getOutputStream(), ErrorResponse.from(code));
  }
}
