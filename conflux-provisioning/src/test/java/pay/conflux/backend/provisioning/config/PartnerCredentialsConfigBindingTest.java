package pay.conflux.backend.provisioning.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = PartnerCredentialsConfigBindingTest.TestConfig.class)
@TestPropertySource(
    properties = {
      "conflux.adapters.partner-credentials.sslcommerz.storeId=foo",
      "conflux.adapters.partner-credentials.sslcommerz.storePasswd=bar",
      "conflux.adapters.partner-credentials.sslcommerz.baseUrl=https://example"
    })
class PartnerCredentialsConfigBindingTest {

  @Configuration
  @EnableConfigurationProperties(PartnerCredentialsConfig.class)
  static class TestConfig {}

  @Autowired private PartnerCredentialsConfig config;

  @Test
  void sslcommerzCredentialsBindWithRelaxedCamelCaseKeys() {
    Map<String, String> creds = config.credentialsFor("sslcommerz");
    assertThat(creds)
        .containsEntry("storeId", "foo")
        .containsEntry("storePasswd", "bar")
        .containsEntry("baseUrl", "https://example");
  }
}
