package com.emba.asuntoshoras.adapters.out.persistence;

import com.emba.asuntoshoras.application.port.out.DashboardReadRepository;
import com.emba.asuntoshoras.domain.dashboard.DashboardScope;
import com.emba.asuntoshoras.domain.dashboard.DateWindow;
import com.emba.asuntoshoras.domain.dashboard.HoursTotals;
import com.emba.asuntoshoras.domain.dashboard.LawyerHours;
import com.emba.asuntoshoras.domain.dashboard.MatterHours;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model del tablero (add-hours-dashboard, design D4): sumas condicionales de {@code
 * hours_tenths} en SQL, agrupadas por asunto y por abogado. Solo cuentan {@code APPROVED} y {@code
 * PENDING}; el alcance llega ya resuelto desde la aplicación. Las consultas son textos constantes
 * (sin concatenar entrada) con parámetros con nombre.
 */
@Component
@Transactional(readOnly = true)
public class DashboardReadAdapter implements DashboardReadRepository {

  private static final String SUMS =
      " COALESCE(SUM(CASE WHEN te.status = 'APPROVED' THEN te.hours_tenths END), 0)"
          + " AS approved_tenths,"
          + " COALESCE(SUM(CASE WHEN te.status = 'PENDING' THEN te.hours_tenths END), 0)"
          + " AS pending_tenths";

  private static final String WINDOW =
      " WHERE te.work_date BETWEEN :from AND :to AND te.status IN ('APPROVED', 'PENDING')";

  private static final String OWN = " AND te.lawyer_id = :lawyerId";

  private static final String MATTER_SELECT =
      "SELECT m.id AS matter_id, m.code AS matter_code, m.title AS matter_title,"
          + SUMS
          + " FROM time_entries te JOIN matters m ON m.id = te.matter_id"
          + WINDOW;

  private static final String MATTER_GROUP =
      " GROUP BY m.id, m.code, m.title ORDER BY m.code ASC, m.id ASC";

  private static final String LAWYER_SELECT =
      "SELECT u.id AS lawyer_id, u.full_name AS lawyer_name,"
          + SUMS
          + " FROM time_entries te JOIN users u ON u.id = te.lawyer_id"
          + WINDOW;

  private static final String LAWYER_GROUP =
      " GROUP BY u.id, u.full_name ORDER BY u.full_name ASC, u.id ASC";

  /** Por asunto, alcance del Socio (visible para el IT de plan de consulta). */
  static final String BY_MATTER_ALL = MATTER_SELECT + MATTER_GROUP;

  /** Por asunto, alcance del Abogado. */
  static final String BY_MATTER_OWN = MATTER_SELECT + OWN + MATTER_GROUP;

  /** Por abogado, alcance del Socio. */
  static final String BY_LAWYER_ALL = LAWYER_SELECT + LAWYER_GROUP;

  /** Por abogado, alcance del Abogado. */
  static final String BY_LAWYER_OWN = LAWYER_SELECT + OWN + LAWYER_GROUP;

  private final NamedParameterJdbcTemplate jdbc;

  public DashboardReadAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Aggregate aggregate(DateWindow window, DashboardScope scope) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("from", Date.valueOf(window.from()))
            .addValue("to", Date.valueOf(window.to()));
    boolean own = scope instanceof DashboardScope.Own;
    if (scope instanceof DashboardScope.Own ownScope) {
      params.addValue("lawyerId", ownScope.lawyerId());
    }
    List<MatterHours> byMatter =
        jdbc.query(own ? BY_MATTER_OWN : BY_MATTER_ALL, params, (rs, n) -> toMatterHours(rs));
    List<LawyerHours> byLawyer =
        jdbc.query(own ? BY_LAWYER_OWN : BY_LAWYER_ALL, params, (rs, n) -> toLawyerHours(rs));
    return new Aggregate(byMatter, byLawyer);
  }

  private static MatterHours toMatterHours(ResultSet rs) throws SQLException {
    return new MatterHours(
        rs.getObject("matter_id", UUID.class),
        rs.getString("matter_code"),
        rs.getString("matter_title"),
        hours(rs));
  }

  private static LawyerHours toLawyerHours(ResultSet rs) throws SQLException {
    return new LawyerHours(
        rs.getObject("lawyer_id", UUID.class), rs.getString("lawyer_name"), hours(rs));
  }

  private static HoursTotals hours(ResultSet rs) throws SQLException {
    return new HoursTotals(
        Hours.sumOfTenths(rs.getLong("approved_tenths")),
        Hours.sumOfTenths(rs.getLong("pending_tenths")));
  }
}
