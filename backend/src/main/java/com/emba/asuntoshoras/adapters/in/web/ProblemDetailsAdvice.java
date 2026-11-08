package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.domain.shared.DomainException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción a {@code application/problem+json} (RFC 9457) con {@code code}.
 *
 * <p>Ninguna respuesta copia el mensaje de una excepción ajena al dominio, su clase, la traza ni
 * texto SQL: los {@code detail} salen del dominio (mensajes propios en español) o de plantillas
 * fijas. Lo no catalogado responde {@code 500 internal-error} con un {@code detail} genérico; la
 * excepción completa queda solo en el log, correlacionada por {@code traceId}.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  public static final String VALIDATION_ERROR = "validation-error";
  public static final String INTERNAL_ERROR = "internal-error";
  public static final String INTERNAL_ERROR_DETAIL =
      "Ocurrió un error inesperado. Si persiste, informe el identificador de traza.";

  private static final String VALIDATION_TITLE = "Solicitud inválida";
  private static final String VALIDATION_DETAIL = "La solicitud tiene campos inválidos.";
  private static final String INVALID_TYPE_MESSAGE = "Tiene un tipo o valor no válido.";

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

  /** Validación de parámetros vía proxy {@code @Validated} (interfaces generadas). */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
    List<Problems.FieldMessage> errors = new ArrayList<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      String constraint =
          violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
      errors.add(
          new Problems.FieldMessage(lastNode(violation.getPropertyPath()), messageFor(constraint)));
    }
    return ResponseEntity.badRequest().body(validationProblem(errors));
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
    return handleExceptionInternal(
        ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
  }

  /** Validación integrada de Spring MVC (si el controller no usa el proxy {@code @Validated}). */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Problems.FieldMessage> errors = new ArrayList<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      if (result instanceof ParameterErrors parameterErrors) {
        parameterErrors
            .getFieldErrors()
            .forEach(e -> errors.add(new Problems.FieldMessage(e.getField(), messageFor(e))));
        continue;
      }
      String field = result.getMethodParameter().getParameterName();
      for (MessageSourceResolvable error : result.getResolvableErrors()) {
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        errors.add(new Problems.FieldMessage(field, messageFor(constraint)));
      }
    }
    return handleExceptionInternal(
        ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
  }

  /** Parámetro con tipo o valor no convertible (p. ej. {@code size=abc}, enum o uuid inválido). */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String field =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ex.getPropertyName();
    List<Problems.FieldMessage> errors =
        field == null ? List.of() : List.of(new Problems.FieldMessage(field, INVALID_TYPE_MESSAGE));
    return handleExceptionInternal(
        ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
  }

  /** Cuerpo ilegible: si Jackson identifica el campo (p. ej. uuid mal formado), se informa. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        Problems.of(
            HttpStatus.BAD_REQUEST,
            VALIDATION_ERROR,
            VALIDATION_TITLE,
            "El cuerpo de la solicitud no es válido.");
    if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      String field = mapping.getPath().get(mapping.getPath().size() - 1).getFieldName();
      if (field != null) {
        Problems.withErrors(
            problem, List.of(new Problems.FieldMessage(field, INVALID_TYPE_MESSAGE)));
      }
    }
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
    if (status.is5xxServerError()) {
      log.error("Error inesperado del framework", ex);
    }
    return super.handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static ProblemDetail validationProblem(List<Problems.FieldMessage> errors) {
    return Problems.withErrors(
        Problems.of(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, VALIDATION_TITLE, VALIDATION_DETAIL),
        errors);
  }

  private static ResponseEntity<ProblemDetail> internalError() {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(genericProblem(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  static ProblemDetail genericProblem(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 ->
          Problems.of(status, VALIDATION_ERROR, VALIDATION_TITLE, "La solicitud no es válida.");
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
              ? Problems.of(status, INTERNAL_ERROR, "Error interno", INTERNAL_ERROR_DETAIL)
              : Problems.of(
                  status, "request-error", "Solicitud rechazada", "La solicitud fue rechazada.");
    };
  }

  private static String lastNode(Path path) {
    String name = "";
    for (Path.Node node : path) {
      if (node.getName() != null) {
        name = node.getName();
      }
    }
    return name;
  }

  private static String messageFor(FieldError error) {
    return messageFor(error.getCode() == null ? "" : error.getCode());
  }

  private static String messageFor(String constraint) {
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
