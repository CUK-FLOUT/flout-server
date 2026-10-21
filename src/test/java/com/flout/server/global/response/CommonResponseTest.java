package com.flout.server.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.global.exception.BaseException;
import com.flout.server.global.exception.GlobalExceptionHandler;
import com.flout.server.global.response.code.GeneralErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class CommonResponseTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void successPreservesBsceneJsonContract() throws Exception {
    mvc.perform(get("/success"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("COMMON_200"))
        .andExpect(jsonPath("$.result").value("hello"))
        .andExpect(jsonPath("$.timeStamp").isString());
  }

  @Test
  void businessExceptionUsesCodeStatus() throws Exception {
    mvc.perform(get("/business"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value("COMMON_400_1"));
  }

  @Test
  void validationReturnsCommonError() throws Exception {
    mvc.perform(
            post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"))
        .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
  }

  @Test
  void malformedJsonReturnsCommonError() throws Exception {
    mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"));
  }

  @Test
  void unexpectedExceptionDoesNotExposeDetails() throws Exception {
    String body =
        mvc.perform(get("/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON_500_1"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("database-secret");
  }

  @RestController
  static class TestController {
    @GetMapping("/success")
    SuccessResponse<String> success() {
      return SuccessResponse.ok("hello");
    }

    @GetMapping("/business")
    void business() {
      throw new BaseException(GeneralErrorCode.BAD_REQUEST_ERROR);
    }

    @GetMapping("/unexpected")
    void unexpected() {
      throw new IllegalStateException("database-secret");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody Input input) {}
  }

  record Input(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
