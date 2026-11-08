package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.AbstractTimeEntriesIT;
import com.emba.asuntoshoras.application.port.out.DailyHoursLock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tarea 2.9: el límite diario se respeta con altas y ediciones concurrentes (design D3, advisory
 * lock por abogado y día). Cada ronda lanza las dos peticiones a la vez desde dos hilos.
 */
class TimeEntriesConcurrencyIT extends AbstractTimeEntriesIT {

  private static final int ROUNDS = 5;

  @Autowired private DailyHoursLock dailyHoursLock;
  @Autowired private PlatformTransactionManager transactionManager;

  /** Ejecuta las tareas a la vez (barrera común) y devuelve los códigos HTTP. */
  private List<Integer> inParallel(List<Callable<MvcResult>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<MvcResult>> futures = new ArrayList<>();
      for (Callable<MvcResult> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<MvcResult> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
      }
      return statuses;
    } finally {
      pool.shutdownNow();
    }
  }

  // Scenario: Dos altas simultáneas de 13 horas
  @Test
  void twoParallelCreatesOf13HoursOnlyOneSucceeds() throws Exception {
    String token = lawyer();
    for (int round = 0; round < ROUNDS; round++) {
      String day = LocalDate.parse("2026-11-05").minusDays(round).toString();
      Map<String, Object> body = body(day, "13");

      List<Integer> statuses =
          inParallel(
              List.of(
                  () -> create(token, body).andReturn(), () -> create(token, body).andReturn()));

      assertThat(statuses).as("ronda %d", round).containsExactlyInAnyOrder(201, 422);
      assertThat(countedTenths(LAWYER_ID, day)).isEqualTo(130);
    }
  }

  /**
   * Scenario: Alta y edición simultáneas el mismo día. Edición a 15 h: el resultado conjunto (25 h)
   * supera el límite inclusivo de 24 h (la primera versión de la spec decía 14 h; corregida en la
   * integración, ver bitácora).
   */
  @Test
  void parallelCreateAndEditOnlyOneSucceeds() throws Exception {
    String token = lawyer();
    for (int round = 0; round < ROUNDS; round++) {
      String day = LocalDate.parse("2026-11-05").minusDays(round).toString();
      UUID existing = insertPending(day, 100);
      Map<String, Object> create = body(day, "10");
      Map<String, Object> edit = withVersion(body(day, "15"), 0);

      List<Integer> statuses =
          inParallel(
              List.of(
                  () -> create(token, create).andReturn(),
                  () -> update(token, existing, edit).andReturn()));

      assertThat(statuses)
          .as("ronda %d: %s", round, statuses)
          .hasSize(2)
          .containsOnlyOnce(422)
          .anySatisfy(s -> assertThat(s).isIn(200, 201));
      assertThat(countedTenths(LAWYER_ID, day)).isLessThanOrEqualTo(240);
    }
  }

  /** El bloqueo es por abogado y día: otra transacción no puede tomar la misma clave. */
  @Test
  void advisoryLockIsHeldUntilTheEndOfTheTransaction() throws Exception {
    UUID lawyerId = UUID.fromString(LAWYER_ID);
    LocalDate day = LocalDate.parse("2026-11-05");
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        dailyHoursLock.lock(lawyerId, List.of(day));
                        locked.countDown();
                        try {
                          release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

      Boolean sameKey = tryLock("time-entry:" + lawyerId + ":" + day);
      Boolean otherDay = tryLock("time-entry:" + lawyerId + ":" + day.minusDays(1));
      Boolean otherLawyer = tryLock("time-entry:" + LAWYER_B_ID + ":" + day);

      release.countDown();
      holder.get(30, TimeUnit.SECONDS);
      assertThat(sameKey).isFalse();
      assertThat(otherDay).isTrue();
      assertThat(otherLawyer).isTrue();
      assertThat(tryLock("time-entry:" + lawyerId + ":" + day)).isTrue();
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  private Boolean tryLock(String key) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status ->
                jdbc.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, key));
  }
}
