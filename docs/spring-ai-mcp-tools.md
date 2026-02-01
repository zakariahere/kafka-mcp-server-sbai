# Spring AI MCP Tools: `@Tool` vs `@McpTool`

This document explains the difference between the two approaches for exposing tools via MCP in Spring AI.

## Overview

Spring AI 2.0 provides two ways to expose tools via the Model Context Protocol (MCP):

| Approach | Annotation | Registration | Best For |
|----------|------------|--------------|----------|
| **Generic** | `@Tool` + `@ToolParam` | Requires `ToolCallbackProvider` bean | Reusable tools (MCP + ChatClient) |
| **MCP-Native** | `@McpTool` + `@McpToolParam` | Auto-registered by Spring AI MCP | MCP-only servers |

## Approach 1: `@Tool` (Generic Spring AI Tools)

This is the approach used in this project.

### Annotations
```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class KafkaToolProvider {

    @Tool(description = "List all Kafka topics in the cluster")
    public String listTopics() {
        // implementation
    }

    @Tool(description = "Describe a specific topic")
    public String describeTopic(
            @ToolParam(description = "The topic name") String topicName) {
        // implementation
    }
}
```

### Registration (Required)
You **must** create a `ToolCallbackProvider` bean to expose `@Tool` methods via MCP:

```java
@SpringBootApplication
public class KafkaMcpSbaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaMcpSbaiApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider kafkaTools(KafkaToolProvider kafkaToolProvider) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(kafkaToolProvider)
                .build();
    }
}
```

### Pros
- **Reusable**: Same tools work with MCP and Spring AI's `ChatClient`
- **Flexible**: Can be used in multiple contexts
- **Standard**: Part of core Spring AI

### Cons
- Requires explicit bean registration
- Slightly more boilerplate

## Approach 2: `@McpTool` (MCP-Native Tools)

This approach is specifically designed for MCP servers.

### Annotations
```java
import org.springframework.ai.mcp.server.McpTool;
import org.springframework.ai.mcp.server.McpToolParam;

@Component
public class KafkaToolProvider {

    @McpTool(description = "List all Kafka topics in the cluster")
    public String listTopics() {
        // implementation
    }

    @McpTool(description = "Describe a specific topic")
    public String describeTopic(
            @McpToolParam(description = "The topic name") String topicName) {
        // implementation
    }
}
```

### Registration
**Auto-registered** - No additional bean configuration needed. Spring AI MCP server automatically discovers and registers `@McpTool` methods.

### Pros
- Less boilerplate (no `ToolCallbackProvider` bean needed)
- Cleaner for MCP-only use cases
- Auto-discovery

### Cons
- **MCP-only**: Cannot be reused with `ChatClient`
- Less flexible if you need tools in multiple contexts

## Which Should You Use?

### Use `@Tool` when:
- You want tools usable with both MCP and `ChatClient`
- You're building a library of reusable tools
- You need maximum flexibility

### Use `@McpTool` when:
- You're building a dedicated MCP server
- You don't need `ChatClient` integration
- You prefer minimal configuration

## This Project's Approach

This project uses `@Tool` with explicit `ToolCallbackProvider` registration because:

1. It provides flexibility for future use with `ChatClient`
2. It follows the more general Spring AI pattern
3. The explicit registration makes the tool exposure clear and intentional

## Configuration

Both approaches require the same MCP server configuration in `application.properties`:

```properties
# MCP Server Configuration (SSE transport)
spring.ai.mcp.server.transport=sse
spring.ai.mcp.server.name=kafka-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.sse-message-endpoint=/mcp/message
spring.ai.mcp.server.sse-endpoint=/sse
server.port=9085
```

## References

- [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp-server.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
