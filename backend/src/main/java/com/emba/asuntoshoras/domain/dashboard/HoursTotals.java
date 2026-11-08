package com.emba.asuntoshoras.domain.dashboard;

import com.emba.asuntoshoras.domain.timeentry.Hours;
import java.util.Objects;

/** Horas aprobadas y pendientes (sumas en décimas enteras, design D4). */
public record HoursTotals(Hours approved, Hours pending) {

  public static final HoursTotals ZERO = new HoursTotals(Hours.ZERO, Hours.ZERO);

  public HoursTotals {
    Objects.requireNonNull(approved, "approved");
    Objects.requireNonNull(pending, "pending");
  }

  public HoursTotals plus(HoursTotals other) {
    return new HoursTotals(approved.plus(other.approved), pending.plus(other.pending));
  }

  public boolean isZero() {
    return approved.tenths() == 0 && pending.tenths() == 0;
  }
}
