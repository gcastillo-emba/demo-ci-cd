package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.out.MatterCodeSequence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correlativo anual con un upsert atómico sobre {@code matter_code_seq} (design D3): el bloqueo de
 * fila del {@code ON CONFLICT DO UPDATE} serializa las altas concurrentes del mismo año. Corre en
 * la transacción del alta (JpaTransactionManager comparte la conexión con JdbcTemplate).
 */
@Component
public class MatterCodeSequenceAdapter implements MatterCodeSequence {

  private static final String NEXT =
      "INSERT INTO matter_code_seq (year, last_value) VALUES (?, 1)"
          + " ON CONFLICT (year) DO UPDATE SET last_value = matter_code_seq.last_value + 1"
          + " RETURNING last_value";

  private final JdbcTemplate jdbc;

  public MatterCodeSequenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public int next(int year) {
    Integer value = jdbc.queryForObject(NEXT, Integer.class, year);
    if (value == null) {
      throw new IllegalStateException("El correlativo de asuntos no devolvió valor");
    }
    return value;
  }
}
