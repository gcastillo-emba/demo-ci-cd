package com.emba.asuntoshoras;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Reglas mecánicas de ADR-001 (hexagonal).
 *
 * <p>Se ejecutan como tests de JUnit Jupiter ({@code rule.check(...)}) y no con el motor
 * {@code @ArchTest}: con Surefire 3.5.3 el motor de ArchUnit reportaba 0 tests y no evaluaba las
 * reglas (gate silencioso, bitácora de fallas).
 */
class ArchitectureTest {

  private static final String ROOT = "com.emba.asuntoshoras";
  private static final String DOMAIN = ROOT + ".domain..";
  private static final String APPLICATION = ROOT + ".application..";
  private static final String ADAPTERS = ROOT + ".adapters..";
  private static final String CONFIG = ROOT + ".config..";

  private static JavaClasses classes;

  @BeforeAll
  static void importProductionClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  @Test
  void importsProductionClassesOfEveryLayer() {
    assertThat(classes.containPackage(ROOT + ".domain.user")).isTrue();
    assertThat(classes.containPackage(ROOT + ".application.service")).isTrue();
    assertThat(classes.containPackage(ROOT + ".adapters.in.web")).isTrue();
    assertThat(classes.containPackage(ROOT + ".config")).isTrue();
  }

  /** Regla 1: capas onion; nadie depende de config. */
  @Test
  void onionLayers() {
    check(
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy(DOMAIN)
            .layer("Application")
            .definedBy(APPLICATION)
            .layer("Adapters")
            .definedBy(ADAPTERS)
            .layer("Config")
            .definedBy(CONFIG)
            .whereLayer("Config")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapters")
            .mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapters", "Config")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Adapters", "Config"));
  }

  /** Regla 1: ningún adaptador depende de otro adaptador. */
  @Test
  void adaptersAreIndependent() {
    check(slices().matching(ROOT + ".adapters.(*).(*)..").should().notDependOnEachOther());
  }

  /** Los adaptadores de entrada solo usan puertos de entrada (no servicios ni puertos out). */
  @Test
  void inboundAdaptersUseOnlyInboundPorts() {
    check(
        noClasses()
            .that()
            .resideInAPackage(ROOT + ".adapters.in..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + ".application.service..", ROOT + ".application.port.out.."));
  }

  /** Regla 2: el dominio solo depende del JDK y de sí mismo. */
  @Test
  void domainIsPure() {
    check(
        classes()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", DOMAIN));
  }

  /** Aplicación: JDK, dominio, aplicación y solo {@code @Transactional} de Spring. */
  @Test
  void applicationDependsOnlyOnDomainAndTransactions() {
    check(
        classes()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..", DOMAIN, APPLICATION, "org.springframework.transaction.annotation.."));
  }

  /** Los servicios de aplicación se cablean en config, sin estereotipos. */
  @Test
  void applicationHasNoStereotypes() {
    check(
        noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .beAnnotatedWith(Component.class)
            .orShould()
            .beAnnotatedWith(Service.class));
  }

  /** Regla 3: {@code @Entity} solo en persistencia. */
  @Test
  void entitiesOnlyInPersistence() {
    check(
        classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage(ROOT + ".adapters.out.persistence.."));
  }

  /** Regla 3: controllers y advices solo en el adaptador web. */
  @Test
  void controllersOnlyInWebAdapter() {
    check(
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(RestControllerAdvice.class)
            .should()
            .resideInAPackage(ROOT + ".adapters.in.web.."));
  }

  /** API-first: cada controller implementa una interfaz generada desde el contrato. */
  @Test
  void controllersImplementGeneratedApi() {
    check(
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .implement(
                JavaClass.Predicates.resideInAPackage(ROOT + ".adapters.in.web.api")
                    .and(JavaClass.Predicates.INTERFACES)));
  }

  /** Regla 4: fechas y horas solo vía {@link java.time.Clock} inyectado. */
  @Test
  void noSystemTimeOutsideConfig() {
    check(
        noClasses()
            .that()
            .resideOutsideOfPackage(CONFIG)
            .should()
            .callMethodWhere(
                target(name("now"))
                    .and(target(owner(JavaClass.Predicates.resideInAPackage("java.time")))))
            .orShould()
            .callMethodWhere(
                target(nameMatching("system.*"))
                    .and(target(owner(JavaClass.Predicates.equivalentTo(java.time.Clock.class)))))
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .because("ADR-001: 'hoy' se calcula con un Clock inyectado (America/La_Paz)"));
  }

  /** Regla 5: sin inyección por campo. */
  @Test
  void noFieldInjection() {
    check(NO_CLASSES_SHOULD_USE_FIELD_INJECTION);
  }

  private static void check(ArchRule rule) {
    rule.check(classes);
  }
}
