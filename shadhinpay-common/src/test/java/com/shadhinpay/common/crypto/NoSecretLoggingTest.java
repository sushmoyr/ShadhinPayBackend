package com.shadhinpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Static guard: no log statement inside the {@code crypto} or {@code webhook} packages may
 * reference any of the sensitive parameter names (secret/key/plaintext/masterKey/password). Catches
 * accidental {@code log.debug("decrypted: " + plaintext)} regressions at build time.
 */
class NoSecretLoggingTest {

  private static final List<String> ROOTS =
      List.of(
          "src/main/java/com/shadhinpay/common/crypto",
          "src/main/java/com/shadhinpay/common/webhook");

  private static final Pattern LOG_LINE = Pattern.compile("\\blog\\s*\\.[a-zA-Z]+\\s*\\(");
  private static final Pattern SENSITIVE =
      Pattern.compile("\\b(secret|key|plaintext|masterKey|password|webhookSecret)\\b");

  @Test
  void noLogStatementReferencesASensitiveIdentifier() throws IOException {
    for (String root : ROOTS) {
      Path dir = Path.of(root);
      if (!Files.exists(dir)) {
        continue;
      }
      try (Stream<Path> stream = Files.walk(dir)) {
        stream
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(NoSecretLoggingTest::assertNoForbiddenLogIn);
      }
    }
  }

  private static void assertNoForbiddenLogIn(Path file) {
    try {
      String content = Files.readString(file);
      for (String line : content.split("\\r?\\n")) {
        String stripped = line.replaceAll("//.*$", "").trim();
        if (stripped.isEmpty()) {
          continue;
        }
        if (LOG_LINE.matcher(stripped).find() && SENSITIVE.matcher(stripped).find()) {
          assertThat(stripped)
              .as("File %s contains a log call referencing a sensitive identifier", file)
              .isEmpty();
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + file, e);
    }
  }
}
