package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;
import com.emba.asuntoshoras.domain.assistant.FeatureDisabledException;
import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import com.emba.asuntoshoras.domain.matter.MatterAlreadyClosedException;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.DomainException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.timeentry.ConcurrentModificationException;
import com.emba.asuntoshoras.domain.timeentry.DailyHoursExceededException;
import com.emba.asuntoshoras.domain.timeentry.EditWindowExpiredException;
import com.emba.asuntoshoras.domain.timeentry.EntryNotPendingException;
import com.emba.asuntoshoras.domain.timeentry.FutureDateException;
import com.emba.asuntoshoras.domain.timeentry.HoursInvalidIncrementException;
import com.emba.asuntoshoras.domain.timeentry.HoursOutOfRangeException;
import com.emba.asuntoshoras.domain.timeentry.MatterClosedException;
import com.emba.asuntoshoras.domain.timeentry.NotEntryOwnerException;
import com.emba.asuntoshoras.domain.timeentry.RejectionReasonRequiredException;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryNotFoundException;
import com.emba.asuntoshoras.domain.user.InvalidCredentialsException;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Catálogo de errores de dominio → HTTP. Toda subclase concreta de {@link DomainException} debe
 * tener una entrada (lo verifica {@code ProblemCatalogTest}).
 */
@Component
public class ProblemCatalog {

  /** Estado HTTP y título en español de un error de dominio. */
  public record Entry(HttpStatus status, String title) {}

  private static final Map<Class<? extends DomainException>, Entry> ENTRIES =
      Map.ofEntries(
          Map.entry(
              InvalidCredentialsException.class,
              new Entry(HttpStatus.UNAUTHORIZED, "Credenciales inválidas")),
          Map.entry(
              UserNotFoundException.class,
              new Entry(HttpStatus.NOT_FOUND, "Usuario no encontrado")),
          Map.entry(
              InvalidValueException.class, new Entry(HttpStatus.BAD_REQUEST, "Solicitud inválida")),
          Map.entry(
              ForbiddenException.class,
              new Entry(HttpStatus.FORBIDDEN, "Acción no permitida para su rol")),
          Map.entry(
              ClientNotFoundException.class,
              new Entry(HttpStatus.NOT_FOUND, "Recurso no encontrado")),
          Map.entry(
              MatterNotFoundException.class,
              new Entry(HttpStatus.NOT_FOUND, "Recurso no encontrado")),
          Map.entry(
              ClientTaxIdTakenException.class,
              new Entry(HttpStatus.CONFLICT, "Identificación tributaria ya registrada")),
          Map.entry(
              MatterAlreadyClosedException.class,
              new Entry(HttpStatus.CONFLICT, "El asunto ya está cerrado")),
          // add-time-entries (design D6)
          Map.entry(
              TimeEntryNotFoundException.class,
              new Entry(HttpStatus.NOT_FOUND, "Recurso no encontrado")),
          Map.entry(
              HoursInvalidIncrementException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Incremento de horas inválido")),
          Map.entry(
              HoursOutOfRangeException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Horas fuera de rango")),
          Map.entry(
              DailyHoursExceededException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Límite diario de horas superado")),
          Map.entry(
              FutureDateException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Fecha de trabajo futura")),
          Map.entry(
              MatterClosedException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "El asunto está cerrado")),
          Map.entry(
              EditWindowExpiredException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Ventana de edición vencida")),
          Map.entry(
              EntryNotPendingException.class,
              new Entry(HttpStatus.CONFLICT, "El registro ya no está pendiente")),
          Map.entry(
              ConcurrentModificationException.class,
              new Entry(HttpStatus.CONFLICT, "El registro cambió")),
          Map.entry(
              NotEntryOwnerException.class,
              new Entry(HttpStatus.FORBIDDEN, "El registro no te pertenece")),
          Map.entry(
              RejectionReasonRequiredException.class,
              new Entry(HttpStatus.UNPROCESSABLE_ENTITY, "Motivo de rechazo obligatorio")),
          // add-hours-dashboard (design D2): validación de la ventana con errors[] por campo.
          Map.entry(
              DomainValidationException.class,
              new Entry(HttpStatus.BAD_REQUEST, "Solicitud inválida")),
          // add-description-assistant (design D8)
          Map.entry(
              FeatureDisabledException.class,
              new Entry(HttpStatus.NOT_FOUND, "Funcionalidad no disponible")),
          Map.entry(
              AssistantUnavailableException.class,
              new Entry(HttpStatus.SERVICE_UNAVAILABLE, "Asistente no disponible")));

  /** Entrada exacta o, en su defecto, la de la superclase más cercana. */
  public Optional<Entry> find(Class<?> exceptionType) {
    Class<?> current = exceptionType;
    while (current != null && DomainException.class.isAssignableFrom(current)) {
      Entry entry = ENTRIES.get(current);
      if (entry != null) {
        return Optional.of(entry);
      }
      current = current.getSuperclass();
    }
    return Optional.empty();
  }

  /** Indica si el tipo tiene una entrada propia (sin heredar). */
  public boolean isMapped(Class<? extends DomainException> exceptionType) {
    return ENTRIES.containsKey(exceptionType);
  }
}
