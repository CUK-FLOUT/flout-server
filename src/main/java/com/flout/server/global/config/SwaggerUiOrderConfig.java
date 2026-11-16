package com.flout.server.global.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.resource.TransformedResource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"},
    matchIfMissing = true)
class SwaggerUiOrderConfig {
  static final String DISPLAY_ORDER_EXTENSION = "x-flout-display-order";

  private static final String INITIALIZER_OPTIONS = "window.ui = SwaggerUIBundle({";
  private static final String OPERATION_SORTER =
      """
          operationsSorter: (first, second) => {
            const rank = (item) =>
              item.getIn(["operation", "%s"], Number.MAX_SAFE_INTEGER);
            return rank(first) - rank(second);
          },
      """
          .formatted(DISPLAY_ORDER_EXTENSION);

  @Bean
  SwaggerIndexTransformer swaggerIndexTransformer(
      SwaggerUiConfigProperties properties,
      SwaggerUiOAuthProperties oauthProperties,
      SwaggerWelcomeCommon welcome,
      ObjectMapperProvider mapperProvider) {
    var delegate =
        new SwaggerIndexPageTransformer(properties, oauthProperties, welcome, mapperProvider);
    return (request, resource, chain) -> {
      var transformed = delegate.transform(request, resource, chain);
      if (!"swagger-initializer.js".equals(resource.getFilename())) {
        return transformed;
      }
      var script = transformed.getContentAsString(UTF_8);
      var orderedScript =
          script.replace(INITIALIZER_OPTIONS, INITIALIZER_OPTIONS + "\n" + OPERATION_SORTER);
      return new TransformedResource(transformed, orderedScript.getBytes(UTF_8));
    };
  }
}
