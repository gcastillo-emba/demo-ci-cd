package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractIT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
class AuthIT extends AbstractIT {

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private Clock clock;

  @ParameterizedTest
  @CsvSource({
    "socia@asuntos.test,    Socia Demo,   PARTNER",
    "abogado1@asuntos.test, Abogado Uno,  LAWYER",
    "abogado2@asuntos.test, Abogada Dos,  LAWYER",
    "abogado3@asuntos.test, Abogado Tres, LAWYER"
  })
  void demoUsersCanLogIn(String email, String fullName, String role) throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, DEMO_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(7200))
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(jsonPath("$.user.fullName").value(fullName))
        .andExpect(jsonPath("$.user.role").value(role))
        .andExpect(jsonPath("$.user.id").isNotEmpty());
  }

  @Test
  void wrongPasswordIs401InvalidCredentials() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(PARTNER_EMAIL, "Incorrecta1!")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(
                jsonPath("$.type")
                    .value("https://asuntos-horas.example/problems/invalid-credentials"))
            .andExpect(jsonPath("$.title").value("Credenciales inválidas"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("El email o la contraseña no son correctos."))
            .andExpect(jsonPath("$.code").value("invalid-credentials"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andReturn();

    String traceId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("traceId").asText();
    assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo(traceId);
  }

  @Test
  void unknownEmailIsIndistinguishableFromWrongPassword() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("nadie@asuntos.test", DEMO_PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid-credentials"));
  }

  @Test
  void invalidBodyIs400ValidationErrorWithFieldErrors() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-es-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'password')].message").exists());
  }

  @Test
  void malformedJsonIs400ValidationError() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-error"));
  }

  @Test
  void meWithoutTokenIs401Unauthenticated() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(
            jsonPath("$.type").value("https://asuntos-horas.example/problems/unauthenticated"))
        .andExpect(jsonPath("$.title").value("No autenticado"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("unauthenticated"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  void meWithTokenReturnsCurrentUser() throws Exception {
    String token = accessToken(PARTNER_EMAIL);

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PARTNER_ID))
        .andExpect(jsonPath("$.email").value(PARTNER_EMAIL))
        .andExpect(jsonPath("$.fullName").value("Socia Demo"))
        .andExpect(jsonPath("$.role").value("PARTNER"));
  }

  @Test
  void tamperedTokenIs401() throws Exception {
    String token = accessToken(PARTNER_EMAIL);

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void expiredTokenIs401() throws Exception {
    Instant issuedAt = clock.instant().minus(Duration.ofHours(3));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(PARTNER_ID)
            .claim("role", "PARTNER")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofHours(2)))
            .build();
    String expired =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void tokenForUnknownUserIs404UserNotFound() throws Exception {
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("00000000-0000-4000-8000-0000000000ff")
            .claim("role", "LAWYER")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofHours(2)))
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("user-not-found"));
  }

  @Test
  void logsNeverContainEmailPasswordOrToken(CapturedOutput output) throws Exception {
    String token = accessToken(PARTNER_EMAIL);
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PARTNER_EMAIL, "Incorrecta1!")))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());

    assertThat(output.getAll())
        .contains(PARTNER_ID)
        .doesNotContain(PARTNER_EMAIL)
        .doesNotContain(DEMO_PASSWORD)
        .doesNotContain("Incorrecta1!")
        .doesNotContain(token);
  }
}
