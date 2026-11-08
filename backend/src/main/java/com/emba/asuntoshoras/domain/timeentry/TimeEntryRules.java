package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.matter.Matter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Reglas del alta y la edición que dependen del asunto y de "hoy" en la zona de la firma. */
public final class TimeEntryRules {

  private TimeEntryRules() {}

  /** Fecha de hoy en la zona de la firma para un instante (design D2). */
  public static LocalDate today(Instant now, ZoneId firmZone) {
    return now.atZone(firmZone).toLocalDate();
  }

  /**
   * @throws MatterClosedException si el asunto está cerrado
   */
  public static void ensureMatterOpen(Matter matter) {
    if (matter.isClosed()) {
      throw new MatterClosedException();
    }
  }

  /**
   * @throws FutureDateException si {@code workDate} es posterior a {@code today}
   */
  public static void ensureNotFuture(LocalDate workDate, LocalDate today) {
    if (workDate.isAfter(today)) {
      throw new FutureDateException();
    }
  }
}
