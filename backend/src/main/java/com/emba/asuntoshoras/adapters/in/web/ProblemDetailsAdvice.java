package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.domain.shared.DomainException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduce toda excepción a {@code application/problem+json} (RFC 9457) con {@code code}. */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  public static final String VALIDATION_ERROR = "validation-error";
  public static final String INTERNAL_ERROR = "internal-error";

  private final ProblemCatalog catalog;

  public ProblemDetailsAdvice(ProblemCatalog catalog) {
    this.catalog = catalog;
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
    return catalog
        .find(ex.getClass())
        .map(
            entry -> {
              log.info("Error de dominio code={}", ex.code());
              ProblemDetail problem =
                  Problems.of(entry.status(), ex.code(), entry.title(), ex.getMessage());
              return ResponseEntity.status(entry.status()).body(problem);
            })
        .orElseGet(
            () -> {
              log.error("Error de dominio sin entrada en ProblemCatalog code={}", ex.code());
              return internalError();
            });
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(genericProblem(HttpStatus.UNAUTHORIZED));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(genericProblem(HttpStatus.FORBIDDEN));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Error inesperado", ex);
    return internalError();
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Problems.FieldMessage> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new Problems.FieldMessage(e.getField(), messageFor(e)))
            .toList();
    ProblemDetail problem =
        Problems.withErrors(
            Problems.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_ERROR,
                "Solicitud inválida",
                "La solicitud tiene campos inválidos."),
            errors);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Object problem = body;
    if (!(body instanceof ProblemDetail detail)
        || detail.getProperties() == null
        || !detail.getProperties().containsKey(Problems.PROPERTY_CODE)) {
      problem = genericProblem(status);
    }
    return super.handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static ResponseEntity<ProblemDetail> internalError() {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_ERROR,
                "Error interno",
                "Ocurrió un error inesperado. Intenta de nuevo más tarde."));
  }

  static ProblemDetail genericProblem(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 ->
          Problems.of(status, VALIDATION_ERROR, "Solicitud inválida", "La solicitud no es válida.");
      case 401 ->
          Problems.of(
              status,
              "unauthenticated",
              "No autenticado",
              "Debes iniciar sesión para acceder a este recurso.");
      case 403 ->
          Problems.of(
              status, "forbidden", "Acceso denegado", "No tienes permiso para esta operación.");
      case 404 ->
          Problems.of(status, "not-found", "No encontrado", "El recurso solicitado no existe.");
      case 405 ->
          Problems.of(
              status,
              "method-not-allowed",
              "Método no permitido",
              "El método HTTP no está permitido para este recurso.");
      case 406 ->
          Problems.of(
              status,
              "not-acceptable",
              "Formato no aceptable",
              "No se puede responder en el formato solicitado.");
      case 415 ->
          Problems.of(
              status,
              "unsupported-media-type",
              "Tipo de contenido no soportado",
              "El tipo de contenido de la solicitud no está soportado.");
      default ->
          status.is5xxServerError()
              ? Problems.of(
                  status,
                  INTERNAL_ERROR,
                  "Error interno",
                  "Ocurrió un error inesperado. Intenta de nuevo más tarde.")
              : Problems.of(
                  status, "request-error", "Solicitud rechazada", "La solicitud fue rechazada.");
    };
  }

  private static String messageFor(FieldError error) {
    String constraint = error.getCode() == null ? "" : error.getCode();
    return switch (constraint) {
      case "NotNull", "NotBlank", "NotEmpty" -> "Es obligatorio.";
      case "Email" -> "Debe ser un email válido.";
      case "Size" -> "No cumple la longitud permitida.";
      case "Pattern" -> "No tiene el formato esperado.";
      case "Min", "Max", "DecimalMin", "DecimalMax" -> "Está fuera del rango permitido.";
      default -> "Valor inválido.";
    };
  }
}
