package com.emba.asuntoshoras.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

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
