package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La suma diaria de horas {@code PENDING} + {@code APPROVED} del abogado superaría 24. Expone
 * {@code limitHours} y {@code alreadyLoggedHours} como miembros de extensión del Problem Details.
 * El mensaje solo usa valores calculados por el servidor, nunca texto del usuario.
 */
public class DailyHoursExceededException extends DomainException {

  public static final String CODE = "daily-hours-exceeded";

  private final Hours limit;
  private final Hours alreadyLogged;

  public DailyHoursExceededException(Hours limit, Hours alreadyLogged) {
    super(CODE, message(limit, alreadyLogged));
    this.limit = limit;
    this.alreadyLogged = alreadyLogged;
  }

  private static String message(Hours limit, Hours alreadyLogged) {
    int remaining = Math.max(0, limit.tenths() - alreadyLogged.tenths());
    return "Ya tienes "
        + format(alreadyLogged)
        + " h registradas ese día; puedes registrar como máximo "
        + format(new Hours(remaining))
        + " h más.";
  }

  /** Formato en español: coma decimal ({@code 3,5}). */
  private static String format(Hours hours) {
    return hours.toBigDecimal().toPlainString().replace('.', ',');
  }

  public Hours limit() {
    return limit;
  }

  public Hours alreadyLogged() {
    return alreadyLogged;
  }

  @Override
  public Map<String, Object> properties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("limitHours", limit.toBigDecimal());
    properties.put("alreadyLoggedHours", alreadyLogged.toBigDecimal());
    return properties;
  }
}
