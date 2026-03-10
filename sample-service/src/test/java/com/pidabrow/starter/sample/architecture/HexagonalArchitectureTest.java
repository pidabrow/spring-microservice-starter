package com.pidabrow.starter.sample.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing hexagonal architecture boundaries in the sample service.
 * <p>
 * Verifies that infrastructure details (JPA entities) never leak into
 * the inbound adapter (web/API) or domain layers.
 */
@DisplayName("Hexagonal Architecture Boundary Tests")
class HexagonalArchitectureTest {

    private static final String SAMPLE_PACKAGE = "com.pidabrow.starter.sample";
    private static final String PERSISTENCE_ENTITY_PACKAGE = SAMPLE_PACKAGE + ".infrastructure.persistence.entity";
    private static final String API_PACKAGE = SAMPLE_PACKAGE + ".api..";
    private static final String DOMAIN_PACKAGE = SAMPLE_PACKAGE + ".domain..";
    private static final String APPLICATION_PACKAGE = SAMPLE_PACKAGE + ".application..";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter().importPackages(SAMPLE_PACKAGE);
    }

    @Test
    @DisplayName("JPA entities must not be referenced from the inbound adapter (api) package")
    void jpa_entities_must_not_leak_into_api_package() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(API_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(PERSISTENCE_ENTITY_PACKAGE);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("JPA entities must not be referenced from the domain layer")
    void jpa_entities_must_not_leak_into_domain_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(PERSISTENCE_ENTITY_PACKAGE);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("JPA entities must not be referenced from the application (use case) layer")
    void jpa_entities_must_not_leak_into_application_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(PERSISTENCE_ENTITY_PACKAGE);

        rule.check(importedClasses);
    }
}