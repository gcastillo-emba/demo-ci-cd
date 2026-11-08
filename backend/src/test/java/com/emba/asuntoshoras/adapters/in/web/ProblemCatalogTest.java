package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.domain.shared.DomainException;
import com.emba.asuntoshoras.domain.shared.NotFoundException;
import com.emba.asuntoshoras.domain.user.InvalidCredentialsException;
import com.emba.asuntoshoras.domain.user.UserNotFoundException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
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
}
