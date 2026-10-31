package com.flout.server.domain.address;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.global.response.code.GeneralErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.inspection.auto-start.enabled=false")
@AutoConfigureMockMvc
class AddressSecurityTest {
  @Autowired MockMvc mvc;

  @Test
  void bothAddressEndpointsRequireAuthentication() throws Exception {
    for (String path :
        new String[] {"/api/resident/addresses/search", "/api/resident/addresses/coordinates"}) {
      mvc.perform(get(path))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(GeneralErrorCode.UNAUTHORIZED_ERROR.getCode()))
          .andExpect(jsonPath("$.message").value(GeneralErrorCode.UNAUTHORIZED_ERROR.getMessage()));
      mvc.perform(get(path).header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(GeneralErrorCode.INVALID_ACCESS_TOKEN.getCode()))
          .andExpect(
              jsonPath("$.message").value(GeneralErrorCode.INVALID_ACCESS_TOKEN.getMessage()));
    }
  }
}
