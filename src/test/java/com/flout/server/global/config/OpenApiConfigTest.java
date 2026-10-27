package com.flout.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
  @Test
  void errorReferencesResolveEvenWhenInitialSchemaHasBeenRemoved() {
    OpenAPI api = new OpenAPI().components(new Components()).paths(new Paths());
    for (String path : Set.of("/api/auth/google", "/api/auth/admin/google", "/api/auth/login")) {
      api.getPaths().addPathItem(path, new PathItem().post(new Operation()));
    }
    new OpenApiConfig().authDocumentation(3600).customise(api);
    assertThat(api.getComponents().getSchemas()).containsKey("CommonErrorResponse");
    api.getPaths()
        .values()
        .forEach(
            path ->
                path.readOperations()
                    .forEach(
                        operation ->
                            operation
                                .getResponses()
                                .values()
                                .forEach(
                                    response -> {
                                      var schema =
                                          response.getContent().get("application/json").getSchema();
                                      if (schema != null && schema.get$ref() != null) {
                                        String ref = schema.get$ref();
                                        assertThat(ref).startsWith("#/components/schemas/");
                                        assertThat(api.getComponents().getSchemas())
                                            .containsKey(
                                                ref.substring("#/components/schemas/".length()));
                                      }
                                    })));
  }
}
