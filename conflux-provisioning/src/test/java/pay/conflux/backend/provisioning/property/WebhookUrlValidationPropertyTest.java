package pay.conflux.backend.provisioning.property;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;

/** All http://... URLs are rejected; all https://... URLs are accepted by the request DTO. */
class WebhookUrlValidationPropertyTest {

  private static Validator validator() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator();
    }
  }

  @Provide
  Arbitrary<String> hostPaths() {
    Arbitrary<String> host =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(20);
    Arbitrary<String> path =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(0).ofMaxLength(20);
    return Arbitraries.lazyOf(() -> host.flatMap(h -> path.map(p -> h + ".example/" + p)));
  }

  @Property(tries = 500)
  void httpsUrl_isAccepted(@ForAll("hostPaths") String hostPath) {
    UpdateWebhookRequest req = new UpdateWebhookRequest("https://" + hostPath);
    Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator().validate(req);
    assertThat(violations).as("https URL %s should pass validation", req.getWebhookUrl()).isEmpty();
  }

  @Property(tries = 500)
  void httpUrl_isRejected(@ForAll("hostPaths") String hostPath) {
    UpdateWebhookRequest req = new UpdateWebhookRequest("http://" + hostPath);
    Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator().validate(req);
    assertThat(violations)
        .as("http URL %s should fail validation", req.getWebhookUrl())
        .isNotEmpty();
  }

  @Property(tries = 500)
  void nonHttpsScheme_isRejected(
      @ForAll @AlphaChars @NotBlank @StringLength(min = 1, max = 6) String scheme,
      @ForAll("hostPaths") String hostPath) {
    String s = scheme.toLowerCase();
    if (s.equals("https")) {
      return;
    }
    UpdateWebhookRequest req = new UpdateWebhookRequest(s + "://" + hostPath);
    Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator().validate(req);
    assertThat(violations)
        .as("non-https URL %s should fail validation", req.getWebhookUrl())
        .isNotEmpty();
  }
}
