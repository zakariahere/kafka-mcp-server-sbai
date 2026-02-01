# Kafka MCP Server - Architecture

## Package Structure

```
com.elzakaria.kafkamcpsbai
├── KafkaMcpSbaiApplication.java    # Spring Boot entry point + Bean config
├── dto/                             # Data Transfer Objects
│   ├── ClusterInfo.java
│   ├── ConsumerGroupInfo.java
│   ├── KafkaMessage.java
│   ├── ProduceResult.java
│   └── TopicInfo.java
├── filter/
│   └── McpMessageStatusFilter.java  # HTTP filter for MCP compatibility
├── service/
│   └── KafkaService.java            # Kafka operations
└── tool/
    └── KafkaToolProvider.java       # MCP Tool definitions
```

## Class Diagram (UML)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SPRING BOOT APPLICATION                           │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│    <<SpringBootApplication>>            │
│    KafkaMcpSbaiApplication              │
├─────────────────────────────────────────┤
│ + main(args: String[])                  │
│ + kafkaTools(): ToolCallbackProvider    │──────────┐
└─────────────────────────────────────────┘          │
                                                     │ creates
                                                     ▼
┌─────────────────────────────────────────┐    ┌─────────────────────────────┐
│         <<Component>>                   │    │  <<Interface>>              │
│         KafkaToolProvider               │    │  ToolCallbackProvider       │
├─────────────────────────────────────────┤    │  (Spring AI)                │
│ - kafkaService: KafkaService            │    └─────────────────────────────┘
│ - objectMapper: ObjectMapper            │
├─────────────────────────────────────────┤
│ <<@Tool>> + listTopics()                │
│ <<@Tool>> + describeTopic(name)         │
│ <<@Tool>> + createTopic(name,p,rf)      │
│ <<@Tool>> + deleteTopic(name)           │
│ <<@Tool>> + produceMessage(...)         │
│ <<@Tool>> + consumeMessages(...)        │
│ <<@Tool>> + peekMessages(...)           │
│ <<@Tool>> + listConsumerGroups()        │
│ <<@Tool>> + describeConsumerGroup(id)   │
│ <<@Tool>> + describeCluster()           │
└──────────────────┬──────────────────────┘
                   │ uses
                   ▼
┌─────────────────────────────────────────┐
│         <<Service>>                     │
│         KafkaService                    │
├─────────────────────────────────────────┤
│ - kafkaAdmin: KafkaAdmin                │
│ - kafkaTemplate: KafkaTemplate          │
├─────────────────────────────────────────┤
│ + listTopics(): List<String>            │
│ + describeTopic(name): TopicInfo        │
│ + createTopic(name,p,rf): String        │
│ + deleteTopic(name): String             │
│ + produceMessage(...): ProduceResult    │
│ + consumeMessages(...): List<Message>   │
│ + peekMessages(...): List<Message>      │
│ + listConsumerGroups(): List<String>    │
│ + describeConsumerGroup(id): GroupInfo  │
│ + describeCluster(): ClusterInfo        │
│ - getAdminClient(): AdminClient         │
└──────────────────┬──────────────────────┘
                   │ returns
                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              <<DTOs>>                                       │
├─────────────────────┬─────────────────────┬─────────────────────────────────┤
│                     │                     │                                 │
│  ┌───────────────┐  │  ┌───────────────┐  │  ┌───────────────────────────┐  │
│  │  TopicInfo    │  │  │  ClusterInfo  │  │  │  ConsumerGroupInfo        │  │
│  ├───────────────┤  │  ├───────────────┤  │  ├───────────────────────────┤  │
│  │ name          │  │  │ clusterId     │  │  │ groupId                   │  │
│  │ partitionCount│  │  │ controller    │  │  │ state                     │  │
│  │ partitions[]  │  │  │ brokers[]     │  │  │ coordinator               │  │
│  │ configs{}     │  │  └───────────────┘  │  │ partitionAssignor         │  │
│  └───────────────┘  │                     │  │ members[]                 │  │
│                     │                     │  └───────────────────────────┘  │
│  ┌───────────────┐  │  ┌───────────────┐  │                                 │
│  │ KafkaMessage  │  │  │ ProduceResult │  │                                 │
│  ├───────────────┤  │  ├───────────────┤  │                                 │
│  │ topic         │  │  │ topic         │  │                                 │
│  │ partition     │  │  │ partition     │  │                                 │
│  │ offset        │  │  │ offset        │  │                                 │
│  │ key           │  │  │ timestamp     │  │                                 │
│  │ value         │  │  │ success       │  │                                 │
│  │ timestamp     │  │  │ errorMessage  │  │                                 │
│  │ headers{}     │  │  └───────────────┘  │                                 │
│  └───────────────┘  │                     │                                 │
└─────────────────────┴─────────────────────┴─────────────────────────────────┘


┌─────────────────────────────────────────┐
│         <<Component>>                   │
│         McpMessageStatusFilter          │
├─────────────────────────────────────────┤
│ <<implements Filter>>                   │
├─────────────────────────────────────────┤
│ + doFilter(req, res, chain)             │
│   // Changes 200 → 202 for /mcp/message │
└─────────────────────────────────────────┘
```

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              MCP CLIENT (Claude Code)                        │
└───────────────────────────────────┬──────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │ SSE: GET /sse                 │
                    │ Messages: POST /mcp/message   │
                    ▼                               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT MCP SERVER (:9085)                       │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                    Spring AI MCP Server (SSE Transport)                │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    McpMessageStatusFilter                        │  │  │
│  │  │                    (200 → 202 for Claude compatibility)          │  │  │
│  │  └──────────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                      ToolCallbackProvider                              │  │
│  │                      (wraps KafkaToolProvider)                         │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                        KafkaToolProvider                               │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐   │  │
│  │  │ listTopics   │ │ describeTopic│ │ createTopic  │ │ deleteTopic  │   │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘   │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                    │  │
│  │  │produceMessage│ │consumeMessage│ │ peekMessages │                    │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘                    │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                    │  │
│  │  │listConsumer  │ │describeGroup │ │describeClust │                    │  │
│  │  │   Groups     │ │              │ │     er       │                    │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘                    │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                                    ▼                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                          KafkaService                                  │  │
│  │                                                                        │  │
│  │    ┌─────────────┐          ┌─────────────────┐                        │  │
│  │    │ KafkaAdmin  │          │ KafkaTemplate   │                        │  │
│  │    │(AdminClient)│          │ (Producer)      │                        │  │
│  │    └──────┬──────┘          └────────┬────────┘                        │  │
│  └───────────┼──────────────────────────┼────────────────────────────────┘  │
└──────────────┼──────────────────────────┼────────────────────────────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA BROKER (:9092)                                │
│                          (Docker - apache/kafka)                             │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Sequence Diagram - Tool Invocation

```
┌─────────┐     ┌─────────────┐     ┌────────────────┐     ┌────────────┐     ┌───────┐
│ Claude  │     │  MCP Server │     │ KafkaTool      │     │ Kafka      │     │ Kafka │
│ Code    │     │  (SSE)      │     │ Provider       │     │ Service    │     │ Broker│
└────┬────┘     └──────┬──────┘     └───────┬────────┘     └─────┬──────┘     └───┬───┘
     │                 │                    │                    │                │
     │  GET /sse       │                    │                    │                │
     │────────────────►│                    │                    │                │
     │  (SSE stream)   │                    │                    │                │
     │◄ ─ ─ ─ ─ ─ ─ ─ ─│                    │                    │                │
     │                 │                    │                    │                │
     │  POST /mcp/message                   │                    │                │
     │  {tool: "listTopics"}                │                    │                │
     │────────────────►│                    │                    │                │
     │                 │  listTopics()      │                    │                │
     │                 │───────────────────►│                    │                │
     │                 │                    │  listTopics()      │                │
     │                 │                    │───────────────────►│                │
     │                 │                    │                    │  AdminClient   │
     │                 │                    │                    │  .listTopics() │
     │                 │                    │                    │───────────────►│
     │                 │                    │                    │                │
     │                 │                    │                    │  topic names   │
     │                 │                    │                    │◄───────────────│
     │                 │                    │  List<String>      │                │
     │                 │                    │◄───────────────────│                │
     │                 │  JSON response     │                    │                │
     │                 │◄───────────────────│                    │                │
     │  SSE: data: {topics: [...]}         │                    │                │
     │◄────────────────│                    │                    │                │
     │                 │                    │                    │                │
```

## Layer Responsibilities

| Layer | Class | Responsibility |
|-------|-------|----------------|
| **Entry Point** | `KafkaMcpSbaiApplication` | Bootstrap app, register beans |
| **Filter** | `McpMessageStatusFilter` | HTTP response code adjustment |
| **MCP Tools** | `KafkaToolProvider` | Define tools, JSON serialization |
| **Service** | `KafkaService` | Kafka client operations |
| **DTOs** | `*Info`, `*Result` | Data structures for responses |

## External Dependencies

```
┌───────────────────────────────────────────┐
│           Spring Boot 4.0.2               │
│  ┌─────────────────────────────────────┐  │
│  │  spring-boot-starter-web            │  │
│  │  spring-boot-docker-compose         │  │
│  │  spring-kafka                       │  │
│  └─────────────────────────────────────┘  │
│  ┌─────────────────────────────────────┐  │
│  │  Spring AI 2.0.0-M2                 │  │
│  │  └── spring-ai-starter-mcp-server   │  │
│  └─────────────────────────────────────┘  │
│  ┌─────────────────────────────────────┐  │
│  │  Lombok (code generation)           │  │
│  └─────────────────────────────────────┘  │
└───────────────────────────────────────────┘
```
