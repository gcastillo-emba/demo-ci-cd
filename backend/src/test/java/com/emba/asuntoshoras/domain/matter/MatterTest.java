package com.emba.asuntoshoras.domain.matter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatterTest {

  private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID CLIENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final MatterCode CODE = MatterCode.of(2026, 7);
  private static final Instant CREATED = Instant.parse("2026-11-07T14:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-08T10:00:00Z");

  private static Matter openMatter() {
    return Matter.open(ID, CODE, CLIENT, "  Due diligence adquisición planta  ", null, CREATED);
  }

  // Scenario: Alta de asunto
  @Test
  void opensWithStatusOpenAndNoClosedAt() {
    Matter matter = openMatter();

    assertThat(matter.status()).isEqualTo(MatterStatus.OPEN);
    assertThat(matter.isClosed()).isFalse();
    assertThat(matter.closedAt()).isNull();
    assertThat(matter.title()).isEqualTo("Due diligence adquisición planta");
    assertThat(matter.description()).isNull();
    assertThat(matter.code()).isEqualTo(CODE);
    assertThat(matter.clientId()).isEqualTo(CLIENT);
    assertThat(matter.createdAt()).isEqualTo(CREATED);
    assertThat(matter.version()).isZero();
    assertThat(matter.id()).isEqualTo(ID);
  }

  // Scenario: Cierre exitoso
  @Test
  void closeMovesOpenToClosedWithClosedAt() {
    Matter matter = openMatter();

    matter.close(T1);

    assertThat(matter.status()).isEqualTo(MatterStatus.CLOSED);
    assertThat(matter.isClosed()).isTrue();
    assertThat(matter.closedAt()).isEqualTo(T1);
  }

  // Scenario: Cerrar dos veces
  @Test
  void closingAClosedMatterFailsAndKeepsClosedAt() {
    Matter matter = openMatter();
    matter.close(T1);

    assertThatThrownBy(() -> matter.close(T1.plusSeconds(60)))
        .isInstanceOf(MatterAlreadyClosedException.class)
        .extracting(e -> ((MatterAlreadyClosedException) e).code())
        .isEqualTo("matter-already-closed");
    assertThat(matter.closedAt()).isEqualTo(T1);
  }

  @Test
  void closeRequiresAnInstant() {
    assertThatThrownBy(() -> openMatter().close(null)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void titleIsRequiredAndLimitedTo200AfterTrim() {
    assertThatThrownBy(() -> Matter.open(ID, CODE, CLIENT, "   ", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Matter.open(ID, CODE, CLIENT, "x".repeat(201), null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThat(Matter.open(ID, CODE, CLIENT, " " + "x".repeat(200) + " ", null, CREATED).title())
        .hasSize(200);
  }

  @Test
  void descriptionIsLimitedTo2000AndBlankBecomesNull() {
    assertThatThrownBy(() -> Matter.open(ID, CODE, CLIENT, "T", "d".repeat(2001), CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThat(Matter.open(ID, CODE, CLIENT, "T", "d".repeat(2000), CREATED).description())
        .hasSize(2000);
    assertThat(Matter.open(ID, CODE, CLIENT, "T", "  ", CREATED).description()).isNull();
  }

  @Test
  void requiredFieldsAreValidated() {
    assertThatThrownBy(() -> Matter.open(null, CODE, CLIENT, "T", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Matter.open(ID, null, CLIENT, "T", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Matter.open(ID, CODE, null, "T", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Matter.open(ID, CODE, CLIENT, "T", null, null))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Matter.restore(ID, CODE, CLIENT, "T", null, null, CREATED, null, 0))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void closedAtMustMatchStatus() {
    assertThatThrownBy(
            () ->
                Matter.restore(ID, CODE, CLIENT, "T", null, MatterStatus.CLOSED, CREATED, null, 1))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> Matter.restore(ID, CODE, CLIENT, "T", null, MatterStatus.OPEN, CREATED, T1, 1))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void restoresClosedMatterWithVersion() {
    Matter matter =
        Matter.restore(ID, CODE, CLIENT, "T", "Desc", MatterStatus.CLOSED, CREATED, T1, 3);

    assertThat(matter.isClosed()).isTrue();
    assertThat(matter.version()).isEqualTo(3);
    assertThat(matter.description()).isEqualTo("Desc");
  }

  @Test
  void equalityIsById() {
    Matter a = openMatter();
    Matter b = Matter.restore(ID, CODE, CLIENT, "Otro", null, MatterStatus.OPEN, CREATED, null, 5);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isEqualTo(a);
    assertThat(a).isNotEqualTo("otro tipo");
    assertThat(a.toString()).contains(ID.toString(), "AS-2026-0007", "OPEN");
  }
}
