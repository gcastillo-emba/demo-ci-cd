package com.emba.asuntoshoras.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientTest {

  private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Instant CREATED = Instant.parse("2026-11-07T14:00:00Z");

  // Scenario: Alta de cliente con identificación tributaria
  @Test
  void createsClientTrimmingNameAndTaxId() {
    Client client = Client.create(ID, "  Inversiones Andinas S.A. ", " 0000000001 ", CREATED);

    assertThat(client.id()).isEqualTo(ID);
    assertThat(client.name()).isEqualTo("Inversiones Andinas S.A.");
    assertThat(client.taxId()).isEqualTo("0000000001");
    assertThat(client.createdAt()).isEqualTo(CREATED);
  }

  // Scenario: Alta de cliente sin identificación tributaria
  @Test
  void taxIdIsOptional() {
    assertThat(Client.create(ID, "Comercial Illimani", null, CREATED).taxId()).isNull();
  }

  // Scenario: Nombre ausente
  @Test
  void nameIsRequired() {
    assertThatThrownBy(() -> Client.create(ID, null, null, CREATED))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("name");
    assertThatThrownBy(() -> Client.create(ID, "   ", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
  }

  // Scenario: Nombre de más de 200 caracteres / Nombre de exactamente 200 caracteres
  @Test
  void nameIsLimitedTo200CharactersAfterTrim() {
    assertThatThrownBy(() -> Client.create(ID, "n".repeat(201), null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThat(Client.create(ID, "  " + "n".repeat(200) + "  ", null, CREATED).name()).hasSize(200);
  }

  // Scenario: taxId de más de 50 caracteres / taxId vacío
  @Test
  void informedTaxIdMustBeNonBlankAndUpTo50() {
    assertThatThrownBy(() -> Client.create(ID, "N", "t".repeat(51), CREATED))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("taxId");
    assertThatThrownBy(() -> Client.create(ID, "N", "   ", CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThat(Client.create(ID, "N", "t".repeat(50), CREATED).taxId()).hasSize(50);
  }

  @Test
  void idAndCreatedAtAreRequired() {
    assertThatThrownBy(() -> Client.create(null, "N", null, CREATED))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(() -> Client.restore(ID, "N", null, null))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void equalityIsByIdAndToStringHasNoPersonalData() {
    Client a = Client.create(ID, "Inversiones Andinas S.A.", "0000000001", CREATED);
    Client b = Client.restore(ID, "Otro nombre", null, CREATED);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isEqualTo(a);
    assertThat(a).isNotEqualTo("otro tipo");
    assertThat(a.toString()).contains(ID.toString()).doesNotContain("0000000001");
  }

  @Test
  void exceptionsExposeStableCodes() {
    assertThat(new ClientNotFoundException(ID).code()).isEqualTo("not-found");
    assertThat(new ClientNotFoundException(ID).clientId()).isEqualTo(ID);
    assertThat(new ClientTaxIdTakenException().code()).isEqualTo("client-tax-id-taken");
  }
}
