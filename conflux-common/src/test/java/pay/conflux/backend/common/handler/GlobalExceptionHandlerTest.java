package pay.conflux.backend.common.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.common.error.ForbiddenException;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.error.ValidationException;

@SpringBootTest(classes = GlobalExceptionHandlerTest.TestApp.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void resourceNotFound_returns404WithEnvelope() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "not_found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.meta.success").value(false))
        .andExpect(jsonPath("$.meta.errorCode").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.meta.message").value("Widget not found with id: 7"));
  }

  @Test
  void duplicate_returns409() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "duplicate"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.meta.errorCode").value("RESOURCE_ALREADY_EXISTS"));
  }

  @Test
  void validation_returns400ViaApiOperationHandler() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "validation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.meta.errorCode").value("VALIDATION_ERROR"));
  }

  @Test
  void invalidState_returns422() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "invalid_state"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.meta.errorCode").value("INVALID_OPERATION_STATE"));
  }

  @Test
  void unauthorized_returns401() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.meta.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void forbidden_returns403() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
  }

  @Test
  void dataIntegrity_returns409WithGenericMessage() throws Exception {
    mockMvc
        .perform(get("/__test/throw").param("kind", "data_integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.meta.errorCode").value("RESOURCE_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.meta.message").value("Resource conflict"));
  }

  @Test
  void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
    SampleBody body = new SampleBody("", -1);

    mockMvc
        .perform(
            post("/__test/validate")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.meta.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.data.name").exists())
        .andExpect(jsonPath("$.data.amount").exists());
  }

  @SpringBootApplication
  static class TestApp {

    @Bean
    TestController testController() {
      return new TestController();
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
      return new GlobalExceptionHandler();
    }
  }

  @RestController
  @RequestMapping("/__test")
  static class TestController {

    @GetMapping("/throw")
    void throwIt(@RequestParam String kind) {
      switch (kind) {
        case "not_found" -> throw new ResourceNotFoundException("Widget", 7);
        case "duplicate" -> throw new DuplicateResourceException("Widget", "name", "x");
        case "validation" -> throw new ValidationException("bad input");
        case "invalid_state" -> throw new InvalidOperationStateException("wrong state");
        case "unauthorized" -> throw new UnauthorizedException("login first");
        case "forbidden" -> throw new ForbiddenException("nope");
        case "data_integrity" -> throw new DataIntegrityViolationException("constraint");
        default -> throw new IllegalArgumentException("unknown kind: " + kind);
      }
    }

    @PostMapping("/validate")
    void validate(@RequestBody @Valid SampleBody body) {}
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class SampleBody {

    @NotBlank(message = "must not be blank")
    private String name;

    @Positive(message = "must be positive")
    private int amount;
  }
}
