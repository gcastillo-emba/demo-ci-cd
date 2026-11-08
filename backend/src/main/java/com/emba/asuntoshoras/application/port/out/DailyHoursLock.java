package com.emba.asuntoshoras.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/**
 * Puerto de salida: serializa altas y ediciones del mismo abogado y día (design D3). El bloqueo
 * dura hasta el fin de la transacción en curso; con varias fechas se toman en orden ascendente para
 * evitar interbloqueos.
 */
public interface DailyHoursLock {

  void lock(UUID lawyerId, Collection<LocalDate> workDates);
}
