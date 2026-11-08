package com.emba.asuntoshoras.application.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reemplaza correos por {@value #EMAIL_MARKER} y teléfonos por {@value #PHONE_MARKER} (design D12).
 * Clase pura, sin Spring: el servicio la aplica a la nota antes del proveedor y a su salida.
 *
 * <ul>
 *   <li>Correo: {@code [\w.+-]+@[\w-]+(\.[\w-]+)+} (con clases Unicode).
 *   <li>Teléfono: paréntesis y {@code +} opcionales y 7 o más dígitos, con separadores {@code
 *       [\s.\-–—()]*} entre dígitos (p. ej. {@code +591 000 00000}, {@code (591) 700-00000}, {@code
 *       700.000.00}). Se conservan las fechas {@code AAAA-MM-DD} y {@code DD/MM/AAAA}, los montos
 *       con miles {@code 1.500.000} y los números con un único punto y un grupo largo ({@code
 *       1.2345678}). Todas las regex son lineales (sin cuantificadores anidados).
 * </ul>
 */
public final class PiiRedactor {

  public static final String EMAIL_MARKER = "[correo]";
  public static final String PHONE_MARKER = "[teléfono]";

  private static final Pattern EMAIL =
      Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+", Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * Grupo 1: fecha a conservar (se prueba primero en la misma posición). Grupo 2: teléfono. Los
   * bordes excluyen dígitos, letras, el punto (montos con separador de miles) y el guion previo
   * (códigos como {@code AS-2026-0007}).
   */
  private static final Pattern DATE_OR_PHONE =
      Pattern.compile(
          "(?<![\\w.])((?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4})(?![\\w/-])"
              + "|[0-9]{1,3}(?:\\.[0-9]{3})+(?![\\w]|\\.[0-9]))"
              + "|((?<![\\w.+\\-–—])\\(?\\+?[0-9](?:[\\s.\\-–—()]*[0-9]){6,}(?![\\w]|\\.[0-9]))");

  /**
   * Número formado solo por dígitos y puntos con un grupo largo (versión, decimal): se conserva.
   */
  private static final Pattern DOTTED_NUMBER = Pattern.compile("[0-9.]*[0-9]{5,}[0-9.]*");

  /**
   * Texto con correos y teléfonos reemplazados; {@code null} queda {@code null}. Debe invocarse
   * sobre texto con los espacios ya normalizados, aunque también tolera espacios, tabuladores y
   * saltos de línea entre grupos de dígitos.
   */
  public String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String withoutEmails = EMAIL.matcher(text).replaceAll(Matcher.quoteReplacement(EMAIL_MARKER));
    Matcher matcher = DATE_OR_PHONE.matcher(withoutEmails);
    StringBuilder out = new StringBuilder(withoutEmails.length());
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement(matcher)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String replacement(Matcher matcher) {
    if (matcher.group(1) != null) {
      return matcher.group(1);
    }
    String candidate = matcher.group(2);
    if (candidate.indexOf('.') >= 0
        && hasSingleDot(candidate)
        && DOTTED_NUMBER.matcher(candidate).matches()) {
      return candidate;
    }
    if (candidate.startsWith("(") && candidate.indexOf(')') < 0) {
      return "(" + PHONE_MARKER;
    }
    return PHONE_MARKER;
  }

  private static boolean hasSingleDot(String candidate) {
    return candidate.indexOf('.') == candidate.lastIndexOf('.');
  }
}
