package com.emba.asuntoshoras.adapters.out.persistence;

import java.util.Locale;

/** Patrón {@code LIKE} de coincidencia parcial, en minúsculas y con {@code % _ \} escapados. */
final class LikePattern {

  static final char ESCAPE = '\\';

  private LikePattern() {}

  /**
   * @return el patrón {@code %texto%} o {@code null} si el texto es nulo o está en blanco
   */
  static String contains(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lower = text.trim().toLowerCase(Locale.ROOT);
    StringBuilder pattern = new StringBuilder(lower.length() + 2).append('%');
    for (char c : lower.toCharArray()) {
      if (c == '%' || c == '_' || c == ESCAPE) {
        pattern.append(ESCAPE);
      }
      pattern.append(c);
    }
    return pattern.append('%').toString();
  }
}
