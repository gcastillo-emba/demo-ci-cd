package com.emba.asuntoshoras.application.port.in;

import com.emba.asuntoshoras.domain.client.Client;
import java.util.UUID;

/** Casos de uso de clientes: alta (solo Socio), listado filtrado y consulta por id. */
public interface ClientUseCases {

  /**
   * Crea un cliente.
   *
   * @throws com.emba.asuntoshoras.domain.shared.ForbiddenException si el usuario no es Socio
   * @throws com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException si el taxId ya existe
   */
  Client createClient(CurrentUser user, NewClient command);

  /** Lista clientes por nombre ascendente, con filtro opcional {@code q} sobre el nombre. */
  PageResult<Client> listClients(ClientFilter filter, PageQuery page);

  /**
   * @throws com.emba.asuntoshoras.domain.client.ClientNotFoundException si no existe
   */
  Client getClient(UUID clientId);

  /** Datos de alta de un cliente. */
  record NewClient(String name, String taxId) {}

  /** Filtro del listado: {@code q} es coincidencia parcial sin distinguir mayúsculas. */
  record ClientFilter(String q) {}
}
