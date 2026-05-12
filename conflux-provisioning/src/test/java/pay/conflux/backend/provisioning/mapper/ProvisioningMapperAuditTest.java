package pay.conflux.backend.provisioning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the rule documented in the module CLAUDE.md: no public DTO emitted by ProvisioningMapper
 * may carry {@code credentialsEncrypted}, {@code webhookSecretEncrypted}, or {@code keyHash}.
 */
class ProvisioningMapperAuditTest {

  private static final List<String> FORBIDDEN =
      List.of("credentialsEncrypted", "webhookSecretEncrypted", "keyHash");

  @Test
  void dtoFiles_doNotMentionEncryptedOrHashFields() throws IOException {
    Path dtoDir = Path.of("src/main/java/pay/conflux/backend/provisioning/dto");
    assertThat(Files.isDirectory(dtoDir)).as("dto directory exists").isTrue();

    try (Stream<Path> walk = Files.walk(dtoDir)) {
      walk.filter(p -> p.toString().endsWith(".java"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  for (String forbidden : FORBIDDEN) {
                    assertThat(content)
                        .as("DTO %s must not declare %s", p.getFileName(), forbidden)
                        .doesNotContain(forbidden);
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }
}
