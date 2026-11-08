package com.emba.asuntoshoras.domain.timeentry;

/**
 * Estado del registro de horas: {@code PENDING} (Pendiente), {@code APPROVED} (Aprobado, terminal)
 * o {@code REJECTED} (Rechazado, terminal).
 */
public enum TimeEntryStatus {
  PENDING,
  APPROVED,
  REJECTED;

  /** Indica si las horas cuentan para el límite diario ({@code PENDING} y {@code APPROVED}). */
  public boolean countsTowardsDailyLimit() {
    return this != REJECTED;
  }
}
