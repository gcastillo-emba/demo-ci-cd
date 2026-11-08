package com.emba.asuntoshoras.application.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reemplaza correos por {@value #EMAIL_MARKER} y teléfonos por {@value #PHONE_MARKER} (design D12).
 * Clase pura, sin Spring: el servicio la aplica a la nota antes del proveedor y a su salida.
 *
 * <ul>
 *   <li>Correo: {@code [\w.+-]+@[\w-]+(\.[\w-]+)+} (con clases Unicode).
 *   <li>Teléfono: {@code +} opcional y 7 o más dígitos, con separadores opcionales de un espacio o
 *       un guion entre dígitos (p. ej. {@code +591 000 00000}, {@code 555-0100-00}). El punto no es
 *       separador, para no confundir montos como {@code 1.500.000}; las fechas {@code AAAA-MM-DD} y
 *       {@code DD/MM/AAAA} se conservan.
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
          "(?<![\\w.])((?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4})(?![\\w/-]))"
              + "|((?<![\\w.+-])\\+?[0-9](?:[ -]?[0-9]){6,}(?![\\w]|\\.[0-9]))");

  /** Texto con correos y teléfonos reemplazados; {@code null} queda {@code null}. */
  public String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String withoutEmails = EMAIL.matcher(text).replaceAll(Matcher.quoteReplacement(EMAIL_MARKER));
    Matcher matcher = DATE_OR_PHONE.matcher(withoutEmails);
    StringBuilder out = new StringBuilder(withoutEmails.length());
    while (matcher.find()) {
      String replacement = matcher.group(1) != null ? matcher.group(1) : PHONE_MARKER;
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
