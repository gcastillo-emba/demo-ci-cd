package com.emba.asuntoshoras;

import com.emba.asuntoshoras.ControlledBeansConfiguration.FailingClientRepository;
import com.emba.asuntoshoras.ControlledBeansConfiguration.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * IT con reloj movible y repositorio de clientes que puede fallar. Usan un contexto propio (otro
 * PostgreSQL en Testcontainers), compartido por todas las subclases.
 */
@Import({TestcontainersConfiguration.class, ControlledBeansConfiguration.class})
public abstract class AbstractControlledIT extends AbstractClientsMattersIT {

  @Autowired protected MutableClock clock;
  @Autowired protected FailingClientRepository failingClients;
}
