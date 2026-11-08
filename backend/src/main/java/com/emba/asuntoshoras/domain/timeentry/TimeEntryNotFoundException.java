package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.NotFoundException;
import java.util.UUID;

/** El registro de horas solicitado no existe. */
public class TimeEntryNotFoundException extends NotFoundException {

  public static final String CODE = "not-found";

  private final UUID timeEntryId;

  public TimeEntryNotFoundException(UUID timeEntryId) {
    super(CODE, "El registro de horas solicitado no existe.");
    this.timeEntryId = timeEntryId;
  }

  public UUID timeEntryId() {
    return timeEntryId;
  }
}
