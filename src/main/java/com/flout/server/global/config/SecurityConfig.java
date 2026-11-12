package com.flout.server.global.config;

import com.flout.server.global.response.ErrorResponse;
import com.flout.server.global.response.code.GeneralErrorCode;
import com.flout.server.global.security.UserJwtAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
  private final ObjectMapper mapper;
  private final UserJwtAuthenticationConverter authenticationConverter;

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/signup",
                        "/api/auth/refresh",
                        "/api/auth/logout",
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

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:}") List<String> configuredOrigins) {
    List<String> allowedOrigins =
        configuredOrigins.stream().map(String::trim).filter(origin -> !origin.isBlank()).toList();

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
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
