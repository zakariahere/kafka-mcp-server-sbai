# kafka-mcp-sbai

A Kafka MCP (Model Context Protocol) server built with Spring Boot and Spring AI. Exposes Kafka cluster operations as tools consumable by AI clients over the MCP protocol via SSE transport.

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 4.0.2 |
| Spring AI | 2.0.0-M2 |
| Apache Kafka | via `spring-boot-starter-kafka` |
| Build | Maven (wrapper scripts included) |

## Prerequisites

- Java 25+
- Docker (for the local Kafka broker)

## Quick Start

```bash
# Build
mvnw.cmd clean package -DskipTests          # Windows
./mvnw clean package -DskipTests            # Unix

# Run
mvnw.cmd spring-boot:run                    # Windows
./mvnw spring-boot:run                      # Unix
```

Spring Boot's Docker Compose integration automatically starts the Kafka broker, Schema Registry, and Kafka UI defined in `compose.yaml` when the application launches. No manual `docker compose up` is needed.

The MCP server starts on **port 9085**.

## Local Infrastructure (compose.yaml)

| Service | Port | Description |
|---------|------|-------------|
| Kafka (KRaft) | 9092 | Single-node broker, auto-creates topics |
| Schema Registry | 8081 | Confluent Schema Registry |
| Kafka UI | 8080 | Provectus Kafka UI for cluster management |

## Exposed MCP Tools

### Topic Management

| Tool | Description |
|------|-------------|
| `listTopics` | Lists all topics in the cluster |
| `describeTopic` | Returns partition layout, replicas, ISR, and non-default config for a topic |
| `createTopic` | Creates a topic with a specified partition count and replication factor |
| `deleteTopic` | Permanently deletes a topic and all its messages |

### Messaging

| Tool | Parameters | Description |
|------|------------|-------------|
| `produceMessage` | topic, message, key?, headers? | Publishes a message; returns partition and offset |
| `consumeMessages` | topic, maxMessages?, fromBeginning?, timeoutSeconds? | Reads messages via a short-lived consumer group |
| `peekMessages` | topic, partition, offset, count? | Reads from a specific partition/offset without committing |

### Consumer Groups

| Tool | Description |
|------|-------------|
| `listConsumerGroups` | Lists all consumer group IDs |
| `describeConsumerGroup` | Returns group state, coordinator, members, and partition assignments |

### Cluster

| Tool | Description |
|------|-------------|
| `describeCluster` | Returns cluster ID, controller, and all broker details |

## Architecture

```
AI Client  ──SSE──>  MCP Server (port 9085)
                          │
                    ┌─────▼──────┐
                    │ToolProvider │   @Tool-annotated methods, auto-registered
                    └─────┬──────┘      by Spring AI's MethodToolCallbackProvider
                          │
                    ┌─────▼──────┐
                    │KafkaService │   Wraps AdminClient, KafkaTemplate,
                    └─────┬──────┘   and ad-hoc KafkaConsumer instances
                          │
                    ┌─────▼──────┐
                    │   Kafka     │   Local broker via Docker Compose
                    └────────────┘
```

- **No REST layer.** MCP over SSE is the sole transport.
- `McpMessageStatusFilter` patches the `/mcp/message` POST response from `200 OK` to `202 Accepted` to satisfy Claude's SSE expectations.
- Consumer operations (`consumeMessages`, `peekMessages`) spin up ephemeral `KafkaConsumer` instances with unique group IDs so they don't interfere with application consumers.

## Configuration

Key properties in `application.properties`:

```properties
server.port=9085
spring.ai.mcp.server.transport=sse
spring.ai.mcp.server.sse-endpoint=/sse
spring.ai.mcp.server.sse-message-endpoint=/mcp/message
spring.kafka.bootstrap-servers=localhost:9092
```

## Testing

```bash
# Full test suite
mvnw.cmd test

# Single test class
mvnw.cmd test -Dtest=com.elzakaria.kafkamcpsbai.SomeTestClass

# Single test method
mvnw.cmd test -Dtest="com.elzakaria.kafkamcpsbai.SomeTestClass#methodName"
```
