# Spring Microservice Starter

An opinionated Spring Boot 3 microservice starter monorepo using Gradle (Kotlin DSL) and Java 21.

## Overview

This project provides a foundation for building microservices with Spring Boot 3, following a modular monorepo structure with shared platform modules and service implementations.

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.0
- **Build Tool**: Gradle 8.5 (Kotlin DSL)
- **Database**: PostgreSQL (production), H2 (testing)
- **ORM**: JPA/Hibernate

## Project Structure

```
spring-microservice-starter/
├── platform-common/          # Shared utilities and common code
│   └── src/main/java/com/pidabrow/starter/common/
│       ├── exception/        # Common exception classes
│       └── util/             # Utility classes
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
        ├── controller/       # REST controllers
        ├── entity/           # Domain entities
        ├── repository/       # JPA repositories
        └── service/          # Business logic
```

## Modules

### platform-common
Shared utilities and common code used across all services:
- Common exception classes
- Utility functions
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
- Product entity with CRUD operations
- REST API endpoints for product management
- PostgreSQL database integration

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL (for running the application)

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Run the Sample Service

1. Ensure PostgreSQL is running and accessible at `localhost:5432`
2. Create a database named `microservice_db`
3. Run the application:

```bash
./gradlew :sample-service:bootRun
```

The service will start on port 8080.

### Configuration

Database configuration can be found in `sample-service/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/microservice_db
    username: postgres
    password: postgres
```

## API Endpoints

The sample service exposes the following REST endpoints:

- `GET /api/products` - Get all products
- `GET /api/products/{id}` - Get product by ID
- `POST /api/products` - Create a new product
- `PUT /api/products/{id}` - Update a product
- `DELETE /api/products/{id}` - Delete a product

## Package Structure

All code follows the root package convention:
```
com.pidabrow.starter
```

## License

This project is a starter template for microservices development.