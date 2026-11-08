package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.application.port.in.ClientUseCases.ClientFilter;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.domain.client.Client;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: persistencia de clientes. */
public interface ClientRepository {

  /**
   * Inserta un cliente nuevo.
   *
   * @throws com.emba.asuntoshoras.domain.client.ClientTaxIdTakenException si otro cliente ya tiene
   *     el mismo taxId (carrera entre dos altas)
   */
  void insert(Client client);

  Optional<Client> findById(UUID id);

  /** Indica si existe un cliente con ese taxId (comparación exacta). */
  boolean existsByTaxId(String taxId);

  /** Busca clientes ordenados por {@code name asc, id asc}. */
  PageResult<Client> search(ClientFilter filter, PageQuery page);
}
