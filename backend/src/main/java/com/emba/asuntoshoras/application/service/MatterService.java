package com.emba.asuntoshoras.application.service;

import com.emba.asuntoshoras.application.port.in.CurrentUser;
import com.emba.asuntoshoras.application.port.in.MatterUseCases;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.application.port.out.MatterCodeSequence;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.domain.client.Client;
import com.emba.asuntoshoras.domain.client.ClientNotFoundException;
import com.emba.asuntoshoras.domain.matter.Matter;
import com.emba.asuntoshoras.domain.matter.MatterAlreadyClosedException;
import com.emba.asuntoshoras.domain.matter.MatterCode;
import com.emba.asuntoshoras.domain.matter.MatterNotFoundException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.Text;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, listado, consulta y cierre de asuntos. Solo el Socio escribe. Orden de evaluación (S11):
 * rol (403) → datos (400) → existencia (404) → estado (409).
 */
public class MatterService implements MatterUseCases {

  private final MatterRepository matters;
  private final ClientRepository clients;
  private final MatterCodeSequence codeSequence;
  private final Clock clock;

  public MatterService(
      MatterRepository matters,
      ClientRepository clients,
      MatterCodeSequence codeSequence,
      Clock clock) {
    this.matters = Objects.requireNonNull(matters);
    this.clients = Objects.requireNonNull(clients);
    this.codeSequence = Objects.requireNonNull(codeSequence);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  @Transactional
  public MatterView createMatter(CurrentUser user, NewMatter command) {
    Authorization.requirePartner(user);
    if (command.clientId() == null) {
      throw new InvalidValueException("clientId", "El cliente del asunto es obligatorio.");
    }
    String title = Text.required("title", command.title(), Matter.TITLE_MAX_LENGTH);
    String description =
        Text.optional("description", command.description(), Matter.DESCRIPTION_MAX_LENGTH);
    Client client =
        clients
            .findById(command.clientId())
            .orElseThrow(() -> new ClientNotFoundException(command.clientId()));
    Instant now = now();
    // Año de creación en la zona de la firma (Clock en America/La_Paz, design D3 / S4).
    int year = now.atZone(clock.getZone()).getYear();
    MatterCode code = MatterCode.of(year, codeSequence.next(year));
    Matter matter = Matter.open(UUID.randomUUID(), code, client.id(), title, description, now);
    matters.insert(matter);
    return new MatterView(matter, client.name());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<MatterView> listMatters(MatterFilter filter, PageQuery page) {
    return matters.search(filter == null ? new MatterFilter(null, null, null) : filter, page);
  }

  @Override
  @Transactional(readOnly = true)
  public MatterView getMatter(UUID matterId) {
    return matters.findById(matterId).orElseThrow(() -> new MatterNotFoundException(matterId));
  }

  @Override
  @Transactional
  public MatterView closeMatter(CurrentUser user, UUID matterId) {
    Authorization.requirePartner(user);
    MatterView current = getMatter(matterId);
    Matter matter = current.matter();
    matter.close(now());
    if (!matters.saveClosed(matter)) {
      // Otro Socio lo cerró entre la lectura y la escritura: el resultado observable es el mismo
      // que cerrar dos veces (design D5).
      getMatter(matterId);
      throw new MatterAlreadyClosedException();
    }
    return new MatterView(matter, current.clientName());
  }

  /** Instante actual con la precisión de PostgreSQL (microsegundos). */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
