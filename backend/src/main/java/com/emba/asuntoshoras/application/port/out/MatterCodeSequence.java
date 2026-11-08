package com.emba.asuntoshoras.application.port.out;

/**
 * Puerto de salida: correlativo anual de códigos de asunto. Debe ser atómico bajo concurrencia y
 * participar de la transacción del alta (design D3).
 */
public interface MatterCodeSequence {

  /** Reserva y devuelve el siguiente correlativo del año (el primero es 1). */
  int next(int year);
}
