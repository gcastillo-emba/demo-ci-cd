package com.emba.asuntoshoras.domain.timeentry;

/**
 * Límite diario de 24 h por abogado: la suma de sus horas {@code PENDING} + {@code APPROVED} de un
 * día, incluido el registro nuevo o editado, no puede superar 24. Las horas {@code REJECTED}, las
 * de otros abogados y las horas previas del registro editado no forman parte de {@code
 * alreadyLogged} (las excluye la consulta que la calcula).
 */
public final class DailyHoursPolicy {

  public static final Hours DAILY_LIMIT = Hours.ofTenths(Hours.MAX_TENTHS);

  private DailyHoursPolicy() {}

  /**
   * @param alreadyLogged horas ya contabilizadas ese día, sin el registro que se evalúa
   * @param requested horas del registro nuevo o editado
   * @throws DailyHoursExceededException si la suma supera {@link #DAILY_LIMIT}
   */
  public static void check(Hours alreadyLogged, Hours requested) {
    if (alreadyLogged.plus(requested).exceeds(DAILY_LIMIT)) {
      throw new DailyHoursExceededException(DAILY_LIMIT, alreadyLogged);
    }
  }
}
