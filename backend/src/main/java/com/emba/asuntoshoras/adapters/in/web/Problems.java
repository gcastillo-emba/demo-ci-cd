package com.emba.asuntoshoras.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fábrica de Problem Details (RFC 9457) con {@code type}, {@code code} y {@code traceId}. */
public final class Problems {

  public static final String TYPE_PREFIX = "https://asuntos-horas.example/problems/";
  public static final String PROPERTY_CODE = "code";
  public static final String PROPERTY_TRACE_ID = "traceId";
  public static final String PROPERTY_ERRORS = "errors";

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_PREFIX + code));
    problem.setTitle(title);
    problem.setProperty(PROPERTY_CODE, code);
    problem.setProperty(PROPERTY_TRACE_ID, currentTraceId());
    return problem;
  }

  /** Agrega errores de campo ({@code errors[]{field,message,code?}}). */
  public static ProblemDetail withErrors(ProblemDetail problem, List<FieldMessage> errors) {
    problem.setProperty(PROPERTY_ERRORS, errors);
    return problem;
  }

  /** Identificador de traza de la solicitud en curso (lo fija {@link RequestIdFilter}). */
  public static String currentTraceId() {
    String id = MDC.get(RequestIdFilter.MDC_KEY);
    return id != null ? id : UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Error de un campo de la solicitud. {@code code} es opcional (kebab-case, p. ej. {@code
   * assistant-note-empty}); si es nulo no se serializa ({@code default-property-inclusion:
   * non_null}).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record FieldMessage(String field, String message, String code) {

    public FieldMessage(String field, String message) {
      this(field, message, null);
    }
  }
}
