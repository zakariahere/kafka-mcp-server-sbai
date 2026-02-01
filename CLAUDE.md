# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Kafka MCP (Model Context Protocol) server built with **Spring Boot 4.0.2** and **Spring AI 2.0.0-M2**. The MCP server exposes Kafka operations as tools consumable by AI clients via the Model Context Protocol.

- **Java version:** 25
- **Base package:** `com.elzakaria.kafkamcpsbai`
- **Build tool:** Maven (use the wrapper scripts — `mvnw.cmd` on Windows, `mvnw` on Unix)

## Build & Run Commands

```bash
# Build (skip tests for speed during iteration)
mvnw.cmd clean package -DskipTests

# Full build with tests
mvnw.cmd clean verify

# Run the application
mvnw.cmd spring-boot:run

# Run all tests
mvnw.cmd test

# Run a single test class
mvnw.cmd test -Dtest=com.elzakaria.kafkamcpsbai.SomeTestClass

# Run a single test method
mvnw.cmd test -Dtest="com.elzakaria.kafkamcpsbai.SomeTestClass#methodName"
```

## Architecture & Key Decisions

### MCP Server via Spring AI
The project uses `spring-ai-starter-mcp-server` to expose tools over the Model Context Protocol. Spring AI's MCP server support works by scanning for `@Tool`-annotated methods on Spring beans. When you add Kafka functionality, structure it as:

1. A **service layer** that wraps Kafka client operations (produce, consume, list topics, etc.).
2. A **tool provider bean** (annotated with `@Component`) whose `@Tool` methods delegate to the service. Spring AI auto-registers these as MCP tools.

There is no need for a REST controller layer — MCP is the transport.

### Kafka Connectivity
Kafka is not yet wired in. When adding it:
- Add `spring-boot-starter-kafka` to `pom.xml`.
- Configure brokers and any security (SASL/SSL) in `application.properties`.
- Use `compose.yaml` to define a local Kafka broker (+ Zookeeper or KRaft mode) for development. The app currently fails to start because `compose.yaml` is empty — adding a Kafka service there will also satisfy that requirement.

### Lombok
Lombok is on the classpath and its annotation processor is configured in `pom.xml`. Use it freely for DTOs and service classes.

### Docker Compose Integration
Spring Boot's `spring-boot-docker-compose` dependency means the app will automatically start services defined in `compose.yaml` at startup. Put your dev Kafka broker there rather than requiring a separate `docker compose up` step.

## Important Notes

- **Spring Boot 4.0.2 + Spring AI 2.0.0-M2 are pre-release / very recent.** API surfaces may differ from older documentation. Prefer the Spring AI reference docs linked in `HELP.md` for MCP server specifics.
- The application currently does not start due to the empty `compose.yaml`. This is expected until at least one Docker Compose service is defined.
- `.idea/` is tracked in `.gitignore` — don't commit IDE config changes.
