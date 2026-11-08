package com.emba.asuntoshoras.domain.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.DomainValidationException;
import com.emba.asuntoshoras.domain.shared.DomainValidationException.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Requisito "Validación de la nota de entrada" (recorte, vacía, 500 code points). */
class AssistantNoteTest {

  @Test
  void trimsBeforeValidating() {
    AssistantNote note = AssistantNote.of("  \t reunion cliente \n ");

    assertThat(note.value()).isEqualTo("reunion cliente");
    assertThat(note.length()).isEqualTo(15);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "    ", "\t\n  "})
  void absentEmptyOrBlankIsAssistantNoteEmpty(String raw) {
    assertThatThrownBy(() -> AssistantNote.of(raw))
        .isInstanceOfSatisfying(
            DomainValidationException.class,
            e -> {
              assertThat(e.code()).isEqualTo("validation-error");
              assertThat(e.violations())
                  .containsExactly(
                      new Violation(
                          "note",
                          "Escribe una nota breve antes de pedir una sugerencia.",
                          "assistant-note-empty"));
            });
  }

  @Test
  void fiveHundredCodePointsAfterTrimIsValid() {
    String raw = "   " + "a".repeat(500) + "   ";

    assertThat(AssistantNote.of(raw).length()).isEqualTo(500);
  }

  @Test
  void fiveHundredAndOneIsAssistantNoteTooLong() {
    assertThatThrownBy(() -> AssistantNote.of("a".repeat(501)))
        .isInstanceOfSatisfying(
            DomainValidationException.class,
            e ->
                assertThat(e.violations())
                    .singleElement()
                    .satisfies(
                        v -> {
                          assertThat(v.field()).isEqualTo("note");
                          assertThat(v.code()).isEqualTo("assistant-note-too-long");
                          assertThat(v.message())
                              .isEqualTo("La nota admite como máximo 500 caracteres.");
                        }));
  }

  @Test
  void lengthIsMeasuredInCodePoints() {
    // 500 emojis = 1000 unidades UTF-16 pero 500 code points (Supuesto 6).
    String emojis = "📝".repeat(500);

    assertThat(AssistantNote.of(emojis).length()).isEqualTo(500);
    assertThatThrownBy(() -> AssistantNote.of(emojis + "x"))
        .isInstanceOf(DomainValidationException.class);
    assertThat(AssistantNote.codePoints(null)).isZero();
  }

  @Test
  void constructorRejectsUntrimmedValue() {
    assertThatThrownBy(() -> new AssistantNote(" x ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void messagesDoNotEchoTheNote() {
    String note = "ana.perez@example.com " + "z".repeat(600);

    assertThatThrownBy(() -> AssistantNote.of(note))
        .isInstanceOfSatisfying(
            DomainValidationException.class,
            e -> {
              assertThat(e.getMessage()).doesNotContain("ana.perez");
              assertThat(e.violations().get(0).message()).doesNotContain("ana.perez");
            });
  }

  @Test
  void toStringNeverContainsTheText() {
    assertThat(AssistantNote.of("llamada con ana.perez@example.com").toString())
        .doesNotContain("ana.perez")
        .contains("length=33");
  }
}
