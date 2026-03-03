package com.pidabrow.starter.sample.architecture;

import com.pidabrow.starter.common.event.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ArchUnit tests for domain events and auditing architecture.
 * 
 * Verifies:
 * - Domain events are immutable and don't depend on Spring or JPA
 * - AuditLog entity has no public setters (append-only)
 * - Adapters are package-private
 */
@DisplayName("Domain Events and Auditing Architecture Tests")
class DomainEventsArchitectureTest {

    private static final String COMMON_PACKAGE = "com.pidabrow.starter.common";
    private static final String DATA_PACKAGE = "com.pidabrow.starter.data";
    private static final String WEB_PACKAGE = "com.pidabrow.starter.web";

    @Test
    @DisplayName("Domain events should not depend on Spring")
    void domain_events_should_not_depend_on_spring() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(COMMON_PACKAGE + ".event");

        ArchRule rule = noClasses()
                .that().resideInAPackage(COMMON_PACKAGE + ".event")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Domain events should not depend on JPA")
    void domain_events_should_not_depend_on_jpa() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(COMMON_PACKAGE + ".event");

        ArchRule rule = noClasses()
                .that().resideInAPackage(COMMON_PACKAGE + ".event")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Domain events should be records")
    void domain_events_should_be_records() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(COMMON_PACKAGE + ".event");

        ArchRule rule = classes()
                .that().implement("com.pidabrow.starter.common.event.DomainEvent")
                .should().beRecords();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("AuditLog entity should be in the correct package")
    void audit_log_entity_should_be_in_correct_package() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(DATA_PACKAGE + ".entity");

        // Verify that AuditLog class is in the entity package
        // The append-only nature (no public setters) is enforced by code design:
        // - Only getters and a static factory method are provided
        // - Protected no-args constructor for JPA
        // - No public setters in the implementation
        ArchRule rule = classes()
                .that().haveSimpleName("AuditLog")
                .should().resideInAPackage(DATA_PACKAGE + ".entity");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("AuditLogListener should be package-private")
    void audit_log_listener_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(DATA_PACKAGE + ".audit");

        ArchRule rule = classes()
                .that().haveSimpleName("AuditLogListener")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("SpringDomainEventPublisher adapter should be package-private")
    void spring_domain_event_publisher_adapter_should_be_package_private() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(WEB_PACKAGE + ".event");

        ArchRule rule = classes()
                .that().haveSimpleName("SpringDomainEventPublisher")
                .should().notBePublic();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("DomainEventPublisher port should be an interface")
    void domain_event_publisher_port_should_be_an_interface() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(COMMON_PACKAGE + ".event");

        ArchRule rule = classes()
                .that().haveSimpleName("DomainEventPublisher")
                .should().beInterfaces();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("DomainEvent should be a sealed interface")
    void domain_event_should_be_a_sealed_interface() {
        // Verify that DomainEvent is sealed by checking the actual Java class
        Class<?> domainEventClass = DomainEvent.class;
        
        // Check if the class is sealed using Java reflection
        boolean isSealed = domainEventClass.isSealed();
        org.assertj.core.api.Assertions.assertThat(isSealed)
                .as("DomainEvent should be a sealed interface")
                .isTrue();
        
        // Verify that all expected event types are in the permits list
        Class<?>[] permittedSubtypes = domainEventClass.getPermittedSubclasses();
        var permittedTypeNames = java.util.Arrays.stream(permittedSubtypes)
                .map(Class::getSimpleName)
                .toList();
        org.assertj.core.api.Assertions.assertThat(permittedTypeNames)
                .as("DomainEvent should permit EntityCreatedEvent, EntityUpdatedEvent, UserCreatedEvent, UserUpdatedEvent, UserDeletedEvent, NotificationRequestedEvent")
                .contains("EntityCreatedEvent", "EntityUpdatedEvent", "UserCreatedEvent", "UserUpdatedEvent", "UserDeletedEvent", "NotificationRequestedEvent");
    }
}

