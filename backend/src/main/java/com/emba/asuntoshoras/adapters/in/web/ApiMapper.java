package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.model.Client;
import com.emba.asuntoshoras.adapters.in.web.api.model.ClientPage;
import com.emba.asuntoshoras.adapters.in.web.api.model.Matter;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterPage;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterStatus;
import com.emba.asuntoshoras.adapters.in.web.api.model.Role;
import com.emba.asuntoshoras.adapters.in.web.api.model.User;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Mapeo dominio → DTOs generados del contrato. */
final class ApiMapper {

  private ApiMapper() {}

  static User toDto(com.emba.asuntoshoras.domain.user.User user) {
    return new User()
        .id(user.id())
        .email(user.email().value())
        .fullName(user.fullName())
        .role(Role.fromValue(user.role().name()));
  }

  static Client toDto(com.emba.asuntoshoras.domain.client.Client client) {
    return new Client()
        .id(client.id())
        .name(client.name())
        .taxId(client.taxId())
        .createdAt(toOffset(client.createdAt()));
  }

  static Matter toDto(MatterView view) {
    com.emba.asuntoshoras.domain.matter.Matter matter = view.matter();
    return new Matter()
        .id(matter.id())
        .code(matter.code().value())
        .clientId(matter.clientId())
        .clientName(view.clientName())
        .title(matter.title())
        .description(matter.description())
        .status(MatterStatus.fromValue(matter.status().name()))
        .createdAt(toOffset(matter.createdAt()))
        .closedAt(toOffset(matter.closedAt()));
  }

  static ClientPage toClientPage(PageResult<com.emba.asuntoshoras.domain.client.Client> page) {
    return new ClientPage()
        .items(page.items().stream().map(ApiMapper::toDto).toList())
        .page(page.page())
        .size(page.size())
        .totalItems(page.totalItems())
        .totalPages(page.totalPages());
  }

  static MatterPage toMatterPage(PageResult<MatterView> page) {
    return new MatterPage()
        .items(page.items().stream().map(ApiMapper::toDto).toList())
        .page(page.page())
        .size(page.size())
        .totalItems(page.totalItems())
        .totalPages(page.totalPages());
  }

  static com.emba.asuntoshoras.domain.matter.MatterStatus toDomain(MatterStatus status) {
    return status == null
        ? null
        : com.emba.asuntoshoras.domain.matter.MatterStatus.valueOf(status.getValue());
  }

  private static OffsetDateTime toOffset(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
