package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.matter.MatterStatus;
import java.util.UUID;

/** Casos de uso de asuntos: alta y cierre (solo Socio), listado filtrado y consulta por id. */
public interface MatterUseCases {

  /**
   * Crea un asunto {@code OPEN} con código {@code AS-YYYY-NNNN} generado por el servidor.
   *
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Socio
   * @throws com.emba.asuntoshoras.domain.client.ClientNotFoundException si el cliente no existe
   */
  MatterView createMatter(CurrentUser user, NewMatter command);

  /** Lista asuntos por código descendente con filtros opcionales combinables. */
  PageResult<MatterView> listMatters(MatterFilter filter, PageQuery page);

  /**
   * @throws com.emba.asuntoshoras.domain.matter.MatterNotFoundException si no existe
   */
  MatterView getMatter(UUID matterId);

  /**
   * Cierra un asunto abierto (irreversible).
   *
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Socio
   * @throws com.emba.asuntoshoras.domain.matter.MatterNotFoundException si no existe
   * @throws com.emba.asuntoshoras.domain.matter.MatterAlreadyClosedException si ya está cerrado
   */
  MatterView closeMatter(CurrentUser user, UUID matterId);

  /** Datos de alta de un asunto (el cliente no puede fijar código, estado ni cierre). */
  record NewMatter(UUID clientId, String title, String description) {}

  /** Filtros del listado; {@code q} busca en título o código sin distinguir mayúsculas. */
  record MatterFilter(String q, UUID clientId, MatterStatus status) {}
}
