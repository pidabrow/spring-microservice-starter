# Spring Microservice Starter

An opinionated Spring Boot 3 microservice starter monorepo using Gradle (Kotlin DSL) and Java 21.

## Overview

This project provides a foundation for building microservices with Spring Boot 3, following a modular monorepo structure with shared platform modules and service implementations.

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.0
- **Build Tool**: Gradle 8.5 (Kotlin DSL)

## Project Structure

```
spring-microservice-starter/
├── platform-common/          # Shared utilities and common code
│   └── src/main/java/com/pidabrow/starter/common/
│       └── exception/        # Common exception classes
├── platform-data/            # JPA/Hibernate and database configurations
│   └── src/main/java/com/pidabrow/starter/data/
│       ├── config/           # Data layer configuration
│       └── entity/           # Base entities
├── platform-web/             # REST/Web layer configurations
│   └── src/main/java/com/pidabrow/starter/web/
│       ├── config/           # Web configuration
│       └── exception/        # Global exception handlers
└── sample-service/           # Sample microservice implementation
    └── src/main/java/com/pidabrow/starter/sample/
        └── controller/       # REST controllers
```

## Modules

### platform-common
Shared utilities and common code used across all services:
- Common exception classes
- Shared constants

### platform-data
Database and persistence layer:
- JPA configuration
- Base entity classes with audit fields (id, createdAt, updatedAt)
- Common repository configurations

### platform-web
REST API and web layer:
- Web configuration
- Global exception handling
- Common REST utilities

### sample-service
Example microservice demonstrating the platform usage:
- Main application class: `MicroserviceStarterApplication`
- Simple HelloController returning "Hello World"

## Getting Started

### Prerequisites

- Java 21

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Run the Sample Service

Run the application:

```bash
./gradlew :sample-service:bootRun
```

The service will start on port 8080.

## API Endpoints

The sample service exposes a simple hello world endpoint:

- `GET /` - Returns "Hello World"

## CI/CD

The project includes a GitHub Actions CI pipeline that:
- Runs on all push and pull request events
- Sets up Java 21 with Temurin distribution
- Builds the project using Gradle
- Executes all tests

See `.github/workflows/ci.yml` for the pipeline configuration.

## Package Structure

All code follows the root package convention:
```
com.pidabrow.starter
```

## License

This project is a starter template for microservices development.