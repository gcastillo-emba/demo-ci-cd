package com.emba.asuntoshoras.adapters.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 {@code unauthenticated} en {@code application/problem+json}. */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  public static final String CODE = "unauthenticated";

  private final ProblemResponseWriter writer;

  public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    boolean invalidToken = authException instanceof InvalidBearerTokenException;
    response.setHeader(
        HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
    writer.write(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        CODE,
        "No autenticado",
        invalidToken
            ? "El token no es válido o expiró. Inicia sesión de nuevo."
            : "Debes iniciar sesión para acceder a este recurso.");
  }
}
