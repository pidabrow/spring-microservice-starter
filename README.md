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

- Java 21 (for local development)
- Docker and Docker Compose (for containerized deployment)

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Run the Sample Service

#### Local Development

Run the application locally:

```bash
./gradlew :sample-service:bootRun
```

The service will start on port 8080.

**Note:** For local development, ensure PostgreSQL is running and configured in `application.yml`.

#### Containerized Deployment

The project includes Docker support for reproducible, containerized deployments.

**Start the entire stack (PostgreSQL + Application):**

```bash
docker compose up --build
```

This command will:
- Build the application Docker image (multi-stage build)
- Start PostgreSQL 16+ in a container
- Start the sample-service application
- Wait for PostgreSQL to be healthy before starting the application
- Run database migrations automatically via Flyway

**Stop the stack:**

```bash
docker compose down
```

**Stop and remove volumes (clean slate):**

```bash
docker compose down -v
```

**View logs:**

```bash
docker compose logs -f sample-service
```

**Environment Variables:**

You can customize the deployment using environment variables:

```bash
# PostgreSQL configuration
export POSTGRES_DB=starter_db
export POSTGRES_USER=starter_user
export POSTGRES_PASSWORD=starter_pass
export POSTGRES_PORT=5432

# Application port
export APP_PORT=8080

# Then start
docker compose up --build
```

**Docker Features:**

- **Multi-stage build**: Optimized Dockerfile with separate build and runtime stages
- **Non-root user**: Application runs as `spring` user for security
- **Health checks**: Both PostgreSQL and application include health checks
- **Persistent storage**: PostgreSQL data is stored in a named volume
- **Isolated network**: Services communicate via a dedicated bridge network
- **Layer caching**: Dependencies are cached to speed up subsequent builds

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