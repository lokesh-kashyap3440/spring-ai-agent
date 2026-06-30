# Spring AI Agent — Developer Guide

> Line-by-line documentation for every source file in the application.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [pom.xml](#2-pomxml)
3. [application.yml](#3-applicationyml)
4. [AiAgentApplication.java](#4-aiagentapplicationjava)
5. [Config Layer](#5-config-layer)
    - [AppConfig.java](#51-appconfigjava)
    - [OllamaConfig.java](#52-ollamaconfigjava)
    - [AgentConfig.java](#53-agentconfigjava)
    - [SimpleVectorStoreConfig.java](#54-simplevectorstoreconfigjava)
6. [Model Layer](#6-model-layer)
    - [ChatRequest.java](#61-chatrequestjava)
    - [ChatResponse.java](#62-chatresponsejava)
    - [AgentState.java](#63-agentstatejava)
    - [DocumentInfo.java](#64-documentinfojava)
7. [Controller Layer](#7-controller-layer)
    - [AgentController.java](#71-agentcontrollerjava)
    - [DocumentController.java](#72-documentcontrollerjava)
    - [McpServerController.java](#73-mcpservercontrollerjava)
8. [Agent Layer](#8-agent-layer)
    - [ReActAgent.java](#81-reactagentjava)
9. [Service Layer](#9-service-layer)
    - [OllamaService.java](#91-ollamaservicejava)
    - [DocumentIngestionService.java](#92-documentingestionservicejava)
    - [KafkaEventPublisher.java](#93-kafkaeventpublisherjava)
10. [Memory Layer](#10-memory-layer)
    - [AgentMemoryService.java](#101-agentmemoryservicejava)
11. [Tool Layer](#11-tool-layer)
    - [Tool.java](#111-tooljava)
    - [ToolRegistry.java](#112-toolregistryjava)
    - [WeatherTool.java](#113-weathertooljava)
    - [NewsTool.java](#114-newstooljava)
    - [CalculatorTool.java](#115-calculatortooljava)
    - [DatabaseTool.java](#116-databasetooljava)
    - [RAGTool.java](#117-ragtooljava)
12. [Architecture Diagram](#12-architecture-diagram)

---

## 1. Project Overview

This is a **ReAct-style AI Agent** built with Spring Boot 3.5.13 and Java 21. It uses a locally-running Ollama LLM (
`qwen3.5:4b`) for reasoning, Redis for conversation memory, Kafka for event streaming, and an in-memory vector store for
RAG (Retrieval-Augmented Generation).

**Key capabilities:**

- Chat with an AI agent that reasons and calls tools iteratively
- Upload documents (PDF, DOCX, TXT, Markdown, HTML) for semantic search
- Expose all tools via MCP (Model Context Protocol) for integration with AI clients
- Full REST API for agent interaction and document management

**Package structure:**

```
com.example.aiagent/
├── AiAgentApplication.java          -- Spring Boot entry point
├── agent/ReActAgent.java            -- Core ReAct (Reason+Act) agent loop
├── config/                           -- Configuration classes
├── controller/                       -- REST controllers
├── mcp/McpServerController.java      -- MCP protocol server
├── memory/AgentMemoryService.java    -- Redis-backed conversation memory
├── model/                            -- DTOs and state objects
├── service/                          -- Business services
└── tools/                            -- Tool interface + implementations
```

---

## 2. pom.xml

**File:** `pom.xml`

The Maven build configuration.

```xml
Line  7-12:  Parent POM — Spring Boot 3.5.13 starter parent. Inherits dependency
             management, plugin configuration, and default property overrides
             from the Spring Boot parent BOM.

Line 14-18:  Project coordinates — com.example:ai-agent:0.0.1-SNAPSHOT.
             This is the artifact ID used in the build output JAR.

Line 20-28:  Property overrides — these replace versions managed by the
             Spring Boot parent to patch CVE vulnerabilities:
             - java.version = 21          — Target JDK 21
             - spring-ai.version = 1.0.8  — Spring AI BOM version
             - jackson-bom.version = 2.21.4  — Fixes CVE-2026-24400
             - logback.version = 1.5.34       — Fixes CVE-2026-9828
             - snakeyaml.version = 2.6        — Fixes CVE-2022-1471
             - tomcat.version = 10.1.55        — Fixes multiple CVEs
```

### Dependency Management (Lines 30-67)

```xml
Line 32-38:  spring-ai-bom 1.0.8 imported as a POM-scoped BOM.
             This manages versions for all Spring AI dependencies so we
             don't have to specify versions individually on each one.

Line 39-59:  Bouncy Castle 1.84 — overrides the transitive version
             pulled in by other dependencies. Fixes CVE-2026-5598
             (timing side-channel attack, CVSS 8.9).
             Four artifacts: bcprov, bcpkix, bcutil, bcjmail.

Line 60-65:  snappy-java 1.1.10.8 — overrides the transitive version.
             Fixes CVE-2023-34453, CVE-2023-34454, CVE-2023-34455,
             and CVE-2023-43642 (uncontrolled resource consumption).
```

### Dependencies (Lines 69-120)

```xml
Line 71-74:  spring-ai-starter-model-ollama — Auto-configures the Ollama
             chat model and embedding model clients. Version managed by BOM.
             Provides ChatModel and EmbeddingModel beans.

Line 77-80:  spring-ai-vector-store — Provides SimpleVectorStore (in-memory).
             Replaces the original spring-ai-starter-vector-store-chroma
             which had HTTP/2 compatibility issues with ChromaDB's
             HTTP/1.1-only server.

Line 83-86:  spring-ai-tika-document-reader — Parses documents (PDF, DOCX,
             TXT, Markdown, HTML) into Spring AI Document objects using
             Apache Tika under the hood.

Line 88-90:  spring-boot-starter-web — Embedded Tomcat, Spring MVC,
             REST controller support, Jackson JSON.

Line 92-94:  spring-boot-starter-data-redis — Auto-configures
             StringRedisTemplate and Redis connection. Used for session
             memory, document index, and caching.

Line 96-98:  spring-kafka — KafkaTemplate for publishing events.
             Consumer config is present but no consumers are implemented.
             Fire-and-forget event publishing only.

Line 99-102: spring-boot-starter-validation — Bean validation API.
             Used for @NotBlank on ChatRequest.message.

Line 104-107: jackson-databind — Explicit dependency (version managed
              by the overridden jackson-bom.version). Used by OllamaService
              and McpServerController for JSON construction/parsing.

Line 110-113: springdoc-openapi-starter-webmvc-ui 2.8.17 — Swagger/OpenAPI
              UI available at /swagger-ui.html.

Line 116-119: spring-boot-starter-test (test scope) — JUnit, Mockito,
              Spring Test. Note: no test files exist in src/test/.
```

### Build Plugins (Lines 122-154)

```xml
Line 124-130: spring-boot-maven-plugin — Packages an executable fat JAR
              with mainClass = com.example.aiagent.AiAgentApplication.

Line 132-152: dependency-check-maven 12.1.0 — OWASP dependency vulnerability
              scanner. Configured to:
              - failBuildOnCVSS = 8  (fail if any dep has CVSS >= 8)
              - Output: HTML + JSON reports
              - Uses suppression file: dependency-check-suppressions.xml
              - Binds to the "check" goal (runs during verify phase)
```

---

## 3. application.yml

**File:** `src/main/resources/application.yml`

The central configuration file. Binds to Spring Boot auto-configuration and custom `@ConfigurationProperties` classes.

```yaml
Line 1-2:    server.port = 8082 — The app runs on port 8082 to avoid
             conflicts with common dev services on 8080/8081.

Line 4-6:    spring.application.name = ai-agent — Used as the
             application identifier in logs and Kafka client IDs.

Line 8-19:   Kafka configuration —
             - bootstrap-servers: localhost:9093 (external listener
               on the Docker Kafka container, KRaft mode)
             - Producer: String keys, JSON values (auto-serialized)
             - Consumer: String keys, JSON values with trusted package
               "com.example.aiagent.*"
             - Note: Consumer is configured but no @KafkaListener exists

Line 20-24:  Redis configuration —
             - host: localhost
             - port: 6380 (mapped from Docker container's 6379)
             - timeout: 5000ms

Line 26-29:  Multipart config —
             - max-file-size: 1024MB
             - max-request-size: 1024MB
             Allows uploading large documents for RAG ingestion.

Line 31-40:  Spring AI Ollama configuration —
             - base-url: http://localhost:11434 (Ollama server)
             - embedding.model: nomic-embed-text — The embedding model
               used by SimpleVectorStore to generate vector representations.
             - embedding.options.temperature: 0.0 — Deterministic embeddings
               (no randomness in embedding generation).
             - vectorstore.simple.initialize-schema: true — Tells
               SimpleVectorStore to initialize its internal schema.

Line 42-58:  Custom application properties —
             - app.ollama.* — Binds to OllamaConfig (@ConfigurationProperties)
               - base-url: http://localhost:11434
               - model: qwen3.5:4b — The chat model used by OllamaService
               - timeout: 120 seconds
               - max-tokens: 2048
             - app.agent.* — Binds to AgentConfig
               - max-iterations: 10 — Max ReAct loop iterations
               - memory-size: 20 — Configured but not used (code trims at 50)
             - app.kafka.topics.* — Binds to KafkaEventPublisher @Value fields
               - events: ai-agent-events
               - chat: ai-agent-chat
             - app.mcp.server.* — Referenced by McpServerController
               - name: ai-agent-tools
               - version: 1.0.0
```

**Design note:** The `spring.ai.ollama` section configures Spring AI's auto-configured `EmbeddingModel` bean (used by
SimpleVectorStore). The `app.ollama` section configures the custom `OllamaService` (used by ReActAgent for chat). These
are **two separate Ollama integrations** — one via Spring AI's abstraction, one via direct HTTP.

---

## 4. AiAgentApplication.java

**File:** `src/main/java/com/example/aiagent/AiAgentApplication.java`

```java
Line 1:    package com.example.aiagent;    -- Root package for the app

Line 3-4:  Imports the Spring Boot entry point annotations.

Line 6:    @SpringBootApplication -- Composite annotation that enables:
           - @Configuration (register beans)
           - @EnableAutoConfiguration (auto-configure Spring Boot defaults
             + Spring AI + Redis + Kafka starters)
           - @ComponentScan (scan com.example.aiagent and sub-packages
             for @Component, @Service, @Controller, etc.)

Line 7-11:  The main() method. SpringApplication.run() bootstraps the
           Spring context, starts the embedded Tomcat on port 8082,
           connects to Redis and Kafka, and initializes all beans.
```

This is a minimal entry point — all configuration is done via `@Configuration` classes and `application.yml`.

---

## 5. Config Layer

### 5.1 AppConfig.java

**File:** `src/main/java/com/example/aiagent/config/AppConfig.java`

Provides the shared `RestTemplate` bean used by `OllamaService`.

```java
Line 10:   @Configuration -- Marks this as a Spring configuration class
           that declares @Bean methods.

Line 13:   @Bean -- Registers a RestTemplate in the Spring context.
           Injected into OllamaService (and available for any other
           component that needs HTTP client access).

Line 14-18: Builds the RestTemplate with:
           - connectTimeout: 10 seconds — Max time to wait for a TCP
             connection to be established.
           - readTimeout: 120 seconds — Max time to wait for a response
             body after connecting. Set long because LLM inference
             (especially with large models) can take over a minute.

Line 16:   Duration.ofSeconds(10) -- 10s connect timeout prevents
           hanging on unreachable hosts.

Line 17:   Duration.ofSeconds(120) -- 120s read timeout matches the
           OllamaConfig.timeout value.
```

**Note:** `WeatherTool` and `NewsTool` create their own `new RestTemplate()` instead of using this bean — they bypass
the timeout configuration.

### 5.2 OllamaConfig.java

**File:** `src/main/java/com/example/aiagent/config/OllamaConfig.java`

Type-safe configuration properties for the custom Ollama HTTP client.

```java
Line 6:    @Configuration -- Makes this a Spring bean.
Line 7:    @ConfigurationProperties(prefix = "app.ollama") -- Binds
           properties from application.yml with prefix "app.ollama"
           to the fields below. Spring calls setters after construction.

Line 10:   baseUrl = "http://localhost:11434" -- Default Ollama server URL.
           Can be overridden via app.ollama.base-url in yml.

Line 11:   model = "qwen3.5:4b" -- The LLM model name sent in API
           requests to Ollama. "qwen3.5:4b" is a 4-billion parameter
           Qwen model suitable for local inference.

Line 12:   timeout = 120 -- Request timeout in seconds. Matches the
           RestTemplate readTimeout.

Line 13:   maxTokens = 2048 -- Maximum output tokens. Currently NOT
           sent to the Ollama API (only stored in config). Could be
           added as the "options.num_predict" field in the request.

Line 15-22: Standard getters/setters. Required by
           @ConfigurationProperties for Spring to bind values.
```

### 5.3 AgentConfig.java

**File:** `src/main/java/com/example/aiagent/config/AgentConfig.java`

Configuration properties for the ReAct agent loop.

```java
Line 7:    @ConfigurationProperties(prefix = "app.agent") -- Binds
           properties from application.yml with prefix "app.agent".

Line 10:   maxIterations = 10 -- Maximum number of Reason-Act cycles
           the ReActAgent will perform before giving up. Each iteration
           sends a prompt to the LLM and processes the response.

Line 11:   memorySize = 20 -- Intended to control conversation memory
           window size, but currently NOT enforced. The code in
           AgentMemoryService actually trims at 50 messages.
```

### 5.4 SimpleVectorStoreConfig.java

**File:** `src/main/java/com/example/aiagent/config/SimpleVectorStoreConfig.java`

Creates the in-memory vector store bean for RAG.

```java
Line 8:    @Configuration -- Declares bean definitions for the vector store.

Line 11-13: @Bean public SimpleVectorStore simpleVectorStore(EmbeddingModel)
           Creates a SimpleVectorStore using Spring AI's auto-configured
           EmbeddingModel (Ollama's nomic-embed-text, configured in yml).

           The builder pattern: SimpleVectorStore.builder(embeddingModel).build()
           - The embeddingModel is used by the store to:
             1. Convert document chunks into vectors during ingestion
             2. Convert search queries into vectors during similarity search

           This replaced ChromaDB after an HTTP/2 vs HTTP/1.1 protocol
           mismatch caused uploads to fail. SimpleVectorStore is in-memory
           only — all vector data is lost on application restart.
```

---

## 6. Model Layer

### 6.1 ChatRequest.java

**File:** `src/main/java/com/example/aiagent/model/ChatRequest.java`

Inbound DTO for the chat endpoint.

```java
Line 7:    @NotBlank(message = "Message cannot be empty") -- Bean validation.
           If the controller uses @Valid on the parameter, Spring returns
           a 400 Bad Request if message is null, empty, or whitespace.

Line 8:    message -- The user's chat message. Required.

Line 10:   sessionId -- Optional. If null or blank, the controller generates
           a new UUID. If provided, continues an existing conversation
           (the agent loads history from Redis using this ID).

Line 12-17: Two constructors — no-arg (for Jackson deserialization) and
           all-args (for programmatic construction).
```

### 6.2 ChatResponse.java

**File:** `src/main/java/com/example/aiagent/model/ChatResponse.java`

Outbound DTO for the chat endpoint.

```java
Line 8:    answer -- The agent's final answer text.

Line 9:    sessionId -- The session ID (may be newly generated or
           the one provided in the request). The client should store
           this to continue the conversation.

Line 10:   toolsUsed -- List of tool names used during the ReAct loop.
           **Bug:** Currently hardcoded as List.of("weather", "database",
           "calculator", "news", "rag_search") regardless of which
           tools were actually used.

Line 11:   processingTimeMs -- Total wall-clock time for the agent run
           in milliseconds.

Line 12:   timestamp -- Set to Instant.now() in both constructors,
           capturing when the response was created.

Line 14-16: No-arg constructor initializes timestamp. Required for
           Jackson deserialization.

Line 18-24: All-args constructor sets all fields and initializes timestamp.
```

### 6.3 AgentState.java

**File:** `src/main/java/com/example/aiagent/model/AgentState.java`

Mutable state object tracking the ReAct loop's progress through iterations.

```java
Line 8:    sessionId -- Identifies which conversation this state belongs to.

Line 9:    userMessage -- The original user question that started this run.

Line 10:   thoughtHistory -- Accumulates the agent's "Thought" strings
           parsed from LLM responses across iterations.

Line 11:   actionsTaken -- Accumulates "Action: toolName(input)" strings.

Line 12:   observations -- Accumulates tool execution results (what the
           agent observed after each action).

Line 13:   currentIteration -- Incremented each loop cycle (0-based, so
           after 3 iterations its value is 3).

Line 14:   completed -- Set to true when a "Final Answer" is found,
           signaling the loop should terminate.

Line 16-22: Default constructor initializes all lists as empty ArrayLists,
           currentIteration as 0, completed as false.

Line 24-27: Session-only constructor — calls default constructor then
           sets the sessionId.

Line 29-31: addThought() -- Appends a thought string to thoughtHistory.

Line 33-35: addAction() -- Appends an action string to actionsTaken.

Line 37-39: addObservation() -- Appends an observation string to observations.

Line 41-43: incrementIteration() -- Increments currentIteration by 1.
           Called at the start of each ReAct loop cycle.

Line 45-57: getFormattedHistory() -- Builds a human-readable string of all
           thoughts, actions, and observations, numbered by iteration:
           """
           Thought 1: I need to check the weather
           Action 1: weather(London)
           Observation 1: Sunny, 25°C, 45% humidity
           Thought 2: I have the weather info, I can answer now
           """
           This is injected into subsequent LLM prompts so the model
           knows what it has already done.

Line 47-56: Iterates through thoughtHistory. For each thought, also
           appends the corresponding action and observation if they
           exist (guarded by index bounds checks against actionsTaken
           and observations list sizes).

Line 59-72: Standard getters/setters for all fields.
```

### 6.4 DocumentInfo.java

**File:** `src/main/java/com/example/aiagent/model/DocumentInfo.java`

Metadata DTO for an ingested document.

```java
Line 7:    id -- UUID generated during ingestion. Used as the primary key
           for document management (list, delete).

Line 8:    filename -- The original filename from the upload.

Line 9:    contentType -- The MIME type (e.g., "application/pdf").

Line 10:   size -- File size in bytes (from MultipartFile.getSize()).

Line 11:   chunks -- Number of text chunks the document was split into
           by TokenTextSplitter during ingestion.

Line 12:   uploadedAt -- Set to Instant.now() in the constructor,
           records when the document was ingested.

Line 14-23: Constructors — no-arg (for Jackson) and all-args
           (sets uploadedAt automatically).
```

---

## 7. Controller Layer

### 7.1 AgentController.java

**File:** `src/main/java/com/example/aiagent/controller/AgentController.java`

The primary REST controller for agent interaction.

```java
Line 20:   @RestController -- Combines @Controller + @ResponseBody.
           All methods return JSON by default (via Jackson).

Line 21:   @RequestMapping("/api") -- Base path for all endpoints.
           Combined with method-level mappings, e.g. /api/agent/chat.

Line 22:   @CrossOrigin(origins = "*") -- Allows requests from any
           origin. Suitable for development; should be restricted
           in production.

Line 25:   Logger declaration for logging request/response lifecycle.

Line 27-31: Injected dependencies:
           - agent: ReActAgent — the core agent loop
           - ollamaService: OllamaService — for health check
           - memoryService: AgentMemoryService — for history/clear
           - toolRegistry: ToolRegistry — for listing tools
           - kafkaPublisher: KafkaEventPublisher — for event publishing

Line 33-41: Constructor injection — Spring auto-wires all dependencies.
           No field injection used (constructor injection is preferred
           for immutability and testability).
```

#### POST /api/agent/chat (Lines 43-65)

```java
Line 43:   @PostMapping("/agent/chat") -- Accepts POST requests with
           a JSON body mapped to ChatRequest.

Line 44:   ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest)
           -- Note: missing @Valid annotation, so @NotBlank on the
           message field is NOT enforced by the framework.

Line 45:   startTime -- Captured for measuring total processing time.

Line 47-50: Session ID handling — If the request doesn't include a
           sessionId (null or blank), generates a new UUID. This
           allows both new conversations and continued sessions.

Line 52:   Logs the incoming request at INFO level.

Line 53:   Publishes a "chat_request" event to the "ai-agent-chat"
           Kafka topic. Fire-and-forget (failures logged as warnings).

Line 55:   agent.run() -- The core call. Delegates to the ReActAgent
           which loops through Thought → Action → Observation cycles
           until a Final Answer is produced or max iterations are hit.

Line 57:   processingTime -- Total wall-clock time including all LLM
           calls and tool executions.

Line 58:   toolsUsed -- **Hardcoded bug.** Always returns all 5 tool
           names regardless of which were actually called. Should
           ideally capture tools used from AgentState.actionsTaken.

Line 60:   Constructs the ChatResponse with all fields.

Line 61:   Publishes a "chat_response" event to Kafka with the answer.

Line 63:   Logs the completion time.

Line 64:   Returns 200 OK with the ChatResponse body.
```

#### GET /api/agent/session/{sessionId}/history (Lines 67-71)

```java
Line 67:   @GetMapping("/agent/session/{sessionId}/history") -- Returns
           the conversation history for a session.

Line 68:   @PathVariable String sessionId -- Extracted from the URL path.

Line 69:   memoryService.getConversationHistory() -- Returns a List<String>
           where each entry is "role: content" (e.g., "user: What's the
           weather?"). Entries are stored in Redis as a List.

Line 70:   Returns 200 OK with the history list as JSON.
```

#### DELETE /api/agent/session/{sessionId} (Lines 73-77)

```java
Line 73:   @DeleteMapping("/agent/session/{sessionId}") -- Clears all
           conversation memory for a session.

Line 75:   memoryService.clearMemory() -- Deletes the Redis List key
           "agent:memory:{sessionId}".

Line 76:   Returns a confirmation JSON: {"status": "cleared", "sessionId": "..."}
```

#### GET /api/agent/tools (Lines 79-86)

```java
Line 79:   @GetMapping("/agent/tools") -- Lists all registered tools.

Line 81-85: Streams through toolRegistry.getAllTools() and collects into
           a Map<toolName, toolDescription>. Uses java.util.stream.Collectors
           to transform each entry.

Line 81:   Note: The import for Collectors is used inline here rather
           than at the top of the file (minor style inconsistency).
```

#### GET /api/health (Lines 88-97)

```java
Line 88:   @GetMapping("/health") -- Health check endpoint.

Line 90:   ollamaService.isAvailable() -- Calls GET /api/tags on the
           Ollama server. Returns true if Ollama responds, false otherwise.

Line 91-96: Returns a JSON object with:
           - status: "healthy" or "degraded" based on Ollama availability
           - ollama: "connected" or "disconnected"
           - tools: comma-separated list of tool names
           - version: "1.0.0"
```

### 7.2 DocumentController.java

**File:** `src/main/java/com/example/aiagent/controller/DocumentController.java`

REST controller for document upload and management.

```java
Line 17:   @RequestMapping("/api/documents") -- All document endpoints
           are under /api/documents.

Line 18:   @CrossOrigin(origins = "*") -- Same CORS policy as AgentController.

Line 22-28: ALLOWED_TYPES -- A Set of permitted MIME types:
           - application/pdf
           - application/vnd.openxmlformats-officedocument.wordprocessingml.document (DOCX)
           - text/plain
           - text/markdown
           - text/html
           Any file with a different content type is rejected with 400.
```

#### POST /api/documents/upload (Lines 36-69)

```java
Line 36:   @PostMapping("/upload") -- Multipart form upload endpoint.
           The client must send the file as a "file" part.

Line 37:   @RequestParam("file") MultipartFile file -- Spring parses
           the multipart request and injects the file part.

Line 39-43: Empty file check — Returns 400 with error message if the
           uploaded file has zero bytes.

Line 45-51: Content type validation — Checks against ALLOWED_TYPES.
           Returns 400 with the list of allowed types if the file's
           content type is null or not in the set.

Line 53-54: Logs the filename, content type, and size at INFO level.

Line 56:   ingestionService.ingest(file) -- The main processing call:
           1. TikaDocumentReader parses the file content
           2. TokenTextSplitter chunks the text (500 tokens, 50 overlap)
           3. Metadata is added (docId, filename, contentType, uploadTime)
           4. Chunks are embedded and stored in SimpleVectorStore
           5. A DocumentInfo record is saved to Redis

Line 58-61: Returns 200 with {"status": "success", "document": info}
           where info is the DocumentInfo object (id, filename, chunks, etc.)

Line 63-68: Exception handling — Catches IOException from ingestion
           and returns 500 Internal Server Error with the message.
```

#### GET /api/documents (Lines 71-74)

```java
Line 71:   @GetMapping -- Lists all ingested documents.

Line 73:   ingestionService.listDocuments() -- Reads the document index
           from Redis key "rag:documents" (a JSON-serialized List<DocumentInfo>).
           Returns empty list if no documents or on deserialization error.
```

#### DELETE /api/documents/{docId} (Lines 76-86)

```java
Line 76:   @DeleteMapping("/{docId}") -- Removes a document from the index.

Line 78:   ingestionService.deleteDocument(docId) -- Removes the DocumentInfo
           from the Redis index. **Important limitation:** This does NOT
           remove the document's chunks from SimpleVectorStore (which has
           no delete API). The chunks remain in memory until restart.

Line 79-85: Returns 200 with {"status": "deleted", "docId": "..."} on
           success, or 404 if the docId wasn't found in the index.
```

#### GET /api/documents/search (Lines 88-101)

```java
Line 88:   @GetMapping("/search") -- Semantic similarity search endpoint.

Line 89:   @RequestParam String query -- The search query string.

Line 90:   ingestionService.search(query, 3) -- Performs vector similarity
           search using nomic-embed-text embeddings. Returns top-3 results.

Line 91-100: Transforms results into a response map:
           - query: the search query
           - results: count of matching documents
           - documents: list of {content, metadata} for each match
             - content: the chunk text from doc.getText()
             - metadata: the chunk's metadata map (docId, filename, etc.)
```

### 7.3 McpServerController.java

**File:** `src/main/java/com/example/aiagent/mcp/McpServerController.java`

Implements the Model Context Protocol (MCP) server. This is the most complex controller (319 lines), supporting two
transport mechanisms: Streamable HTTP and Server-Sent Events (SSE).

```java
Line 33:   @RestController -- Returns JSON responses.
Line 34:   @RequestMapping("/mcp") -- Base path for all MCP endpoints.
Line 35:   @CrossOrigin(origins = "*") -- Permissive CORS.

Line 40:   ConcurrentHashMap<String, SseEmitter> emitters -- Tracks active
           SSE connections. Key = sessionId, Value = the SseEmitter for
           that session. ConcurrentHashMap for thread safety.

Line 41-47: Tool dependencies — Each tool is injected individually
           (not via ToolRegistry). This is because each tool has a
           different execute() signature based on the input argument.

Line 48:   DocumentIngestionService -- Needed for upload_document tool.

Line 49:   ObjectMapper -- Jackson's ObjectMapper for building JSON-RPC
           responses programmatically (not deserializing request bodies,
           which come in as ObjectNode).
```

#### SSE Transport: GET /mcp/sse (Lines 62-82)

```java
Line 62:   @GetMapping(value = "/sse", produces = TEXT_EVENT_STREAM_VALUE)
           -- Establishes an SSE connection. The client opens this
           endpoint and keeps the connection open for server-pushed events.

Line 63:   connect() -- Creates a new SSE session.

Line 64:   sessionId = UUID.randomUUID() -- Each SSE connection gets
           a unique session ID for routing messages back.

Line 65:   SseEmitter(0L) -- Infinite timeout. The connection stays
           open until the client disconnects or an error occurs.

Line 66:   emitters.put(sessionId, emitter) -- Stores the emitter so
           messages to this session can be sent later.

Line 68-70: Cleanup hooks — When the SSE connection completes, times
           out, or errors, remove the emitter from the map to prevent
           memory leaks.

Line 72-75: Sends the initial "endpoint" event to the client. The data
           is the URL where the client should POST its JSON-RPC messages:
           "/mcp/message?sessionId={sessionId}". This is the MCP
           handshake — the client learns where to send requests.

Line 76-79: If sending the endpoint event fails (e.g., client disconnected
           before the event was sent), remove the emitter from the map.
```

#### Streamable HTTP Transport: POST /mcp (Lines 84-119)

```java
Line 84:   @PostMapping(consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
           -- The Streamable HTTP transport. The client sends a single
           JSON-RPC 2.0 request and gets a synchronous JSON response.

Line 85:   handleStreamableHttp(@RequestBody ObjectNode request) -- The
           request body is parsed into a Jackson ObjectNode (tree model)
           for flexible field access without a typed DTO.

Line 86-88: Extracts method, params, and id from the JSON-RPC request:
           - method: the RPC method name (e.g., "initialize")
           - params: the RPC parameters (JsonNode, could be object or null)
           - id: the request ID used for correlating responses

Line 90:   Logs the incoming MCP request.

Line 92-94: If no "id" field exists, returns 204 No Content. In JSON-RPC,
           notifications (requests without an id) don't expect responses.

Line 96-98: Initializes the response object with jsonrpc="2.0" and the
           matching request id. The id must match per JSON-RPC spec.

Line 100-110: Method dispatch via switch expression:
           - "initialize" → handleInitialize() — protocol handshake
           - "tools/list" → handleToolsList() — describe available tools
           - "tools/call" → handleToolsCall() — execute a tool
           - default → returns a -32601 "Method not found" error
             (JSON-RPC standard error code)

Line 111-116: Catch-all exception handler. Returns -32603 "Internal error"
           with the exception message. This is the standard JSON-RPC
           error code for unexpected internal failures.
```

#### SSE Transport: POST /mcp/message (Lines 121-170)

```java
Line 121:  @PostMapping(value = "/message", consumes = APPLICATION_JSON)
           -- The SSE message endpoint. The client POSTs JSON-RPC requests
           here, and the response is sent back via the SSE emitter.

Line 122:  @RequestParam String sessionId -- Required. Identifies which
           SSE emitter to use for sending the response.

Line 124-127: Looks up the SseEmitter for this session. If none found
           (e.g., session expired), returns 404.

Line 129-131: Extracts method, params, and id from the request body
           (same logic as handleStreamableHttp).

Line 135-137: If no "id", returns 202 Accepted. Notifications don't
           need responses.

Line 139-141: Initializes the response JSON-RPC object.

Line 143-159: Same method dispatch as handleStreamableHttp — the three
           MCP methods (initialize, tools/list, tools/call) are handled
           identically by the same private methods.

Line 161-167: The key difference from Streamable HTTP: instead of returning
           the response directly, the response is sent as an SSE event
           named "message" with JSON data via the emitter. This pushes
           the response to the client over the already-open SSE connection.

Line 169:  Returns 202 Accepted — the actual response is delivered
           asynchronously via SSE, not in the HTTP response body.
```

#### handleInitialize() (Lines 172-188)

```java
Line 172:  handleInitialize(ObjectNode response) -- Builds the MCP
           initialize response.

Line 173-174: Sets protocolVersion to "2024-11-05" — the MCP spec
           version this server implements.

Line 176-180: Declares server capabilities:
           - tools: { listChanged: false } — The server has tools but
             the list doesn't change dynamically (no tool addition/removal
             at runtime). The client shouldn't poll for tool list changes.

Line 182-185: Server info:
           - name: "ai-agent-tools"
           - version: "1.0.0"
           These values are hardcoded but correspond to app.mcp.server.*
           in application.yml.

Line 187:  Sets the "result" field on the JSON-RPC response.
```

#### handleToolsList() (Lines 190-213)

```java
Line 190:  handleToolsList(ObjectNode response) -- Returns descriptions
           of all 6 MCP tools with their JSON Schema input specifications.

Line 194-209: Each addTool() call registers a tool with:
           - name: The tool identifier (used in tools/call dispatch)
           - description: Human-readable description
           - inputSchema: JSON Schema object defining parameters

Line 194-195: get_weather — takes "city" (string), e.g., "London"
Line 196-197: get_news — takes "topic" (string), e.g., "technology"
Line 198-199: calculate — takes "expression" (string), e.g., "2 + 2"
Line 200-201: query_database — takes "query" (string), e.g., a search term
Line 202-203: rag_search — takes "query" (string), e.g., "refund policy?"
Line 204-209: upload_document — takes "filename" (string),
           "content" (string, base64-encoded), "contentType" (string, optional)
```

#### addTool() (Lines 215-239)

```java
Line 215:  addTool(ArrayNode, name, description, inputSchema) -- Helper
           that constructs a tool definition in the MCP schema format.

Line 216-219: Creates the tool object with name and description.

Line 221-235: Builds the JSON Schema for parameters:
           - type: "object"
           - properties: Maps each parameter to {type, description}
           - required: Array of all parameter names (all are required
             by default in this implementation, even optional ones
             like contentType for upload_document)

Line 237:  Sets the inputSchema on the tool object.

Line 238:  Adds the tool to the tools array.
```

#### handleToolsCall() (Lines 241-264)

```java
Line 241:  handleToolsCall(JsonNode params, ObjectNode response) -- The
           core tool dispatch handler.

Line 242:  toolName = params.get("name").asText() -- The tool to call.

Line 243:  arguments = params.get("arguments") -- The tool arguments as
           a JsonNode object.

Line 245-253: Switch expression dispatches to the correct tool:
           - "get_weather" → weatherTool.execute(city)
           - "get_news" → newsTool.execute(topic)
           - "calculate" → calculatorTool.execute(expression)
           - "query_database" → databaseTool.execute(query)
           - "rag_search" → ragTool.execute(query)
           - "upload_document" → handleUpload(arguments) — special case
           - default → "Unknown tool: {name}"

           Note: For simple tools, the arguments node is accessed by
           parameter name (e.g., arguments.get("city").asText()).
           For upload_document, the full arguments node is passed.

Line 255-263: Wraps the result in MCP's content format:
           { "content": [{ "type": "text", "text": result }] }
           This is the standard MCP tool result structure that clients
           expect for text-based results.
```

#### handleUpload() (Lines 266-283)

```java
Line 266:  handleUpload(JsonNode arguments) -- Special handler for the
           upload_document tool. Bridges MCP's base64-encoded content
           to the multipart-based document ingestion pipeline.

Line 267:  filename — The original filename (e.g., "document.pdf").
Line 268:  base64Content — The file content as a base64-encoded string.
Line 269-270: contentType — Optional, falls back to detectContentType()
           which infers MIME type from the file extension.

Line 273:  Base64.getDecoder().decode(base64Content) — Decodes the
           base64 string into raw bytes.

Line 274:  new ByteArrayMultipartFile(...) — Creates an adapter that
           implements Spring's MultipartFile interface over the raw
           byte array. This lets us reuse DocumentIngestionService.ingest()
           which expects a MultipartFile.

Line 275:  ingestionService.ingest(file) — Same code path as the REST
           upload endpoint. Parses, chunks, embeds, and stores.

Line 276:  Returns a success message with the filename and chunk count.

Line 277-282: Error handling:
           - IllegalArgumentException → "Invalid base64 content"
           - IOException → generic error message with exception details
```

#### detectContentType() (Lines 285-293)

```java
Line 285:  detectContentType(String filename) -- Infers MIME type from
           the file extension. Used when the MCP client doesn't provide
           an explicit contentType argument.

Line 287-292: Extension mapping:
           .pdf → application/pdf
           .docx → application/vnd.openxmlformats-officedocument.wordprocessingml.document
           .txt → text/plain
           .md → text/markdown
           .html/.htm → text/html
           default → application/octet-stream
```

#### ByteArrayMultipartFile (Lines 295-318)

```java
Line 295:  private static class ByteArrayMultipartFile implements MultipartFile
           -- An adapter class that wraps a byte array as a MultipartFile.
           This is necessary because MCP clients send file content as
           base64-encoded strings in JSON, not as multipart form data.

Line 297-300: Fields — name (form field name), originalFilename,
           contentType, content (the raw bytes).

Line 301-306: Constructor — Stores all parameters.

Line 308:  getName() — Returns the form field name (always "file").
Line 309:  getOriginalFilename() — Returns the original filename.
Line 310:  getContentType() — Returns the MIME type.
Line 311:  isEmpty() — Checks if the byte array has length 0.
Line 312:  getSize() — Returns the byte array length.
Line 313:  getBytes() — Returns the raw byte array (not a copy).
Line 314:  getInputStream() — Wraps the bytes in a ByteArrayInputStream.
Line 315:  getResource() — Wraps the bytes in a ByteArrayResource.
Line 316:  transferTo(File) — Writes bytes to a File using Files.write.
Line 317:  transferTo(Path) — Writes bytes to a Path using Files.write.
```

---

## 8. Agent Layer

### 8.1 ReActAgent.java

**File:** `src/main/java/com/example/aiagent/agent/ReActAgent.java`

The core agent implementing the ReAct (Reasoning + Acting) pattern. This is the brain of the application — it
iteratively reasons about what to do, calls tools, and synthesizes a final answer.

```java
Line 19:   @Component -- Registered as a Spring bean. Not a @Service
           because it's not a typical business service — it's an
           orchestration engine with its own loop and state.

Line 23-25: ACTION_PATTERN — Regex to parse the LLM's tool call responses:
           "Action: weather\nInput: London"
           - Group 1: tool name (e.g., "weather")
           - Group 2: tool input (e.g., "London")
           - (?=\n|$) — Non-greedy match until newline or end of string
           - Pattern.CASE_INSENSITIVE — Matches "Action:" or "action:"

Line 26-28: FINISH_PATTERN — Regex to detect final answers:
           "Final Answer: The weather in London is sunny"
           - Group 1: the answer text
           - Pattern.DOTALL — Dot matches newlines (answer may be multi-line)
           - Pattern.CASE_INSENSITIVE — Matches "Final Answer:" or "final answer:"

Line 30-34: Injected dependencies:
           - ollamaService: Makes LLM API calls
           - memoryService: Persists conversation history to Redis
           - toolRegistry: Lookups and executes tools
           - agentConfig: Provides maxIterations limit
           - kafkaPublisher: Publishes agent events for observability
```

#### run() method (Lines 46-111)

```java
Line 46:   run(String userMessage, String sessionId) -- The main entry
           point. Called by AgentController.chat(). Returns the final
           answer string.

Line 47-48: Creates a new AgentState for this run with the session ID,
           and stores the user's message.

Line 50:   memoryService.saveMessage(sessionId, "user", userMessage) --
           Persists the user's message to the Redis conversation history.
           Future calls within the same session will include this message
           in the context.

Line 51:   kafkaPublisher.publishAgentEvent() -- Publishes a "user_message"
           event for observability/audit.

Line 53:   buildSystemPrompt() -- Constructs the system prompt that tells
           the LLM about its role, available tools, and the expected
           response format (Thought/Action/Input/Final Answer).

Line 54:   buildContext(sessionId, userMessage) -- Builds the user prompt
           by combining the Redis conversation history with the current
           message.

Line 56:   The main ReAct loop. Iterates up to maxIterations (default 10).
           Each iteration:
           1. Builds a prompt including all previous thoughts/actions/observations
           2. Sends it to the LLM
           3. Parses the response for "Final Answer:" or "Action:"
           4. If action found → executes the tool → records observation
           5. If final answer found → saves to memory → returns
           6. If neither → treats as a thought, continues

Line 57:   state.incrementIteration() -- Increments the counter before
           processing. The first iteration sets currentIteration to 1.

Line 58:   Logs the iteration number and session ID at INFO level.

Line 60:   buildIterationPrompt(state, context) -- Constructs the full
           prompt for this iteration, including:
           - Conversation history from Redis
           - All previous thoughts, actions, and observations
           - "What should you do next?" instruction

Line 61:   ollamaService.chat(systemPrompt, prompt) -- Calls the Ollama
           API with the system prompt and iteration prompt. Returns the
           LLM's raw text response.

Line 63:   Logs the LLM response at DEBUG level (may be verbose).

Line 65-75: Check for "Final Answer:" in the response:
           Line 65:  FINISH_PATTERN.matcher(llmResponse) -- Searches for
                     the pattern anywhere in the response.
           Line 66:  If found:
           Line 67:   Extracts the answer text (everything after "Final Answer:")
           Line 68:   Records "Final answer reached" as a thought
           Line 69:   Marks the state as completed
           Line 71:   Saves the final answer to Redis as an "assistant" message
           Line 72:   Publishes a "final_answer" agent event to Kafka
           Line 74:   Returns the answer — loop terminates

Line 77-95: Check for "Action: ... Input: ..." in the response:
           Line 77:  ACTION_PATTERN.matcher(llmResponse) -- Searches for
                     tool call pattern.
           Line 78:  If found:
           Line 79:   Extracts tool name (e.g., "weather") and input (e.g., "London")
           Line 82:   Extracts and records the "Thought" preceding the action
           Line 83:   Records the action as "toolName(input)"
           Line 85:   toolRegistry.getTool(toolName) -- Looks up the tool by
                     name (case-insensitive lookup in the registry)
           Line 87-88: If tool found → executes it with the input
           Line 90:   If tool NOT found → returns an error message listing
                     available tools (so the LLM can correct itself next iteration)
           Line 92:   Records the observation in the state
           Line 94-95: Publishes a "tool_call" event to Kafka (truncated
                     to 100 chars for safety)

Line 96-103: No action or final answer found:
           Line 97:   Treats the entire LLM response as a "thought"
           Line 98:   If this is the last iteration (i == maxIterations - 1):
           Line 99:    extractAnswer() -- Tries to find "Final Answer:" in
                      the response; if not found, takes the first 500 chars
           Line 100:   Saves as assistant message to Redis
           Line 101:   Returns the fallback answer — loop terminates
           Line 103:  If NOT the last iteration, continues to the next
                      iteration with this thought in the state

Line 106-110: After the loop exits without a final answer (all iterations
           exhausted and the last iteration didn't match the fallback path):
           Line 106-108: Uses the last thought if available, otherwise
                      uses a default "I could not determine a final answer."
           Line 109:  Saves to Redis as assistant message
           Line 110:  Returns the fallback answer

           Note: This code path may be unreachable because line 98-101
           handles the last-iteration case. It's a safety net.
```

#### buildSystemPrompt() (Lines 113-136)

```java
Line 113:  buildSystemPrompt() -- Constructs the system prompt sent with
           every LLM call. This prompt defines the agent's behavior.

Line 114-135: The system prompt template (text block):
           Line 115:  "You are a helpful AI agent that uses the ReAct pattern"
           Line 117-118: Lists all available tools using toolRegistry.getToolDescriptions()
                      which formats them as "- toolName: toolDescription"
           Line 120-123: Defines the action format:
                      "Thought: [reasoning]\nAction: [tool_name]\nInput: [input]"
           Line 125-126: Defines the final answer format:
                      "Thought: [reasoning]\nFinal Answer: [answer]"
           Line 128-134: Rules for the LLM:
                      - Always start with a Thought
                      - Use one tool at a time
                      - Wait for observation before continuing
                      - Give Final Answer when enough information
                      - Be concise and helpful
```

#### buildContext() (Lines 138-141)

```java
Line 138:  buildContext(sessionId, userMessage) -- Builds the user-facing
           prompt part with conversation history.

Line 139:  memoryService.getFormattedHistory(sessionId) -- Fetches all
           messages from Redis for this session and indents them with
           two spaces per line.

Line 140:  Formats the prompt as:
           "Conversation History:\n{history}\n\nUser's current message: {msg}"

           The history is empty for new sessions, so the prompt just
           contains the user's current message.
```

#### buildIterationPrompt() (Lines 143-156)

```java
Line 143:  buildIterationPrompt(state, context) -- Builds the full prompt
           for a specific ReAct iteration.

Line 144-145: Starts with the conversation context (history + current message).

Line 147-151: If there are previous thoughts/actions/observations:
           Line 148:  "Previous steps:" header
           Line 149:  state.getFormattedHistory() — The numbered
                      Thought/Action/Observation log
           Line 150:  Blank line separator

Line 153:  Appends the instruction: "What should you do next? Respond
           with a Thought and either an Action or Final Answer."
           This tells the LLM what format to use in its response.
```

#### extractThought() (Lines 158-165)

```java
Line 158:  extractThought(String response) -- Parses the "Thought: ..."
           portion from the LLM response.

Line 159:  Regex: "Thought:\s*(.+?)(?=\nAction:|$)" with DOTALL
           - Matches "Thought:" followed by any text
           - Stops at "\nAction:" (the start of the action block) or end
           - Group 1: the thought text

Line 161-162: If found, returns the trimmed thought.

Line 164:  Fallback — If no "Thought:" pattern found, takes the first
           200 characters of the response. Prevents losing context when
           the LLM doesn't follow the expected format.
```

#### extractAnswer() (Lines 167-174)

```java
Line 167:  extractAnswer(String response) -- Last-resort answer extraction
           used when the loop is about to terminate but no clean "Final
           Answer:" was found.

Line 168-169: Tries to find "Final Answer:" in the response (may be
           embedded in freeform text).

Line 170-172: If found, returns the trimmed answer.

Line 173:  Fallback — Takes the first 500 characters of the response.
           This is a best-effort extraction for unstructured LLM output.
```

---

## 9. Service Layer

### 9.1 OllamaService.java

**File:** `src/main/java/com/example/aiagent/service/OllamaService.java`

A custom HTTP client that calls the Ollama REST API directly. This does NOT use Spring AI's `ChatModel` abstraction — it
constructs the request body manually and parses the response.

```java
Line 15:   @Service -- Registered as a Spring service bean.

Line 20-22: Injected dependencies:
           - restTemplate: The bean from AppConfig (10s connect, 120s read)
           - config: OllamaConfig (baseUrl, model, timeout, maxTokens)
           - objectMapper: Jackson ObjectMapper for JSON construction/parsing
```

#### chat() method (Lines 30-61)

```java
Line 30:   chat(String systemPrompt, String userMessage) -- Sends a chat
           request to the Ollama API and returns the assistant's response.

Line 32:   Creates the request body as a Jackson ObjectNode:
           Line 33:   "model": config.getModel() — e.g., "qwen3.5:4b"
           Line 34:   "stream": false — Tells Ollama to return the complete
                      response at once (not streamed token-by-token)

Line 36:   Builds the messages array:
           Line 38-43: If systemPrompt is non-null and non-empty, adds a
                      system message: {"role": "system", "content": systemPrompt}
           Line 45-48: Always adds the user message:
                      {"role": "user", "content": userMessage}

           Note: Only one user message is sent per call, not the full
           conversation history. The ReActAgent builds the context into
           the userMessage string itself.

Line 50:   Sets the messages array on the request body.

Line 52:   Constructs the URL: config.getBaseUrl() + "/api/chat"
           e.g., "http://localhost:11434/api/chat"

Line 53:   POSTs the request using RestTemplate. The response is received
           as a raw String.

Line 55-56: Parses the JSON response and navigates to:
           response.message.content — the assistant's reply text.
           Uses .path() for safe navigation (returns missing node instead
           of throwing). Falls back to "No response" if the content field
           is absent.

Line 57-60: Exception handling — Catches all exceptions and returns an
           error message string. The calling code (ReActAgent) treats
           this as an observation, which may cause the agent to retry
           or give a degraded answer.
```

#### isAvailable() method (Lines 63-71)

```java
Line 63:   isAvailable() -- Health check for the Ollama server.

Line 65:   GET {baseUrl}/api/tags — Ollama's tag listing endpoint.
           If Ollama is running, this returns a 200 with the list of
           available models. If not, it throws an exception.

Line 66:   Returns true if the GET succeeds.

Line 68-69: Catches any exception (connection refused, timeout, etc.)
           and returns false. Logs at WARN level.
```

**Design note:** This service does not use `config.getMaxTokens()` or `config.getTimeout()`. The maxTokens could be
added as `options.num_predict` in the Ollama request body, and the timeout is effectively enforced by the RestTemplate's
readTimeout.

### 9.2 DocumentIngestionService.java

**File:** `src/main/java/com/example/aiagent/service/DocumentIngestionService.java`

Handles RAG document ingestion (parse, chunk, embed, store) and similarity search.

```java
Line 29:   @Service -- Spring service bean.

Line 33:   DOC_INDEX_KEY = "rag:documents" — Redis key for the document
           metadata index. Stores a JSON-serialized List<DocumentInfo>.

Line 34:   DEFAULT_TOP_K = 3 — Default number of search results.

Line 36:   VectorStore vectorStore — The SimpleVectorStore bean (in-memory).
           Used for embedding documents and performing similarity search.

Line 37:   TokenTextSplitter textSplitter — Configured in the constructor.
           Splits documents into chunks for embedding.

Line 38-39: StringRedisTemplate and ObjectMapper for Redis operations
           and JSON serialization.
```

#### Constructor (Lines 41-48)

```java
Line 41-48: Injects VectorStore, StringRedisTemplate, and ObjectMapper.
           Creates the TokenTextSplitter with parameters:
           - defaultChunkSize: 500 — Target chunk size in tokens
           - minChunkSizeChars: 50 — Minimum chunk size in characters
           - minChunkLengthToEmbed: 5 — Minimum character length to embed
           - maxNumChunks: 10000 — Maximum number of chunks per document
           - keepSeparator: true — Preserves paragraph/section separators
```

#### ingest() method (Lines 50-81)

```java
Line 50:   ingest(MultipartFile file) -- The main ingestion pipeline.
           Called by both DocumentController (REST) and McpServerController (MCP).

Line 51-52: Generates a unique docId (UUID) and extracts the filename.

Line 56:   TikaDocumentReader(file.getResource()) -- Creates a Tika reader
           for the uploaded file. Apache Tika auto-detects the file format
           and extracts text content. Supports PDF, DOCX, TXT, Markdown, HTML.

Line 57:   tikaReader.get() -- Parses the file into a List<spring-ai Document>.
           Each Document has text content and metadata.

Line 59-61: If parsing produces no documents (e.g., empty or corrupted file),
           throws an IOException with the filename.

Line 63:   textSplitter.apply(documents) -- Splits the parsed documents into
           chunks. Each chunk is a separate Document with ~500 tokens.
           Overlap of 50 tokens ensures context isn't lost at boundaries.

Line 66-71: Enriches each chunk's metadata:
           - docId: The UUID for this ingestion (groups chunks by source file)
           - filename: The original filename
           - contentType: The MIME type
           - uploadTime: ISO-8601 timestamp

Line 73:   vectorStore.add(chunks) -- The key call:
           1. SimpleVectorStore calls the EmbeddingModel (nomic-embed-text)
              to generate a vector for each chunk
           2. Stores the chunks and their vectors in the in-memory store
           3. After this call, the chunks are searchable via similarity search

Line 75-77: Creates a DocumentInfo record and saves it to Redis.
           This is the "document index" used for listing and management.

Line 79:   Logs successful ingestion with the filename and chunk count.

Line 80:   Returns the DocumentInfo (contains id, filename, chunks count, etc.)
```

#### search() methods (Lines 83-91)

```java
Line 83-87: search(String query, int topK) -- Performs vector similarity search.
           - SearchRequest.builder().query(query).topK(topK).build()
           - SimpleVectorStore embeds the query using nomic-embed-text
           - Performs cosine similarity search against all stored vectors
           - Returns the top-K most similar Document objects

Line 89-91: search(String query) -- Overload using DEFAULT_TOP_K (3).
           Convenience method used by RAGTool.
```

#### listDocuments() (Lines 93-104)

```java
Line 93-104: listDocuments() -- Returns all ingested document metadata.
           Line 95:   Reads from Redis key "rag:documents"
           Line 96-98: If null or empty, returns empty list
           Line 99:   Deserializes JSON to List<DocumentInfo> using TypeReference
           Line 100-102: If deserialization fails, logs error and returns
                      empty list (graceful degradation)
```

#### deleteDocument() (Lines 106-116)

```java
Line 106:  deleteDocument(String docId) -- Removes a document from the
           Redis metadata index.

Line 107:  listDocuments() -- Loads all documents from Redis.
Line 108:  removeIf(doc -> doc.getId().equals(docId)) -- Removes the
           matching entry. Returns true if any entry was removed.

Line 110-112: If removed, saves the updated list back to Redis.

           **Important limitation:** This does NOT remove the document's
           chunks from the SimpleVectorStore. SimpleVectorStore has no
           delete/remove API. The chunks remain in memory until the
           application restarts. This means:
           - Search results may include chunks from "deleted" documents
           - The document index in Redis becomes inconsistent with the
             actual vector store contents
```

### 9.3 KafkaEventPublisher.java

**File:** `src/main/java/com/example/aiagent/service/KafkaEventPublisher.java`

Fire-and-forget event publishing to Kafka for observability and audit.

```java
Line 13:   @Service -- Spring service bean.

Line 20-24: Topic names injected via @Value:
           - eventsTopic: "ai-agent-events" (from app.kafka.topics.events)
           - chatTopic: "ai-agent-chat" (from app.kafka.topics.chat)
           Default values after the colon are used if the property is missing.

Line 26-28: Constructor injection of KafkaTemplate<String, Object>.
           The Object value type means Spring uses the JsonSerializer
           (configured in application.yml) to serialize event payloads.
```

#### publishChatEvent() (Lines 30-43)

```java
Line 30:   publishChatEvent(sessionId, event, details) -- Publishes to the
           chat topic. Called by AgentController on chat requests and responses.

Line 32-36: Builds a payload map:
           - sessionId: The conversation session ID
           - event: Event type ("chat_request" or "chat_response")
           - details: The message content (user message or assistant answer)
           - timestamp: ISO-8601 instant

Line 38:   kafkaTemplate.send(chatTopic, sessionId, payload) -- Key is the
           sessionId (ensures same session events go to the same partition),
           value is the payload map.

Line 40-42: Catches exceptions and logs at WARN level. Failures are
           non-blocking — Kafka publishing should never break the agent.
```

#### publishAgentEvent() (Lines 45-58)

```java
Line 45:   publishAgentEvent(sessionId, type, content) -- Publishes to the
           events topic. Called by ReActAgent for tool calls, reasoning,
           and final answers.

Line 47-51: Same structure as publishChatEvent but with "type" instead
           of "event" and "content" instead of "details".

Line 53:   kafkaTemplate.send(eventsTopic, sessionId, payload)

           Event types published by ReActAgent:
           - "user_message" — When the agent receives a user message
           - "tool_call" — When a tool is executed (with truncated result)
           - "final_answer" — When the agent produces a final answer
```

---

## 10. Memory Layer

### 10.1 AgentMemoryService.java

**File:** `src/main/java/com/example/aiagent/memory/AgentMemoryService.java`

Redis-backed service for persisting and retrieving conversation history and agent state.

```java
Line 10:   @Service -- Spring service bean.

Line 13:   StringRedisTemplate -- Spring Data Redis template for string
           operations. Auto-configured by spring-boot-starter-data-redis.

Line 14:   MEMORY_PREFIX = "agent:memory:" -- Redis key prefix for
           conversation history lists.

Line 15:   MEMORY_TTL_HOURS = 24 -- All memory keys expire after 24 hours.
           Prevents unbounded Redis growth from abandoned sessions.

Line 17-19: Constructor injection of StringRedisTemplate.
```

#### saveMessage() (Lines 21-31)

```java
Line 21:   saveMessage(sessionId, role, content) -- Appends a message to
           the conversation history for a session.

Line 22:   Key: "agent:memory:{sessionId}" — A Redis List.

Line 23:   Entry format: "role: content" (e.g., "user: What's the weather?")
           Note: This simple format means colons in the role/content may
           be ambiguous when parsing. The history is only used as LLM
           context, so exact parsing isn't critical.

Line 24:   rightPush() — Appends to the right (end) of the list.
           Redis Lists maintain insertion order.

Line 25:   expire(key, 24, HOURS) -- Resets the TTL to 24 hours each time
           a message is saved. This effectively extends the session lifetime
           with each interaction.

Line 27-30: Auto-trimming: If the list exceeds 50 entries, leftPop()
           removes the oldest entry (from the left/head). This keeps
           the conversation window bounded. Note: AgentConfig.memorySize
           (default 20) is NOT used here — the code uses a hardcoded 50.
```

#### getConversationHistory() (Lines 33-40)

```java
Line 33:   getConversationHistory(sessionId) -- Returns all messages for a session.

Line 35-36: Gets the list size. If null or 0, returns an empty list.

Line 39:   range(key, 0, -1) — Returns all elements from the Redis List.
           0 = first element, -1 = last element (LRANGE semantics).
```

#### getFormattedHistory() (Lines 42-47)

```java
Line 42:   getFormattedHistory(sessionId) -- Returns the history as a
           multi-line string with each message indented by two spaces.

Line 43:   getConversationHistory() -- Gets the raw list.
Line 44-46: Maps each entry to "  {entry}" and joins with newlines.
           Used by ReActAgent.buildContext() to include conversation
           history in the LLM prompt.
```

#### clearMemory() (Lines 49-52)

```java
Line 49:   clearMemory(sessionId) -- Deletes the conversation history list.
           Called by AgentController.clearSession().

Line 51:   redisTemplate.delete(key) -- Removes the entire Redis List.
```

#### saveAgentState() / getAgentState() (Lines 54-62)

```java
Line 54:   saveAgentState(sessionId, state) -- Persists an agent state
           as a string (JSON) to a separate Redis key.

Line 55:   Key: "agent:memory:state:{sessionId}" — A Redis String (not List).
           24-hour TTL.

Line 56:   opsForValue().set(key, state, 24, HOURS)

           Note: These methods exist but are NOT called by any current code
           in the application. The ReActAgent creates a new AgentState
           per run() call and doesn't persist it between runs. The
           thought/action/observation history is rebuilt from conversation
           history in the LLM prompt, not from stored AgentState.

Line 59-62: getAgentState(sessionId) -- Reads the stored state string.
           Returns null if the key doesn't exist or has expired.
```

---

## 11. Tool Layer

### 11.1 Tool.java

**File:** `src/main/java/com/example/aiagent/tools/Tool.java`

The contract that all agent tools must implement.

```java
Line 3:    public interface Tool -- A simple Strategy pattern interface.

Line 5:    getName() -- Returns the tool's identifier. Used by:
           - ToolRegistry for lookup (keyed by name)
           - ReActAgent for dispatching (parsed from LLM "Action:" output)
           - McpServerController for MCP tool listing/execution
           - AgentController for the /tools endpoint

Line 7:    getDescription() -- Human-readable description. Used by:
           - ToolRegistry.getToolDescriptions() → injected into the LLM system prompt
           - MCP tool listing
           - /tools REST endpoint

Line 9:    execute(String input) -- The tool's core logic. Takes a single
           string input and returns a string result. The LLM provides
           the input as the text after "Input:" in its response.

Line 11-13: getParameterSchema() -- Default method returning a JSON Schema
           string for the tool's input. Used by MCP for input validation.
           Default schema is just a string type.
```

### 11.2 ToolRegistry.java

**File:** `src/main/java/com/example/aiagent/tools/ToolRegistry.java`

Auto-discovers all Tool implementations and provides lookup and listing capabilities.

```java
Line 10:   @Component -- Registered as a Spring bean.

Line 13:   Map<String, Tool> tools -- Internal map from tool name to
           Tool instance. Uses HashMap (not concurrent — not expected to
           be modified after construction).

Line 15-19: Constructor injection of List<Tool> -- Spring collects all
           beans that implement the Tool interface and injects them as
           a list. The constructor iterates and populates the map keyed
           by tool.getName().

           Note: If two tools return the same getName(), one overwrites
           the other. No validation or warning.

Line 21-23: getTool(String name) -- Case-insensitive lookup. Converts
           to lowercase before looking up in the map.
           **Bug potential:** Tool names are stored as-is from getName(),
           but lookups are case-insensitive. If a tool's getName() returns
           "WeatherTool", it's stored under "WeatherTool" but looked up
           as "weathertool". Current tool implementations all return
           lowercase names, so this works in practice.

Line 25-27: getAllTools() -- Returns a defensive copy of the tools map.
           New HashMap<>(tools) prevents external modification.

Line 29-33: getToolDescriptions() -- Formats all tools as:
           "- toolName: toolDescription\n-toolName: toolDescription\n..."
           Used by ReActAgent.buildSystemPrompt() to tell the LLM
           which tools are available.

Line 35-37: getToolNames() -- Returns a comma-separated string of all
           tool names. Used in the health endpoint and error messages.
```

### 11.3 WeatherTool.java

**File:** `src/main/java/com/example/aiagent/tools/WeatherTool.java`

Fetches weather data from wttr.in with a simulated fallback.

```java
Line 6:    @Component -- Auto-discovered by ToolRegistry.

Line 9:    private final RestTemplate restTemplate = new RestTemplate()
           -- **Issue:** Creates its own RestTemplate instead of using
           the configured bean from AppConfig. This means:
           - No connect/read timeouts (uses JVM defaults)
           - No Spring-managed connection pool
           - Could hang indefinitely if wttr.in is unreachable

Line 12-13: getName() returns "weather" — used in LLM Action dispatch
           and MCP tool name.

Line 17-18: getDescription() tells the LLM how to use the tool:
           "Get current weather for a city. Input: city name"

Line 22-33: execute(String input):
           Line 24:   Trims whitespace from the city name.
           Line 25-28: Builds the wttr.in URL:
                      https://wttr.in/{city}?format=%t+%C+%h+%w
                      - %t = temperature
                      - %C = weather condition
                      - %h = humidity
                      - %w = wind speed
                      Spaces in city names are replaced with "+".
           Line 29:   restTemplate.getForObject() — Makes the HTTP GET.
           Line 30:   Formats the result: "Weather for {city}: {result}"
           Line 31-32: On exception, returns simulated data:
                      "Sunny, 25°C, 45% humidity, 10 km/h wind"
                      This graceful degradation ensures the agent always
                      gets a response, even if the API is down.
```

### 11.4 NewsTool.java

**File:** `src/main/java/com/example/aiagent/tools/NewsTool.java`

Fetches news headlines from NewsAPI with a simulated fallback.

```java
Line 9:    Same RestTemplate issue as WeatherTool — creates its own.

Line 12-13: getName() returns "news".

Line 17-18: getDescription(): "Get latest news headlines for a topic."

Line 22-39: execute(String input):
           Line 24:   Trims and lowercases the topic.
           Line 25-28: Builds the NewsAPI URL:
                      https://newsapi.org/v2/top-headlines?q={topic}&pageSize=3&apiKey=demo
                      Note: "demo" is not a valid API key — this will likely
                      fail in production, triggering the fallback.
           Line 29:   restTemplate.getForObject() — Makes the HTTP GET.
           Line 31-36: On exception, returns three simulated headlines:
                      "1) Major development in {topic} reported today.
                       2) Breaking: New trends emerge in {topic} sector.
                       3) Analysis: Current state of {topic} market."
           The fallback uses String.format with the topic repeated 3 times.
```

### 11.5 CalculatorTool.java

**File:** `src/main/java/com/example/aiagent/tools/CalculatorTool.java`

A custom recursive-descent math expression evaluator.

```java
Line 6:    @Component -- No external dependencies (no RestTemplate needed).

Line 9-10:  getName() returns "calculator".

Line 14-15: getDescription(): "Evaluate mathematical expressions.

Line 19-30: execute(String input):
           Line 21:   Trims whitespace from the expression.
           Line 22:   evaluate(expression) — The recursive evaluation.
           Line 23-24: If the result is a whole number (e.g., 4.0), formats
                      as integer: "Result: 4"
           Line 25:   Otherwise, formats with 6 decimal places: "Result: 3.141593"
           Line 27-29: On error, returns "Error evaluating expression: {message}"
```

#### evaluate() method (Lines 32-59)

```java
Line 32:   evaluate(String expression) -- Recursive descent parser.

Line 33:   Removes all whitespace from the expression.

Line 35-38: Addition: Splits on "+", evaluates both halves.
           **Bug:** Does NOT respect operator precedence. "2+3*4" splits
           on "+" first → evaluate("2") + evaluate("3*4") = 2 + 12 = 14.
           But "2*3+4" splits on "+" first → evaluate("2*3") + evaluate("4")
           = 6 + 4 = 10, which is correct by accident since * is checked next.
           "2+3+4" splits on the first "+" → "2" + "3+4" → 2 + 7 = 9.
           But split("\\+") on "2+3+4" gives ["2","3","4"], so
           evaluate("2") + evaluate("3+4") — wait, split gives all parts.
           Actually, split("\\+") gives ["2", "3", "4"], but the code
           only uses parts[0] and parts[1]. This means "2+3+4" would
           evaluate "2" + "3" = 5, losing the "+4". Multi-operator
           expressions with the same operator are broken.

Line 39-42: Subtraction: Skips if expression starts with "-" (negative number).
           Same precedence and splitting issues as addition.

Line 43-46: Multiplication: Same pattern, splits on "*".

Line 47-52: Division: Same pattern, splits on "/". Checks for
           division by zero and throws ArithmeticException.

Line 53-56: Square root: Matches "sqrt(...)" format.
           Extracts the inner expression, evaluates it recursively,
           and returns Math.sqrt(result).

Line 58:   Base case: If no operators found, parses as a double.
           This handles plain numbers like "42" or "3.14".
```

**Known limitations:**

- No operator precedence (evaluates left-to-right)
- No parentheses support (except `sqrt(...)`)
- Multi-operand expressions (e.g., `1+2+3`) lose trailing operands
- Negative numbers only handled at the start of the expression
- No exponentiation, modulus, or trigonometric functions

### 11.6 DatabaseTool.java

**File:** `src/main/java/com/example/aiagent/tools/DatabaseTool.java`

A hardcoded knowledge base with Redis caching.

```java
Line 10:   @Component -- Requires StringRedisTemplate for caching.

Line 13:   StringRedisTemplate -- For cache operations.
Line 14:   Map<String, String> knowledgeBase -- The in-memory knowledge store.

Line 16-20: Constructor — Injects StringRedisTemplate and initializes the
           knowledge base with hardcoded entries.

Line 22-28: initializeKnowledgeBase() -- Populates 5 entries:
           - "spring boot" → Spring Boot description
           - "kafka" → Apache Kafka description
           - "redis" → Redis description
           - "ollama" → Ollama description
           - "ai" → AI description

Line 31-32: getName() returns "database".

Line 36-37: getDescription(): "Query the knowledge base."

Line 40-63: execute(String input):
           Line 43:   Trims and lowercases the query.
           Line 45:   Cache key: "db:{query}"
           Line 46-49: Checks Redis cache first. If found, returns
                      "From cache: {cached_value}".
           Line 51-55: Iterates through the knowledge base. If the query
                      contains any key (substring match):
                      Line 53: Caches the result in Redis with 5-minute TTL
                      Line 54: Returns "Found: {value}"
           Line 58:   If no match, returns: "No specific information found
                      for: {query}. Available topics: spring boot, kafka, ..."
           Line 60-62: On exception, returns "Database error: {message}"
```

### 11.7 RAGTool.java

**File:** `src/main/java/com/example/aiagent/tools/RAGTool.java`

Semantic search over uploaded documents using the vector store.

```java
Line 13:   @Component -- Injects DocumentIngestionService.

Line 17-20: Constructor — Stores the ingestion service reference.

Line 24-25: getName() returns "rag_search".

Line 29-30: getDescription(): "Search uploaded documents for relevant information."

Line 34-68: execute(String input):
           Line 36:   Trims whitespace from the query.
           Line 37-39: If empty, returns "Please provide a search query."
           Line 41:   Logs the RAG search query at INFO level.
           Line 43:   ingestionService.search(query, 3) — Calls the vector
                      store similarity search. Returns top-3 results.
           Line 45-47: If no results, returns "No relevant information found
                      in uploaded documents for: {query}"
           Line 49-62: Formats the results:
                      Line 50:  "Found {n} relevant sections:\n\n"
                      Line 52-59: For each result Document:
                                 "--- Section {i+1} (from: {filename}) ---\n"
                                 "{chunk text}\n\n"
                                 Extracts filename from metadata (defaults to
                                 "unknown"), and content from doc.getText().
           Line 64-67: On exception, logs and returns error message.
```

---

## 12. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Client (HTTP / MCP)                         │
└───────────┬─────────────────────────────────────┬───────────────────┘
            │ REST API                             │ JSON-RPC 2.0
            ▼                                     ▼
┌───────────────────────┐             ┌───────────────────────────────┐
│   AgentController     │             │   McpServerController         │
│   /api/agent/chat     │             │   POST /mcp (Streamable HTTP) │
│   /api/agent/session  │             │   GET  /mcp/sse (SSE)        │
│   /api/agent/tools    │             │   POST /mcp/message          │
│   /api/health         │             │                               │
└───────────┬───────────┘             └───────────┬───────────────────┘
            │                                     │
            │ chat()                               │ tools/call dispatch
            ▼                                     ▼
┌───────────────────────┐             ┌───────────────────────────────┐
│     ReActAgent        │             │   Tool Implementations         │
│  (Reason+Act Loop)    │             │                               │
│  - buildSystemPrompt  │◄────────────│  WeatherTool  (wttr.in)       │
│  - buildContext       │  Tool repo  │  NewsTool     (NewsAPI)        │
│  - buildIterationPrompt│  + dispatch │  CalculatorTool (math eval)  │
│  - parse LLM output   │             │  DatabaseTool  (hardcoded KB)│
│  - max 10 iterations  │             │  RAGTool       (vector store) │
└─────┬──────┬──────────┘             └───────┬──────────┬────────────┘
      │      │                                │          │
      │      │ saveMessage                    │          │ search
      │      ▼                                │          ▼
      │  ┌────────────────┐                  │  ┌───────────────────────┐
      │  │ AgentMemory    │                  │  │ DocumentIngestion     │
      │  │ Service        │                  │  │ Service               │
      │  │ (Redis Lists)  │                  │  │ (Tika → Split → Embed)│
      │  └───────┬────────┘                  │  └───────┬───────────────┘
      │          │                            │          │
      │          ▼                            │          ▼
      │     ┌─────────┐                      │    ┌─────────────────┐
      │     │  Redis  │◄─── cache (DatabaseTool)   │  SimpleVector   │
      │     │  :6380  │                           │  Store          │
      │     └─────────┘                           │  (nomic-embed)  │
      │                                           └────────┬────────┘
      │ chat()                                             │ embed
      ▼                                                    ▼
┌────────────────┐                                  ┌───────────┐
│ OllamaService  │──── HTTP POST /api/chat ─────────►│  Ollama   │
│ (RestTemplate) │◄─── JSON response ────────────────│  :11434   │
└────────────────┘    (qwen3.5:4b for chat)          │           │
                                                      │ nomic-   │
┌────────────────┐  KafkaTemplate.send()              │ embed-   │
│KafkaEventPublisher│────────────────────────────────►│ text for  │
│(fire-and-forget) │  ai-agent-chat topic             │ embedding │
│                  │  ai-agent-events topic            └───────────┘
└────────────────┘
```

**Data flow for a typical chat request:**

1. Client sends `POST /api/agent/chat` with `{ "message": "What's the weather in London?" }`
2. `AgentController` generates a session ID, publishes a Kafka event
3. `ReActAgent.run()` is called:
    - Loads conversation history from Redis
    - Builds system prompt (tool descriptions) and context (history + message)
    - Sends prompt to `OllamaService.chat()` → Ollama LLM
    - LLM responds: "Thought: I should check the weather\nAction: weather\nInput: London"
    - `ReActAgent` parses the Action, looks up `WeatherTool` via `ToolRegistry`
    - `WeatherTool.execute("London")` calls wttr.in API, returns weather data
    - Observation is recorded in `AgentState`
    - Next iteration: LLM gets the observation and produces "Final Answer: ..."
    - Answer is saved to Redis and returned to the client

---

## Quick Reference

| Endpoint                          | Method | Description               |
|-----------------------------------|--------|---------------------------|
| `/api/agent/chat`                 | POST   | Chat with the AI agent    |
| `/api/agent/session/{id}/history` | GET    | Get conversation history  |
| `/api/agent/session/{id}`         | DELETE | Clear conversation memory |
| `/api/agent/tools`                | GET    | List all available tools  |
| `/api/health`                     | GET    | Health check              |
| `/api/documents/upload`           | POST   | Upload a document         |
| `/api/documents`                  | GET    | List ingested documents   |
| `/api/documents/{docId}`          | DELETE | Delete a document         |
| `/api/documents/search`           | GET    | Search documents          |
| `/mcp`                            | POST   | MCP Streamable HTTP       |
| `/mcp/sse`                        | GET    | MCP SSE transport         |
| `/mcp/message`                    | POST   | MCP SSE message endpoint  |

| Config Property                    | Default                  | Description                        |
|------------------------------------|--------------------------|------------------------------------|
| `app.ollama.base-url`              | `http://localhost:11434` | Ollama server URL                  |
| `app.ollama.model`                 | `qwen3.5:4b`             | Chat model name                    |
| `app.ollama.timeout`               | `120`                    | Request timeout (seconds)          |
| `app.ollama.max-tokens`            | `2048`                   | Max output tokens (unused)         |
| `app.agent.max-iterations`         | `10`                     | Max ReAct loop iterations          |
| `app.agent.memory-size`            | `20`                     | Memory size (unused, code uses 50) |
| `spring.ai.ollama.embedding.model` | `nomic-embed-text`       | Embedding model                    |
