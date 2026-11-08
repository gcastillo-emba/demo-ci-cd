package com.emba.asuntoshoras.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Design D12: correos y teléfonos → marcadores; fechas y montos intactos. */
class PiiRedactorTest {

  private final PiiRedactor redactor = new PiiRedactor();

  @Test
  void redactsEmailAndPhoneOfTheSpecExample() {
    assertThat(redactor.redact("llamada con ana.perez@example.com al +591 000 00000"))
        .isEqualTo("llamada con [correo] al [teléfono]");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "escribir a juan_perez+caso@estudio-ficticio.example.bo hoy|escribir a [correo] hoy",
        "correo JOSÉ.ñandú@dominio.example|correo [correo]",
        "dos: a@b.example, c.d@e.example.org.|dos: [correo], [correo]."
      })
  void redactsEmails(String input, String expected) {
    assertThat(redactor.redact(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "llamar al 70012345|llamar al [teléfono]",
        "llamar al 555-0100-00 mañana|llamar al [teléfono] mañana",
        "fijo 2 2441234|fijo [teléfono]",
        "tel +59170012345.|tel [teléfono].",
        "(+591 7 000 0000)|([teléfono])",
        "1234567|[teléfono]"
      })
  void redactsPhonesWithAndWithoutSeparators(String input, String expected) {
    assertThat(redactor.redact(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "audiencia el 2026-11-07 a las 10",
        "plazo hasta 07/11/2026",
        "honorarios de Bs 1.500.000 y 2.500",
        "asunto AS-2026-0007",
        "8 horas en 3 reuniones",
        "art. 123456 del código",
        "código ABC1234567",
        "versión 1.2345678"
      })
  void keepsDatesAmountsAndShortNumbers(String input) {
    assertThat(redactor.redact(input)).isEqualTo(input);
  }

  @Test
  void dateNextToAPhoneKeepsTheDateAndRedactsThePhone() {
    assertThat(redactor.redact("2026-11-07 llamar al 70012345"))
        .isEqualTo("2026-11-07 llamar al [teléfono]");
  }

  @Test
  void nullAndEmptyStayAsIs() {
    assertThat(redactor.redact(null)).isNull();
    assertThat(redactor.redact("")).isEmpty();
  }

  @Test
  void markersContainNoDigitsAndAreIdempotent() {
    String once = redactor.redact("a@b.example 70012345");

    assertThat(redactor.redact(once)).isEqualTo(once).isEqualTo("[correo] [teléfono]");
  }
}
