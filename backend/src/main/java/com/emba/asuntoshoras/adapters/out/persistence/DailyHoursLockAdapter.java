package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.out.DailyHoursLock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo por abogado y día con {@code pg_advisory_xact_lock} (design D3). El bloqueo se libera al
 * terminar la transacción del caso de uso (JpaTransactionManager comparte la conexión con
 * JdbcTemplate). Las fechas se bloquean sin duplicados y en orden ascendente.
 */
@Component
public class DailyHoursLockAdapter implements DailyHoursLock {

  private static final String LOCK = "SELECT pg_advisory_xact_lock(hashtext(?))";

  private final JdbcTemplate jdbc;

  public DailyHoursLockAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(UUID lawyerId, Collection<LocalDate> workDates) {
    for (LocalDate workDate : new TreeSet<>(workDates)) {
      String key = "time-entry:" + lawyerId + ":" + workDate;
      jdbc.query(LOCK, ps -> ps.setString(1, key), rs -> null);
    }
  }
}
