# Building an MCP Server with Spring AI

This guide explains how to build a Model Context Protocol (MCP) server using Spring Boot and Spring AI, with a focus on the key classes, annotations, and the difference between `@Tool` and `@McpTool`.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Dependencies](#dependencies)
3. [Key Spring AI MCP Classes](#key-spring-ai-mcp-classes)
4. [Transport Options: STDIO vs SSE](#transport-options-stdio-vs-sse)
5. [@Tool vs @McpTool - The Key Difference](#tool-vs-mcptool---the-key-difference)
6. [Step-by-Step Implementation](#step-by-step-implementation)
7. [Configuration](#configuration)
8. [Registering with Claude Code](#registering-with-claude-code)

---

## Prerequisites

- Java 21+ (this project uses Java 25)
- Spring Boot 4.0+
- Spring AI 2.0.0-M2+
- Maven or Gradle

## Dependencies

Add the Spring AI MCP Server starter to your `pom.xml`:

```xml
<properties>
    <spring-ai.version>2.0.0-M2</spring-ai.version>
</properties>

<dependencies>
    <!-- MCP Server with WebMVC (for SSE transport) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Available MCP Server Starters

| Artifact | Transport | Use Case |
|----------|-----------|----------|
| `spring-ai-starter-mcp-server` | STDIO | CLI tools, local processes |
| `spring-ai-starter-mcp-server-webmvc` | SSE (blocking) | Web servers, REST APIs |
| `spring-ai-starter-mcp-server-webflux` | SSE (reactive) | Reactive web servers |

---

## Key Spring AI MCP Classes

### Core Annotations

```
org.springframework.ai.tool.annotation
├── @Tool                    # Generic tool annotation (Spring AI)
└── @ToolParam               # Parameter description for @Tool

org.springframework.ai.mcp.server
├── @McpTool                 # MCP-specific tool annotation
└── @McpToolParam            # Parameter description for @McpTool
```

### Tool Registration Classes

```
org.springframework.ai.tool
├── ToolCallback             # Interface for tool execution
├── ToolCallbackProvider     # Interface to provide tools to MCP
└── MethodToolCallbackProvider  # Wraps @Tool methods as callbacks

org.springframework.ai.mcp.server
├── McpServer                # The MCP server instance
├── McpSyncServer            # Synchronous MCP server
└── McpAsyncServer           # Asynchronous MCP server
```

### Auto-Configuration Classes

```
org.springframework.ai.mcp.server.autoconfigure
├── McpServerAutoConfiguration        # Core MCP server setup
├── McpServerProperties               # Configuration properties
└── McpServerWebMvcAutoConfiguration  # SSE transport setup
```

---

## Transport Options: STDIO vs SSE

### STDIO Transport

```
┌─────────────┐    stdin/stdout    ┌─────────────┐
│ MCP Client  │◄──────────────────►│ MCP Server  │
│ (Claude)    │                    │ (Java proc) │
└─────────────┘                    └─────────────┘
```

- Used for local CLI integrations
- Client spawns server as a subprocess
- Communication via stdin/stdout pipes

### SSE (Server-Sent Events) Transport

```
┌─────────────┐       HTTP         ┌─────────────┐
│ MCP Client  │───────────────────►│ MCP Server  │
│ (Claude)    │  GET /sse (stream) │ (Web app)   │
│             │◄───────────────────│             │
│             │  POST /mcp/message │             │
│             │───────────────────►│             │
└─────────────┘                    └─────────────┘
```

- Used for networked/remote servers
- Server runs as a standalone web application
- Bidirectional via SSE stream + HTTP POST

---

## @Tool vs @McpTool - The Key Difference

This is where most confusion arises. Here's the definitive explanation:

### @Tool (Generic Spring AI)

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class MyToolProvider {

    @Tool(description = "Adds two numbers together")
    public int add(
            @ToolParam(description = "First number") int a,
            @ToolParam(description = "Second number") int b) {
        return a + b;
    }
}
```

**Characteristics:**
- Part of core Spring AI (`org.springframework.ai.tool.annotation`)
- **NOT automatically registered** with MCP server
- Requires explicit `ToolCallbackProvider` bean
- Can be used with both MCP AND `ChatClient` (LLM function calling)

**Registration Required:**
```java
@Bean
public ToolCallbackProvider myTools(MyToolProvider provider) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(provider)
            .build();
}
```

### @McpTool (MCP-Native)

```java
import org.springframework.ai.mcp.server.McpTool;
import org.springframework.ai.mcp.server.McpToolParam;

@Component
public class MyToolProvider {

    @McpTool(description = "Adds two numbers together")
    public int add(
            @McpToolParam(description = "First number") int a,
            @McpToolParam(description = "Second number") int b) {
        return a + b;
    }
}
```

**Characteristics:**
- Part of MCP server module (`org.springframework.ai.mcp.server`)
- **Automatically registered** with MCP server
- No additional bean configuration needed
- **Only works with MCP**, not usable with `ChatClient`

### Comparison Table

| Feature | @Tool | @McpTool |
|---------|-------|----------|
| Package | `spring-ai-core` | `spring-ai-mcp-server` |
| Auto-registered | No | Yes |
| Requires bean | Yes (`ToolCallbackProvider`) | No |
| Works with MCP | Yes (via bridge) | Yes (native) |
| Works with ChatClient | Yes | No |
| Flexibility | High | MCP-only |
| Boilerplate | More | Less |

### Which Should You Choose?

```
┌─────────────────────────────────────────────────────────────┐
│                    Decision Tree                            │
└─────────────────────────────────────────────────────────────┘

Are you building tools for MCP only?
    │
    ├── YES ──► Do you want minimal boilerplate?
    │               │
    │               ├── YES ──► Use @McpTool
    │               │
    │               └── NO ───► Either works, @Tool is fine
    │
    └── NO ───► Will tools be used with ChatClient too?
                    │
                    ├── YES ──► Use @Tool (required)
                    │
                    └── MAYBE ► Use @Tool (more flexible)
```

**Recommendation:** For dedicated MCP servers, `@McpTool` is cleaner. For reusable tool libraries, `@Tool` provides flexibility.

---

## Step-by-Step Implementation

### Step 1: Create the Tool Provider

Using `@Tool` approach (this project's choice):

```java
package com.example.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyToolProvider {

    @Tool(description = "Get the current weather for a city")
    public String getWeather(
            @ToolParam(description = "City name") String city) {
        // Implementation
        return "Sunny, 25°C in " + city;
    }

    @Tool(description = "Search for products by keyword")
    public String searchProducts(
            @ToolParam(description = "Search keyword") String keyword,
            @ToolParam(description = "Maximum results") Integer limit) {
        // Implementation
        return "Found products for: " + keyword;
    }
}
```

### Step 2: Register Tools (only for @Tool)

```java
package com.example;

import com.example.tool.MyToolProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider myTools(MyToolProvider toolProvider) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolProvider)
                .build();
    }
}
```

### Step 3: Configure Application Properties

```properties
# Server
server.port=9085
spring.application.name=my-mcp-server

# MCP Server Configuration
spring.ai.mcp.server.transport=sse
spring.ai.mcp.server.name=my-mcp-server
spring.ai.mcp.server.version=1.0.0

# SSE Endpoints
spring.ai.mcp.server.sse-endpoint=/sse
spring.ai.mcp.server.sse-message-endpoint=/mcp/message
```

---

## Configuration

### All MCP Server Properties

| Property | Description | Default |
|----------|-------------|---------|
| `spring.ai.mcp.server.transport` | Transport type (`stdio`, `sse`) | `stdio` |
| `spring.ai.mcp.server.name` | Server name in MCP metadata | Application name |
| `spring.ai.mcp.server.version` | Server version in MCP metadata | `1.0.0` |
| `spring.ai.mcp.server.sse-endpoint` | SSE stream endpoint | `/sse` |
| `spring.ai.mcp.server.sse-message-endpoint` | Message POST endpoint | `/mcp/message` |

### Claude Code Compatibility Note

Some MCP clients (including Claude Code) expect HTTP 202 (Accepted) for message endpoints, but Spring AI returns 200 (OK). Add this filter if needed:

```java
@Component
public class McpMessageStatusFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if ("POST".equals(httpRequest.getMethod()) &&
            "/mcp/message".equals(httpRequest.getRequestURI())) {

            chain.doFilter(request, new HttpServletResponseWrapper(
                    (HttpServletResponse) response) {
                @Override
                public void setStatus(int sc) {
                    super.setStatus(sc == 200 ? 202 : sc);
                }
            });
        } else {
            chain.doFilter(request, response);
        }
    }
}
```

---

## Registering with Claude Code

Once your MCP server is running:

```bash
# Register globally (all projects)
claude mcp add my-mcp-server --transport sse http://localhost:9085/sse --scope user

# Register for current project only
claude mcp add my-mcp-server --transport sse http://localhost:9085/sse --scope project

# Verify registration
claude mcp list

# Remove if needed
claude mcp remove my-mcp-server
```

---

## Summary

1. **Choose your starter** based on transport needs (STDIO vs SSE)
2. **Choose your annotation** based on flexibility needs:
   - `@McpTool` for MCP-only, minimal setup
   - `@Tool` for reusable tools, requires `ToolCallbackProvider` bean
3. **Configure endpoints** in `application.properties`
4. **Register with Claude Code** using `claude mcp add`

The Spring AI MCP server auto-configuration handles most of the heavy lifting - you just need to define your tools and wire them up correctly.
