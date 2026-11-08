package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.MatterCodeSequence;
import java.util.HashMap;
import java.util.Map;

/** Fake en memoria del correlativo anual de códigos de asunto. */
public final class InMemoryMatterCodeSequence implements MatterCodeSequence {

  private final Map<Integer, Integer> lastByYear = new HashMap<>();

  /** Fija el último correlativo usado de un año. */
  public InMemoryMatterCodeSequence withLast(int year, int last) {
    lastByYear.put(year, last);
    return this;
  }

  @Override
  public synchronized int next(int year) {
    return lastByYear.merge(year, 1, Integer::sum);
  }
}
