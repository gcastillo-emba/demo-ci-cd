package com.emba.asuntoshoras.application.service;

import static com.emba.asuntoshoras.application.service.ClientServiceTest.LAWYER;
import static com.emba.asuntoshoras.application.service.ClientServiceTest.LA_PAZ;
import static com.emba.asuntoshoras.application.service.ClientServiceTest.PARTNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emba.asuntoshoras.application.fakes.InMemoryClientRepository;
import com.emba.asuntoshoras.application.fakes.InMemoryMatterCodeSequence;
import com.emba.asuntoshoras.application.fakes.InMemoryMatterRepository;
import com.emba.asuntoshoras.application.port.in.MatterUseCases.MatterFilter;
import com.emba.asuntoshoras.application.port.in.MatterUseCases.NewMatter;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterAlreadyClosedException;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.matter.MatterStatus;
import com.emba.asuntoshoras.domain.shared.ForbiddenException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatterServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-07T14:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");

  private final InMemoryClientRepository clients = new InMemoryClientRepository();
  private final InMemoryMatterRepository matters = new InMemoryMatterRepository(clients);
  private final InMemoryMatterCodeSequence sequence = new InMemoryMatterCodeSequence();
  private final Client client =
      Client.create(UUID.randomUUID(), "Inversiones Andinas S.A.", null, NOW);
  private final Client otherClient = Client.create(UUID.randomUUID(), "Andes Logística", null, NOW);

  MatterServiceTest() {
    clients.with(client).with(otherClient);
  }

  private MatterService serviceAt(Instant instant) {
    return new MatterService(matters, clients, sequence, Clock.fixed(instant, LA_PAZ));
  }

  private final MatterService service = serviceAt(NOW);

  private Matter existing(int sequenceNumber, Client owner, String title, boolean closed) {
    Matter matter =
        Matter.open(
            UUID.randomUUID(), MatterCode.of(2026, sequenceNumber), owner.id(), title, null, NOW);
    if (closed) {
      matter.close(T1);
    }
    matters.with(matter);
    return matter;
  }

  // Scenario: Alta de asunto
  @Test
  void partnerCreatesOpenMatterWithGeneratedCode() {
    MatterView view =
        service.createMatter(
            PARTNER, new NewMatter(client.id(), "Due diligence adquisición planta", null));

    Matter matter = view.matter();
    assertThat(matter.status()).isEqualTo(MatterStatus.OPEN);
    assertThat(matter.closedAt()).isNull();
    assertThat(matter.code().value()).matches("^AS-\\d{4}-\\d{4}$");
    assertThat(matter.createdAt()).isEqualTo(NOW);
    assertThat(view.clientName()).isEqualTo("Inversiones Andinas S.A.");
    assertThat(matters.findById(matter.id())).isPresent();
  }

  // Scenario: Primer asunto del año
  @Test
  void firstMatterOfTheYearGetsSequence0001() {
    assertThat(
            service
                .createMatter(PARTNER, new NewMatter(client.id(), "Primero", null))
                .matter()
                .code()
                .value())
        .isEqualTo("AS-2026-0001");
  }

  // Scenario: Correlativo siguiente
  @Test
  void nextMatterFollowsTheLastSequence() {
    sequence.withLast(2026, 41);

    assertThat(
            service
                .createMatter(PARTNER, new NewMatter(client.id(), "Siguiente", null))
                .matter()
                .code()
                .value())
        .isEqualTo("AS-2026-0042");
  }

  // Scenario: Cambio de año según la zona de la firma
  @Test
  void yearComesFromTheFirmTimeZone() {
    sequence.withLast(2026, 42);
    MatterService newYearsEveInLaPaz = serviceAt(Instant.parse("2027-01-01T02:00:00Z"));

    assertThat(
            newYearsEveInLaPaz
                .createMatter(PARTNER, new NewMatter(client.id(), "Fin de año", null))
                .matter()
                .code()
                .value())
        .isEqualTo("AS-2026-0043");
  }

  // Scenario: Altas concurrentes (la atomicidad real del correlativo la prueba MatterCodeIT)
  @Test
  void twoCreationsGetDistinctCodes() {
    String first =
        service
            .createMatter(PARTNER, new NewMatter(client.id(), "Uno", null))
            .matter()
            .code()
            .value();
    String second =
        service
            .createMatter(PARTNER, new NewMatter(client.id(), "Dos", null))
            .matter()
            .code()
            .value();

    assertThat(first).isEqualTo("AS-2026-0001");
    assertThat(second).isEqualTo("AS-2026-0002");
  }

  // Scenario: Campos de solo lectura ignorados
  @Test
  void commandCannotFixCodeStatusOrClosedAt() {
    MatterView view = service.createMatter(PARTNER, new NewMatter(client.id(), "Título", null));

    assertThat(view.matter().status()).isEqualTo(MatterStatus.OPEN);
    assertThat(view.matter().code().value()).isNotEqualTo("AS-1999-0001");
  }

  // Scenario: Cliente del asunto inexistente
  @Test
  void unknownClientIsNotFoundAndNothingIsCreated() {
    assertThatThrownBy(
            () -> service.createMatter(PARTNER, new NewMatter(UUID.randomUUID(), "Título", null)))
        .isInstanceOf(ClientNotFoundException.class);
    assertThat(matters.count()).isZero();
  }

  // Scenario: Cuerpo inválido con cliente inexistente
  @Test
  void invalidTitleIsReportedBeforeUnknownClient() {
    assertThatThrownBy(
            () -> service.createMatter(PARTNER, new NewMatter(UUID.randomUUID(), null, null)))
        .isInstanceOf(InvalidValueException.class)
        .extracting(e -> ((InvalidValueException) e).field())
        .isEqualTo("title");
  }

  // Scenario: Título ausente y clientId ausente
  @Test
  void missingClientIdIsInvalid() {
    assertThatThrownBy(() -> service.createMatter(PARTNER, new NewMatter(null, null, null)))
        .isInstanceOf(InvalidValueException.class);
  }

  // Scenario: Descripción de más de 2000 caracteres / Título de más de 200 caracteres
  @Test
  void lengthLimitsAreValidated() {
    assertThatThrownBy(
            () ->
                service.createMatter(
                    PARTNER, new NewMatter(client.id(), "Título", "d".repeat(2001))))
        .isInstanceOf(InvalidValueException.class);
    assertThatThrownBy(
            () -> service.createMatter(PARTNER, new NewMatter(client.id(), "t".repeat(201), null)))
        .isInstanceOf(InvalidValueException.class);
    assertThat(matters.count()).isZero();
  }

  // Scenario: Abogado intenta crear un asunto
  @Test
  void lawyerCannotCreateMatter() {
    assertThatThrownBy(
            () -> service.createMatter(LAWYER, new NewMatter(client.id(), "Título", null)))
        .isInstanceOf(ForbiddenException.class);
    assertThat(matters.count()).isZero();
  }

  // Scenario: Filtrar por cliente y estado
  @Test
  void filtersByClientAndStatus() {
    Matter open = existing(1, client, "Abierto de C", false);
    existing(2, client, "Cerrado de C", true);
    existing(3, otherClient, "Abierto de otro", false);

    PageResult<MatterView> page =
        service.listMatters(
            new MatterFilter(null, client.id(), MatterStatus.OPEN), PageQuery.of(null, null));

    assertThat(page.items()).extracting(v -> v.matter().id()).containsExactly(open.id());
  }

  // Scenario: Buscar por código
  @Test
  void searchesByCode() {
    existing(7, client, "Siete", false);
    Matter twelve = existing(12, client, "Doce", false);

    PageResult<MatterView> page =
        service.listMatters(new MatterFilter("0012", null, null), PageQuery.of(null, null));

    assertThat(page.items()).extracting(v -> v.matter().id()).containsExactly(twelve.id());
  }

  // Scenario: Buscar por título
  @Test
  void searchesByTitleIgnoringCase() {
    Matter matter = existing(1, client, "Due diligence adquisición planta", false);

    PageResult<MatterView> page = service.listMatters(null, PageQuery.of(null, null));
    PageResult<MatterView> filtered =
        service.listMatters(new MatterFilter("DILIGENCE", null, null), PageQuery.of(0, 20));

    assertThat(page.items()).hasSize(1);
    assertThat(filtered.items()).extracting(v -> v.matter().id()).containsExactly(matter.id());
  }

  // Scenario: Segunda página de asuntos
  @Test
  void secondPageOfMatters() {
    for (int i = 1; i <= 25; i++) {
      existing(i, client, "Asunto " + i, false);
    }

    PageResult<MatterView> page = service.listMatters(null, PageQuery.of(1, 10));

    assertThat(page.items()).hasSize(10);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalItems()).isEqualTo(25);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items().get(0).matter().code().value()).isEqualTo("AS-2026-0015");
  }

  // Scenario: Asunto existente
  @Test
  void getsMatterWithClientName() {
    Matter matter = existing(1, client, "Título", false);

    MatterView view = service.getMatter(matter.id());

    assertThat(view.matter()).isEqualTo(matter);
    assertThat(view.clientName()).isEqualTo(client.name());
  }

  // Scenario: Asunto inexistente
  @Test
  void unknownMatterIsNotFound() {
    UUID unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.getMatter(unknown))
        .isInstanceOf(MatterNotFoundException.class)
        .extracting(e -> ((MatterNotFoundException) e).matterId())
        .isEqualTo(unknown);
  }

  // Scenario: Cierre exitoso
  // Scenario: El cierre no impone condiciones sobre registros de horas
  @Test
  void partnerClosesOpenMatter() {
    Matter matter = existing(1, client, "Título", false);

    MatterView closed = service.closeMatter(PARTNER, matter.id());

    assertThat(closed.matter().status()).isEqualTo(MatterStatus.CLOSED);
    assertThat(closed.matter().closedAt()).isEqualTo(NOW);
    assertThat(service.getMatter(matter.id()).matter().closedAt()).isEqualTo(NOW);
  }

  // Scenario: Cerrar dos veces
  @Test
  void closingTwiceIsConflictAndKeepsClosedAt() {
    Matter matter = existing(1, client, "Título", true);

    assertThatThrownBy(() -> service.closeMatter(PARTNER, matter.id()))
        .isInstanceOf(MatterAlreadyClosedException.class);
    assertThat(service.getMatter(matter.id()).matter().closedAt()).isEqualTo(T1);
  }

  @Test
  void concurrentCloseByAnotherPartnerIsAlreadyClosed() {
    Matter matter = existing(1, client, "Título", false);
    matters.beforeSaveClosed(id -> matters.closeConcurrently(id, T1));

    assertThatThrownBy(() -> service.closeMatter(PARTNER, matter.id()))
        .isInstanceOf(MatterAlreadyClosedException.class);
    assertThat(service.getMatter(matter.id()).matter().closedAt()).isEqualTo(T1);
  }

  @Test
  void matterDeletedWhileClosingIsNotFound() {
    Matter matter = existing(1, client, "Título", false);
    matters.beforeSaveClosed(matters::removeConcurrently);

    assertThatThrownBy(() -> service.closeMatter(PARTNER, matter.id()))
        .isInstanceOf(MatterNotFoundException.class);
  }

  // Scenario: Cierre de asunto inexistente
  @Test
  void closingUnknownMatterIsNotFound() {
    assertThatThrownBy(() -> service.closeMatter(PARTNER, UUID.randomUUID()))
        .isInstanceOf(MatterNotFoundException.class);
  }

  // Scenario: Abogado intenta cerrar un asunto
  @Test
  void lawyerCannotCloseMatter() {
    Matter matter = existing(1, client, "Título", false);

    assertThatThrownBy(() -> service.closeMatter(LAWYER, matter.id()))
        .isInstanceOf(ForbiddenException.class);
    assertThat(service.getMatter(matter.id()).matter().status()).isEqualTo(MatterStatus.OPEN);
  }

  // Scenario: Abogado cierra un asunto inexistente
  @Test
  void lawyerClosingUnknownMatterIsForbiddenNotNotFound() {
    assertThatThrownBy(() -> service.closeMatter(LAWYER, UUID.randomUUID()))
        .isInstanceOf(ForbiddenException.class);
  }
}
