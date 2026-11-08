package com.emba.asuntoshoras.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.InMemoryClientRepository;
import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.ClientUseCases.NewClient;
import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientServiceTest {

  static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
  static final Instant NOW = Instant.parse("2026-11-07T14:00:00.123456789Z");
  static final CurrentUser PARTNER =
      new CurrentUser(UUID.fromString("00000000-0000-4000-8000-000000000001"), Role.PARTNER);
  static final CurrentUser LAWYER =
      new CurrentUser(UUID.fromString("00000000-0000-4000-8000-000000000002"), Role.LAWYER);

  private final InMemoryClientRepository repository = new InMemoryClientRepository();
  private final ClientService service = new ClientService(repository, Clock.fixed(NOW, LA_PAZ));

  private Client existing(String name, String taxId) {
    Client client = Client.create(UUID.randomUUID(), name, taxId, NOW);
    repository.with(client);
    return client;
  }

  // Scenario: Alta de cliente con identificación tributaria
  @Test
  void partnerCreatesClientWithTaxId() {
    Client created =
        service.createClient(PARTNER, new NewClient("Inversiones Andinas S.A.", "0000000001"));

    assertThat(created.id()).isNotNull();
    assertThat(created.name()).isEqualTo("Inversiones Andinas S.A.");
    assertThat(created.taxId()).isEqualTo("0000000001");
    assertThat(created.createdAt()).isEqualTo(Instant.parse("2026-11-07T14:00:00.123456Z"));
    assertThat(repository.findById(created.id())).contains(created);
  }

  // Scenario: Alta de cliente sin identificación tributaria
  @Test
  void partnerCreatesClientWithoutTaxId() {
    assertThat(service.createClient(PARTNER, new NewClient("Comercial Illimani", null)).taxId())
        .isNull();
  }

  // Scenario: Nombre de exactamente 200 caracteres
  @Test
  void nameOf200CharactersIsAccepted() {
    assertThat(service.createClient(PARTNER, new NewClient("n".repeat(200), null)).name())
        .hasSize(200);
  }

  // Scenario: Nombre de más de 200 caracteres
  @Test
  void nameOver200IsRejectedAndNothingIsCreated() {
    assertThatThrownBy(() -> service.createClient(PARTNER, new NewClient("n".repeat(201), null)))
        .isInstanceOf(InvalidValueException.class);
    assertThat(repository.count()).isZero();
  }

  // Scenario: taxId duplicado
  @Test
  void duplicateTaxIdIsRejected() {
    existing("Inversiones Andinas S.A.", "0000000001");

    assertThatThrownBy(
            () -> service.createClient(PARTNER, new NewClient("Otra S.A.", " 0000000001 ")))
        .isInstanceOf(ClientTaxIdTakenException.class);
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void duplicateTaxIdRaceIsTranslatedByTheRepository() {
    existing("Inversiones Andinas S.A.", "0000000001");
    repository.hidingTaxIdsFromExistsCheck();

    assertThatThrownBy(() -> service.createClient(PARTNER, new NewClient("Otra", "0000000001")))
        .isInstanceOf(ClientTaxIdTakenException.class);
  }

  // Scenario: Varios clientes sin taxId
  @Test
  void severalClientsWithoutTaxIdCoexist() {
    existing("Sin NIT Uno", null);

    service.createClient(PARTNER, new NewClient("Sin NIT Dos", null));

    assertThat(repository.count()).isEqualTo(2);
  }

  // Scenario: Abogado intenta crear un cliente
  @Test
  void lawyerCannotCreateClient() {
    assertThatThrownBy(
            () -> service.createClient(LAWYER, new NewClient("Comercial Illimani", null)))
        .isInstanceOf(ForbiddenException.class)
        .extracting(e -> ((ForbiddenException) e).code())
        .isEqualTo("forbidden");
    assertThat(repository.count()).isZero();
  }

  // Scenario: Abogado envía un cuerpo inválido
  @Test
  void lawyerWithInvalidBodyGetsForbiddenBeforeValidation() {
    assertThatThrownBy(() -> service.createClient(LAWYER, new NewClient(null, null)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void missingUserIsForbidden() {
    assertThatThrownBy(() -> service.createClient(null, new NewClient("X", null)))
        .isInstanceOf(ForbiddenException.class);
  }

  // Scenario: Abogado lista clientes filtrando por nombre
  @Test
  void listsClientsFilteringByName() {
    existing("Inversiones Andinas S.A.", null);
    existing("Andes Logística", null);
    existing("Comercial Illimani", null);

    PageResult<Client> page =
        service.listClients(new ClientFilter("andin"), PageQuery.of(null, null));

    assertThat(page.items()).extracting(Client::name).containsExactly("Inversiones Andinas S.A.");
    assertThat(page.totalItems()).isEqualTo(1);
  }

  // Scenario: Listado sin filtro
  @Test
  void listsAllClientsOrderedByNameWithDefaults() {
    existing("Comercial Illimani", null);
    existing("Andes Logística", null);
    existing("Inversiones Andinas S.A.", null);

    PageResult<Client> page = service.listClients(null, PageQuery.of(null, null));

    assertThat(page.items())
        .extracting(Client::name)
        .containsExactly("Andes Logística", "Comercial Illimani", "Inversiones Andinas S.A.");
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  // Scenario: Página fuera de rango
  @Test
  void pageBeyondTheLastIsEmpty() {
    existing("A", null);
    existing("B", null);
    existing("C", null);

    PageResult<Client> page = service.listClients(new ClientFilter(null), PageQuery.of(5, null));

    assertThat(page.items()).isEmpty();
    assertThat(page.totalItems()).isEqualTo(3);
  }

  // Scenario: Cliente existente
  @Test
  void getsClientById() {
    Client client = existing("Inversiones Andinas S.A.", "0000000001");

    assertThat(service.getClient(client.id())).isEqualTo(client);
  }

  // Scenario: Cliente inexistente
  @Test
  void unknownClientIsNotFound() {
    UUID unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.getClient(unknown))
        .isInstanceOf(ClientNotFoundException.class)
        .extracting(e -> ((ClientNotFoundException) e).code())
        .isEqualTo("not-found");
  }
}
