package com.emba.asuntoshoras.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.assistant.AssistantUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Requisito "Límites de la sugerencia" (design D7): 0, 1, 300, 301, 450 y sin espacios. */
class SuggestionPolicyTest {

  private final SuggestionPolicy policy = new SuggestionPolicy();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\n\t \r\n", "  "})
  void emptyOrBlankIsUnavailable(String raw) {
    assertThatThrownBy(() -> policy.apply(raw)).isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void singleCharacterIsKept() {
    assertThat(policy.apply(" a ")).isEqualTo("a");
  }

  @Test
  void collapsesWhitespaceAndLineBreaksIntoOneSpace() {
    assertThat(policy.apply("  Revisión\n\ndel   contrato\r\n\tde arrendamiento  "))
        .isEqualTo("Revisión del contrato de arrendamiento");
  }

  @Test
  void exactlyThreeHundredIsKeptWhole() {
    String text = words(300);

    assertThat(text).hasSize(300);
    assertThat(policy.apply(text)).isEqualTo(text);
  }

  @Test
  void threeHundredWithFollowingSpaceKeepsTheFullPrefix() {
    String text = words(300) + " fin";

    assertThat(policy.apply(text)).isEqualTo(words(300));
  }

  @Test
  void threeHundredAndOneCutsAtTheLastWordBoundary() {
    String text = words(301);

    String result = policy.apply(text);

    assertThat(result.length()).isLessThanOrEqualTo(300);
    assertThat(text).startsWith(result);
    assertThat(text.charAt(result.length())).isEqualTo(' ');
  }

  // Scenario: Proveedor devuelve un texto largo
  @Test
  void fourHundredFiftyCutsAtTheLastWordBoundary() {
    String text = words(450);

    String result = policy.apply(text);

    assertThat(result.codePointCount(0, result.length())).isLessThanOrEqualTo(300);
    assertThat(text).startsWith(result);
    assertThat(text.charAt(result.length())).isEqualTo(' ');
    assertThat(result).doesNotEndWith(" ");
  }

  // Scenario: Proveedor devuelve un texto largo sin espacios
  @Test
  void fourHundredFiftyWithoutSpacesCutsExactlyAtThreeHundred() {
    String text = "x".repeat(450);

    assertThat(policy.apply(text)).isEqualTo("x".repeat(300));
  }

  @Test
  void countsCodePointsNotUtf16Units() {
    String emojis = "📝".repeat(301);

    String result = policy.apply(emojis);

    assertThat(result.codePointCount(0, result.length())).isEqualTo(300);
    assertThat(result).isEqualTo("📝".repeat(300));
  }

  /** Palabras "abcd" separadas por espacio hasta exactamente {@code length} caracteres. */
  private static String words(int length) {
    StringBuilder text = new StringBuilder();
    while (text.length() < length) {
      text.append(text.length() % 5 == 4 ? ' ' : (char) ('a' + text.length() % 5));
    }
    String result = text.toString();
    if (result.endsWith(" ")) {
      result = result.substring(0, result.length() - 1) + "z";
    }
    return result;
  }
}
