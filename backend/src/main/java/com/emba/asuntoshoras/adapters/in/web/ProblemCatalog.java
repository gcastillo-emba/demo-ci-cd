package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import com.emba.asuntoshoras.domain.matter.MatterAlreadyClosedException;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.DomainException;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
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
              InvalidValueException.class, new Entry(HttpStatus.BAD_REQUEST, "Valor inválido")),
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
              new Entry(HttpStatus.CONFLICT, "El asunto ya está cerrado")));

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
