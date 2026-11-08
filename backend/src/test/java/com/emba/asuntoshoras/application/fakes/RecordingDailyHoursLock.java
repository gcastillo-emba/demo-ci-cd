package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.DailyHoursLock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Fake de {@link DailyHoursLock}: registra las claves bloqueadas, en el orden en que se toman. */
public final class RecordingDailyHoursLock implements DailyHoursLock {

  private final List<String> locked = new ArrayList<>();

  @Override
  public void lock(UUID lawyerId, Collection<LocalDate> workDates) {
    for (LocalDate date : new TreeSet<>(workDates)) {
      locked.add(lawyerId + ":" + date);
    }
  }

  public List<String> locked() {
    return List.copyOf(locked);
  }
}
