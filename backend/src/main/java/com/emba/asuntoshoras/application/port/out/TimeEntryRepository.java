package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryFilter;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases.TimeEntryOrder;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: persistencia de registros de horas. */
public interface TimeEntryRepository {

  void insert(TimeEntry entry);

  /**
   * Lee el registro bloqueando su fila hasta el fin de la transacción ({@code SELECT … FOR
   * UPDATE}): serializa aprobaciones, rechazos y ediciones del mismo registro (design D4).
   */
  Optional<TimeEntry> findByIdForUpdate(UUID id);

  /** Registro con el código de su asunto y el nombre de su abogado. */
  Optional<TimeEntryListing> findListing(UUID id);

  /**
   * Persiste los cambios de un registro solo si su versión sigue siendo {@link
   * TimeEntry#version()}; la persistencia incrementa la versión.
   *
   * @return {@code false} si otro proceso lo modificó entre la lectura y la escritura
   */
  boolean update(TimeEntry entry);

  /**
   * Suma las horas {@code PENDING} + {@code APPROVED} del abogado en un día, excluido opcionalmente
   * un registro (el que se edita).
   *
   * @param excludedEntryId registro a excluir o {@code null}
   */
  Hours sumCountedHours(UUID lawyerId, LocalDate workDate, UUID excludedEntryId);

  /** Busca registros con los filtros (ya con el alcance por rol aplicado) y el orden pedidos. */
  PageResult<TimeEntryListing> search(TimeEntryFilter filter, TimeEntryOrder order, PageQuery page);

  /** Registro con los datos de presentación de su asunto y su abogado. */
  record TimeEntryListing(TimeEntry entry, String matterCode, String lawyerName) {

    public TimeEntryListing {
      Objects.requireNonNull(entry, "entry");
      Objects.requireNonNull(matterCode, "matterCode");
      Objects.requireNonNull(lawyerName, "lawyerName");
    }
  }
}
