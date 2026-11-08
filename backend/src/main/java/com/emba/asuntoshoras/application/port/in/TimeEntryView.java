package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.timeentry.TimeEntry;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Registro de horas tal como lo exponen los casos de uso: con el código del asunto, el nombre del
 * abogado y {@code editableUntil} calculado en la zona de la firma.
 */
public record TimeEntryView(
    TimeEntry entry, String matterCode, String lawyerName, LocalDate editableUntil) {

  public TimeEntryView {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(matterCode, "matterCode");
    Objects.requireNonNull(lawyerName, "lawyerName");
    Objects.requireNonNull(editableUntil, "editableUntil");
  }
}
