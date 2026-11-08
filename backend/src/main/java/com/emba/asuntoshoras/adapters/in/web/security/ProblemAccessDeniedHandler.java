package com.emba.asuntoshoras.adapters.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 {@code forbidden} en {@code application/problem+json}. */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  public static final String CODE = "forbidden";

  private final ProblemResponseWriter writer;

  public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    writer.write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        CODE,
        "Acceso denegado",
        "No tienes permiso para esta operación.");
  }
}
