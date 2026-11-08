package com.emba.asuntoshoras.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHasherTest {

  private static final String DEMO_PASSWORD = "Demo2026!";
  private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

  private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

  @Test
  void seedHashesMatchTheDocumentedDemoPassword() throws IOException {
    List<String> hashes = seedHashes();

    assertThat(hashes).hasSize(4);
    assertThat(hashes).allSatisfy(h -> assertThat(hasher.matches(DEMO_PASSWORD, h)).isTrue());
    assertThat(hashes).allSatisfy(h -> assertThat(hasher.matches("demo2026!", h)).isFalse());
  }

  @Test
  void rejectsMissingInputs() {
    String hash = "$2a$10$Nu4Qg98z9R7eQNeWWIcbr.qPITSjr1zXHFpGovbR1w5yZ1zGpkn2G";

    assertThat(hasher.matches(null, hash)).isFalse();
    assertThat(hasher.matches(DEMO_PASSWORD, null)).isFalse();
    assertThat(hasher.matches(DEMO_PASSWORD, " ")).isFalse();
  }

  @Test
  void passwordLongerThan72BytesNeverMatchesAndNeverThrows() throws IOException {
    String hash = seedHashes().get(0);
    String longPassword = "A".repeat(100);

    assertThat(hasher.matches(longPassword, hash)).isFalse();
    assertThat(hasher.matches("ñ".repeat(40), hash)).isFalse(); // 80 bytes en UTF-8
  }

  @Test
  void encoderRejectionIsTreatedAsNoMatch() {
    // Versiones de Spring Security que lanzan IllegalArgumentException con más de 72 bytes.
    BCryptPasswordEncoder strict =
        new BCryptPasswordEncoder() {
          @Override
          public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) {
              throw new IllegalArgumentException("password cannot be more than 72 bytes");
            }
            return super.matches(rawPassword, encodedPassword);
          }
        };
    BCryptPasswordHasher strictHasher = new BCryptPasswordHasher(strict);
    String hash = strict.encode(DEMO_PASSWORD);

    assertThat(strictHasher.matches("A".repeat(100), hash)).isFalse();
    assertThat(strictHasher.matches(DEMO_PASSWORD, hash)).isTrue();
    assertThatNoException().isThrownBy(() -> strictHasher.dummyCheck("A".repeat(100)));
  }

  @Test
  void dummyCheckNeverThrows() {
    assertThatNoException().isThrownBy(() -> hasher.dummyCheck(DEMO_PASSWORD));
    assertThatNoException().isThrownBy(() -> hasher.dummyCheck("A".repeat(100)));
    assertThatNoException().isThrownBy(() -> hasher.dummyCheck(null));
  }

  private static List<String> seedHashes() throws IOException {
    try (InputStream in =
        BCryptPasswordHasherTest.class.getResourceAsStream("/db/seed/R__seed_demo_users.sql")) {
      assertThat(in).as("seed en classpath:db/seed").isNotNull();
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Matcher matcher = BCRYPT.matcher(sql);
      return matcher.results().map(r -> r.group()).toList();
    }
  }
}
