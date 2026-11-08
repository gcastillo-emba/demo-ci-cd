package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.SuggestionEventLog;
import java.util.ArrayList;
import java.util.List;

/** Fake del puerto {@link SuggestionEventLog} que guarda los eventos recibidos. */
public final class RecordingSuggestionEventLog implements SuggestionEventLog {

  private final List<SuggestionEvent> events = new ArrayList<>();

  @Override
  public synchronized void record(SuggestionEvent event) {
    events.add(event);
  }

  public synchronized List<SuggestionEvent> events() {
    return List.copyOf(events);
  }

  public synchronized SuggestionEvent last() {
    return events.get(events.size() - 1);
  }
}
