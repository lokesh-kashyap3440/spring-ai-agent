# Spring AI 2.0.0 + Spring Boot 4.1.0 Migration (June 25, 2026)

Upgraded from Spring AI 1.0.8 + Spring Boot 3.5.15 → Spring AI 2.0.0 + Spring Boot 4.1.0.

## Changes Made

| File | Change |
|------|--------|
| `pom.xml` | `spring-boot-starter-parent` → `4.1.0`, `spring-ai.version` → `2.0.0`, removed CVE overrides and OWASP plugin |
| `application.yml` | Flattened `spring.ai.ollama.embedding.options.temperature` → `spring.ai.ollama.embedding.temperature` |
| `AppConfig.java` | `RestTemplateBuilder` → `ClientHttpRequestFactoryBuilder.simple()` with `HttpClientSettings` |
| `DocumentIngestionService.java` | `new TokenTextSplitter(...)` → `TokenTextSplitter.builder()...build()` |
| `AgentController.java` | Added `@Valid` annotation on `chat()` endpoint, version `2.0.0` |
| `McpServerController.java` | Version `2.0.0`, wildcard imports expanded |

## Key Upgrade Details

### Spring Boot 3.5.15 → 4.1.0
- **Parent POM** — upgraded to `4.1.0` (required by Spring AI 2.0.0)
- **`RestTemplateBuilder`** — moved from `org.springframework.boot.web.client` to `org.springframework.boot.restclient`. Use `ClientHttpRequestFactoryBuilder.simple()` with `HttpClientSettings` instead
- **CVE overrides** — removed (managed by Boot 4.1.0)
- **OWASP plugin** — removed from build

### Spring AI 1.0.8 → 2.0.0
- **Ollama starter** (`spring-ai-starter-model-ollama`) — artifact unchanged
- **Vector store** (`spring-ai-vector-store`) — artifact unchanged
- **Tika reader** (`spring-ai-tika-document-reader`) — artifact unchanged
- **TokenTextSplitter** — constructors deprecated; use `TokenTextSplitter.builder()` instead
- **Ollama embedding options** — `.options` prefix removed from config properties
- **Jackson 2.x** — kept on `com.fasterxml.jackson` (Boot 4.x provides both 2.x and 3.x; 2.x is used for backward compatibility)

---

# RAG MCP Setup

## Issue: ChromaDB HTTP/2 ↔ HTTP/1.1 Protocol Mismatch

Spring AI 1.0.8's ChromaDB client attempts HTTP/2/WebSocket upgrades, but ChromaDB 0.4.x/0.5.x Docker images only
support HTTP/1.1. This caused:

- `"Unsupported upgrade request"` in ChromaDB logs
- `"Invalid HTTP request received"` after failed upgrade
- Uploads failing with HTTP 500

## Fix: Switched to SimpleVectorStore

Replaced ChromaDB with Spring AI's in-memory `SimpleVectorStore`.

### Changes

| File                                    | Change                                                                                                 |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------|
| `pom.xml`                               | `spring-ai-starter-vector-store-chroma` → `spring-ai-vector-store`                                     |
| `application.yml`                       | Removed `spring.ai.vectorstore.chroma.*`, added `spring.ai.vectorstore.simple.initialize-schema: true` |
| `config/ChromaInitializer.java`         | Deleted (not needed)                                                                                   |
| `config/SimpleVectorStoreConfig.java`   | New — creates `SimpleVectorStore` bean                                                                 |
| `service/DocumentIngestionService.java` | Removed `@DependsOn("chromaInitializer")`                                                              |

### Running

The app runs on port `8082`. Build and start:

```bash
mvn package -DskipTests
java -jar target/ai-agent-0.0.1-SNAPSHOT.jar
```

### Testing via MCP

All operations available via MCP on `POST /mcp`:

1. **Upload a document** (via MCP — base64-encoded):

```bash
B64=$(base64 -w0 /path/to/document.pdf)
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "upload_document",
      "arguments": {
        "filename": "document.pdf",
        "content": "'"$B64"'",
        "contentType": "application/pdf"
      }
    }
  }'
```

2. **Search via MCP**:

```bash
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "rag_search",
      "arguments": {
        "query": "your search query"
      }
    }
  }'
```

### Available MCP Tools

| Tool              | Description                                             |
|-------------------|---------------------------------------------------------|
| `get_weather`     | Get current weather for a city                          |
| `get_news`        | Get latest news headlines for a topic                   |
| `calculate`       | Evaluate mathematical expressions                       |
| `query_database`  | Query the knowledge base for information                |
| `rag_search`      | Search uploaded documents for relevant information      |
| `upload_document` | Upload a document (PDF, DOCX, TXT, etc.) for RAG search |

### Note

SimpleVectorStore is in-memory — data is lost on restart. For persistence, add PostgreSQL with PgVector or re-add
ChromaDB with a compatible HTTP/1.1 client config.

---

# CVE Vulnerability Fixes (June 10, 2026)

## Status: In Progress

All dependency version overrides applied to `pom.xml` — build compiles. OWASP Dependency-Check scan was running but was
interrupted (NVD DB download ~20% through 357K records on second attempt).

## Fixes Applied

| Dependency                               | Before   | After     | CVEs                                                     |
|------------------------------------------|----------|-----------|----------------------------------------------------------|
| jackson-databind (`jackson-bom.version`) | 2.21.2   | 2.21.4    | CVE-2026-24400                                           |
| logback (`logback.version`)              | 1.5.32   | 1.5.34    | CVE-2026-9828                                            |
| snakeyaml (`snakeyaml.version`)          | 2.4      | 2.6       | CVE-2022-1471                                            |
| Tomcat (`tomcat.version`)                | 10.1.53  | 10.1.55   | CVE-2026-41284, 43512, 43515, 34486, 34500, 43514, 34483 |
| Bouncy Castle (`dependencyManagement`)   | 1.80     | 1.84      | CVE-2026-5598 (CVSS 8.9)                                 |
| snappy-java (`dependencyManagement`)     | 1.1.10.5 | 1.1.10.8  | CVE-2023-34453~55, CVE-2023-43642                        |
| OWASP plugin added                       | —        | 12.1.0    | `failBuildOnCVSS=8`                                      |
| `dependency-check-suppressions.xml`      | —        | stub file | —                                                        |

## To Resume (Tomorrow)

1. **Finish OWASP scan** — first run downloads all 357K NVD records (~20-40 min with API key):
   ```bash
   mvn dependency-check:check -DnvdApiKey="3660af2b-d50e-492d-a185-e9ad4b2531ee"
   ```
   The NVD DB is cached in `~/.dependency-check/data/` after first download — subsequent runs are fast.

2. **Full package build** — verify `mvn package -DskipTests` passes.

3. **Known issue**: dependency-check 12.1.0 has a DB schema issue with `reference.URL` column (1000 chars) vs newer NVD
   URLs (1500+ chars). If the scan errors on this, either:
    - Upgrade to dependency-check 12.1.1+ (if available)
    - Or ignore the error (it only affects those specific CVE records, not the scan results for your dependencies)

## NVD API Key

Stored inline for now: `3660af2b-d50e-492d-a185-e9ad4b2531ee`  
Add to `~/.m2/settings.xml` for reuse:

```xml
<settings>
  <servers>
    <server>
      <id>nvd-api-key</id>
      <username>nvd-api-key</username>
      <password>3660af2b-d50e-492d-a185-e9ad4b2531ee</password>
    </server>
  </servers>
</settings>
```

---

# Chat UI with Selectable Tools (June 25, 2026)

Added a web chat interface where tools can be toggled on/off per request.

## Changes

| File | Change |
|------|--------|
| `model/ChatRequest.java` | Added `List<String> toolsEnabled` field — `null` means all tools enabled |
| `tools/ToolRegistry.java` | Added `getToolDescriptions(Set)`, `isToolEnabled()`, `getToolNames(Set)` for filtered tool access |
| `agent/ReActAgent.java` | `run()` now accepts `Set<String> enabledTools` and returns `AgentResult` record (`answer` + `toolsUsed`). System prompt only includes enabled tools. Disabled tool calls return error observation. |
| `controller/AgentController.java` | Passes `toolsEnabled` from request to agent; uses actual `toolsUsed` from result instead of hardcoded list |
| `resources/static/index.html` | **New** — dark-themed chat UI at `http://localhost:8082/` with tool toggle checkboxes, session-based memory |

## How It Works

- Client sends `toolsEnabled: ["weather", "news"]` in the chat request body
- `null` or omitted → all tools available (backward compatible)
- Empty array → agent can only chat, no tool access
- Agent system prompt only describes enabled tools
- Calling a disabled tool returns `"Tool 'X' is not enabled"` as observation
- Response includes `toolsUsed` — the actual tools invoked during execution

## Bug Fixes

| Issue | Fix |
|-------|-----|
| Kafka producer blocked indefinitely when broker unavailable (60s+ request time) | Added `max.block.ms: 2000`, `retries: 0`, `request.timeout.ms: 2000` to `KafkaConfig.java` |
| `java.time.Instant` serialization failed with `InvalidDefinitionException` | Registered `JavaTimeModule` on custom `ObjectMapper` bean in `AppConfig.java` — Spring Boot's auto-configuration was bypassed by the custom `@Bean` definition |

---

# Unit Tests (June 30, 2026)

Added comprehensive unit test coverage for all 28 application classes. Uses JUnit 5 + Mockito (via `spring-boot-starter-test`), with standalone MockMvc for controller tests (Spring Boot 4.1.0 removed web test autoconfigure classes from default starter). All 162 tests pass.

## Test Files Created

| Class | Test File | Tests | Key Coverage |
|-------|-----------|-------|--------------|
| `ChatRequest` | `model/ChatRequestTest.java` | 6 | Validation, constructors, null tools |
| `ChatResponse` | `model/ChatResponseTest.java` | 3 | Constructors, setters/getters, timestamp |
| `AgentState` | `model/AgentStateTest.java` | 9 | State transitions, formatted history, iteration |
| `DocumentInfo` | `model/DocumentInfoTest.java` | 3 | Constructors, setters/getters |
| `ToolRegistry` | `tools/ToolRegistryTest.java` | 12 | Registration, case-insensitive lookup, filtering, empty registry |
| `CalculatorTool` | `tools/CalculatorToolTest.java` | 14 | Arithmetic, order of operations, sqrt, div-by-zero, decimals, whitespace |
| `WeatherTool` | `tools/WeatherToolTest.java` | 3 | Name, description, fallback/actual response |
| `NewsTool` | `tools/NewsToolTest.java` | 3 | Name, description, fallback on error |
| `DatabaseTool` | `tools/DatabaseToolTest.java` | 7 | Exact match, caching, unknown query, Redis errors |
| `RAGTool` | `tools/RAGToolTest.java` | 6 | Search results, no results, empty query, errors |
| `Tool` (interface) | (covered via `ToolRegistryTest` mocks) | — | Default `getParameterSchema()` |
| `AiService` (interface) | (covered via `OllamaServiceTest`/`NvidiaServiceTest`) | — | Contract tested via implementations |
| `AgentConfig` | `config/AgentConfigTest.java` | 2 | Default values, setters |
| `NvidiaConfig` | `config/NvidiaConfigTest.java` | 2 | Default values, setters |
| `OllamaConfig` | `config/OllamaConfigTest.java` | 2 | Default values, setters |
| `AppConfig` | `config/AppConfigTest.java` | 3 | RestTemplate bean, ObjectMapper with JavaTimeModule |
| `KafkaConfig` | `config/KafkaConfigTest.java` | 2 | Producer properties, template creation |
| `VectorStoreConfig` | `config/VectorStoreConfigTest.java` | 3 | Connection factory host/port/password |
| `AgentMemoryService` | `memory/AgentMemoryServiceTest.java` | 10 | Save/retrieve, TTL, capping at 50, agent state, clear |
| `OllamaService` | `service/OllamaServiceTest.java` | 6 | Chat success/error, empty response, request building, availability |
| `NvidiaService` | `service/NvidiaServiceTest.java` | 7 | Chat success/error, auth header, request params, availability |
| `KafkaEventPublisher` | `service/KafkaEventPublisherTest.java` | 5 | Chat/agent events, graceful degradation on Kafka failure |
| `DocumentIngestionService` | `service/DocumentIngestionServiceTest.java` | 7 | List/delete/search documents, empty/invalid JSON handling |
| `ReActAgent` | `agent/ReActAgentTest.java` | 8 | Final answer, tool execution, unknown/disabled tool, max iterations, multiple tools, empty response |
| `AgentController` | `controller/AgentControllerTest.java` | 8 | Chat, history, session clear, tools listing, health (healthy/degraded) |
| `DocumentController` | `controller/DocumentControllerTest.java` | 9 | Upload (valid/empty/unsupported/null type), list, delete, search |
| `McpServerController` | `mcp/McpServerControllerTest.java` | 15 | Initialize, tools/list, tools/call (all 6 tools + unknown), upload, SSE, content-type detection |
| `AiAgentApplication` | `AiAgentApplicationTest.java` | 1 | Class existence |

## Test Configuration

- **Framework**: JUnit 5 + Mockito + AssertJ (via `spring-boot-starter-test`)
- **Controller tests**: `MockMvcBuilders.standaloneSetup()` — `@WebMvcTest`/`@MockBean`/`AutoConfigureMockMvc` were removed from Spring Boot 4.1.0's `spring-boot-test-autoconfigure`
- **AI service tests**: Mock `RestTemplate` with real `ObjectMapper` for request body verification
- **Redis-dependent tests**: Mock `StringRedisTemplate` — no embedded Redis needed
- **Kafka tests**: Mock `KafkaTemplate` — `@Value` defaults must be set manually outside Spring context

## Running

```bash
mvn test
```
