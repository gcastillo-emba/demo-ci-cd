package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.domain.shared.DomainException;
import com.emba.asuntoshoras.domain.shared.InvalidValueException;
import com.emba.asuntoshoras.domain.shared.NotFoundException;
import com.emba.asuntoshoras.domain.timeentry.ConcurrentModificationException;
import com.emba.asuntoshoras.domain.timeentry.DailyHoursExceededException;
import com.emba.asuntoshoras.domain.timeentry.DailyHoursPolicy;
import com.emba.asuntoshoras.domain.timeentry.EditWindowExpiredException;
import com.emba.asuntoshoras.domain.timeentry.EntryNotPendingException;
import com.emba.asuntoshoras.domain.timeentry.FutureDateException;
import com.emba.asuntoshoras.domain.timeentry.Hours;
import com.emba.asuntoshoras.domain.timeentry.HoursInvalidIncrementException;
import com.emba.asuntoshoras.domain.timeentry.HoursOutOfRangeException;
import com.emba.asuntoshoras.domain.timeentry.MatterClosedException;
import com.emba.asuntoshoras.domain.timeentry.NotEntryOwnerException;
import com.emba.asuntoshoras.domain.timeentry.RejectionReasonRequiredException;
import com.emba.asuntoshoras.domain.timeentry.TimeEntryNotFoundException;
import com.emba.asuntoshoras.domain.user.InvalidCredentialsException;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Regla 7 de ADR-001: toda subclase concreta de DomainException tiene entrada propia. */
class ProblemCatalogTest {

  private final ProblemCatalog catalog = new ProblemCatalog();

  @Test
  @SuppressWarnings("unchecked")
  void everyConcreteDomainExceptionIsMapped() {
    List<Class<? extends DomainException>> concrete =
        new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.emba.asuntoshoras")
                .stream()
                .filter(c -> c.isAssignableTo(DomainException.class))
                .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
                .map(JavaClass::reflect)
                .<Class<? extends DomainException>>map(c -> (Class<? extends DomainException>) c)
                .toList();

    assertThat(concrete).isNotEmpty();
    assertThat(concrete)
        .allSatisfy(
            type ->
                assertThat(catalog.isMapped(type))
                    .as("%s debe tener entrada en ProblemCatalog", type.getName())
                    .isTrue());
  }

  @Test
  void mapsInvalidCredentialsTo401() {
    assertThat(catalog.find(InvalidCredentialsException.class))
        .hasValueSatisfying(e -> assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  @Test
  void subclassesInheritTheClosestEntry() {
    class SpecificUserNotFound extends UserNotFoundException {
      SpecificUserNotFound() {
        super(null);
      }
    }

    assertThat(catalog.find(SpecificUserNotFound.class))
        .hasValueSatisfying(e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void unmappedHierarchyIsEmpty() {
    class Orphan extends NotFoundException {
      Orphan() {
        super("orphan", "Huérfana");
      }
    }

    assertThat(catalog.find(Orphan.class)).isEmpty();
    assertThat(catalog.find(String.class)).isEmpty();
  }

  /** Tarea 2.3: los 10 slugs nuevos de C2 (design D6) y su estado HTTP. */
  @Test
  void mapsTimeEntrySlugsToTheirStatus() {
    Map<DomainException, HttpStatus> expected =
        Map.ofEntries(
            Map.entry(new HoursInvalidIncrementException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new HoursOutOfRangeException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(
                new DailyHoursExceededException(DailyHoursPolicy.DAILY_LIMIT, Hours.ZERO),
                HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new FutureDateException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new MatterClosedException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new EditWindowExpiredException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new EntryNotPendingException(), HttpStatus.CONFLICT),
            Map.entry(new ConcurrentModificationException(), HttpStatus.CONFLICT),
            Map.entry(new NotEntryOwnerException(), HttpStatus.FORBIDDEN),
            Map.entry(new RejectionReasonRequiredException(), HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(new TimeEntryNotFoundException(UUID.randomUUID()), HttpStatus.NOT_FOUND),
            Map.entry(new InvalidValueException("from", "x"), HttpStatus.BAD_REQUEST));

    assertThat(expected.keySet())
        .extracting(DomainException::code)
        .contains(
            "hours-invalid-increment",
            "hours-out-of-range",
            "daily-hours-exceeded",
            "future-date",
            "matter-closed",
            "edit-window-expired",
            "entry-not-pending",
            "concurrent-modification",
            "not-entry-owner",
            "rejection-reason-required",
            "not-found",
            "validation-error");
    expected.forEach(
        (exception, status) ->
            assertThat(catalog.find(exception.getClass()))
                .as(exception.code())
                .hasValueSatisfying(e -> assertThat(e.status()).isEqualTo(status)));
  }
}
