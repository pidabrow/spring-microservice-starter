package com.pidabrow.starter.sample.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests for the Transactional Outbox infrastructure (ADR-007).
 * <p>
 * Verifies:
 * - Outbox adapter implementations are package-private (do not leak outside the package)
 * - MessagePublisher port is an interface in platform-common
 * - Infrastructure adapters depend inward, never the reverse
 */
@DisplayName("Outbox Architecture Tests")
class OutboxArchitectureTest {

    private static final String OUTBOX_PACKAGE = "com.pidabrow.starter.infrastructure.outbox";
    private static final String COMMON_OUTBOX_PACKAGE = "com.pidabrow.starter.common.outbox";

    @Test
    @DisplayName("IntegrationEventListener should be package-private")
    void integration_event_listener_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("IntegrationEventListener")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("KafkaMessagePublisher should be package-private")
    void kafka_message_publisher_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("KafkaMessagePublisher")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("OutboxRelayService should be package-private")
    void outbox_relay_service_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("OutboxRelayService")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("OutboxCleanupService should be package-private")
    void outbox_cleanup_service_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("OutboxCleanupService")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("MessageOutboxEntity should be package-private")
    void message_outbox_entity_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("MessageOutboxEntity")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("MessageOutboxRepository should be package-private")
    void message_outbox_repository_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("MessageOutboxRepository")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("MessagePublisher port should be an interface")
    void message_publisher_port_should_be_an_interface() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(COMMON_OUTBOX_PACKAGE);

        ArchRule rule = classes()
                .that().haveSimpleName("MessagePublisher")
                .should().beInterfaces();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Domain layer should not depend on outbox infrastructure")
    void domain_should_not_depend_on_outbox_infrastructure() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(
                "com.pidabrow.starter.common.event",
                OUTBOX_PACKAGE
        );

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.pidabrow.starter.common.event")
                .should().dependOnClassesThat().resideInAPackage(OUTBOX_PACKAGE);

        rule.check(importedClasses);
    }
}

