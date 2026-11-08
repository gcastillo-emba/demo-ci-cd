package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;

/** Página solicitada: {@code page} base 0 y {@code size} entre 1 y {@value #MAX_SIZE}. */
public record PageQuery(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public PageQuery {
    if (page < 0) {
      throw new InvalidValueException("page", "La página no puede ser negativa.");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new InvalidValueException("size", "El tamaño de página debe estar entre 1 y 100.");
    }
  }

  /** Página con valores por defecto para los parámetros ausentes. */
  public static PageQuery of(Integer page, Integer size) {
    return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
  }
}
