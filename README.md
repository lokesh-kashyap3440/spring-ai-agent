# Spring Boot AI Agent

AI Agent with Ollama/NVIDIA (LLM), PostgreSQL+pgvector (Vectors), Kafka (Events), MCP Server, JWT Auth

## Features

- **Dual AI Providers** - Ollama (local) + NVIDIA (cloud) with automatic fallback
- **Circuit Breakers** - Resilience4j circuit breakers per provider with automatic failover
- **PostgreSQL + pgvector** - Vector storage for RAG with HNSW indexing
- **Kafka** - Event streaming and logging (optional, graceful degradation)
- **Redis** - Distributed rate limiting (multi-instance scale-out)
- **ReAct Pattern** - Reasoning + Acting agent loop
- **6 Tools** - Weather, News, Calculator, Knowledge Base, RAG Search, Document Upload
- **MCP Server** - Model Context Protocol compatible tool server (SSE + Streamable HTTP) with strict JWT authentication
- **JWT Authentication** - Registration and login with stateless tokens; token-to-client binding (IP + User-Agent hashed)
- **Distributed Rate Limiting** - Redis-backed rate limiting with in-memory fallback
- **RBAC** - Role-based access control for document endpoints (`/api/documents/**` requires `USER` or `ADMIN` role)
- **Swagger/OpenAPI** - Interactive API documentation
- **Dark-themed Web UI** - Chat interface with tool toggles

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts: PostgreSQL (pgvector), Kafka (KRaft), Ollama, Redis (rate limiting), and the app.

### 2. Pull Ollama Models

```bash
sleep 30
docker exec ollama ollama pull llama3.2:3b
docker exec ollama ollama pull nomic-embed-text
```

### 3. Build and Run

```bash
mvn clean install -DskipTests
java -jar target/ai-agent-0.0.1-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

### 4. Test

```bash
curl -X POST http://localhost:8082/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in London?", "sessionId": "test"}'
```

## Configuration

All configuration is via environment variables with sensible defaults. See `application.yml` for the full reference.

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8082` | Application port |
| `AI_PROVIDER` | `ollama` | AI provider: `ollama` or `nvidia` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2:3b` | Chat model |
| `NVIDIA_API_KEY` | — | NVIDIA API key (required for nvidia provider) |
| `NVIDIA_MODEL` | `stepfun-ai/step-3.5-flash` | NVIDIA model |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/aiagent` | PostgreSQL URL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` | Kafka brokers |
| `KAFKA_REPLICATION_FACTOR` | `1` | Kafka topic replication factor (3 for production) |
| `SPRING_REDIS_HOST` | `localhost` | Redis host (for distributed rate limiting) |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | — | JWT signing secret (required) |
| `AGENT_MAX_ITERATIONS` | `6` | Max ReAct loop iterations |
| `AGENT_MAX_CONTEXT_TOKENS` | `3000` | Max context tokens for the AI provider |
| `RAG_TOP_K` | `5` | Number of RAG search results |
| `RAG_SIMILARITY_THRESHOLD` | `0.5` | Minimum similarity for RAG |
| `RAG_RERANKING_ENABLED` | `true` | Enable keyword-based reranking of RAG results |

## API Endpoints

### Agent API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/agent/chat` | Send a message to the agent |
| GET | `/api/agent/session/{sessionId}/history` | Get conversation history |
| DELETE | `/api/agent/session/{sessionId}` | Clear session memory |
| GET | `/api/agent/tools` | List available tools |
| GET | `/api/health` | Health check |

### Auth API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and get JWT token |

### Document API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/documents/upload` | Upload a document (PDF, DOCX, TXT) |
| GET | `/api/documents` | List all documents |
| DELETE | `/api/documents/{docId}` | Delete a document |
| GET | `/api/documents/search?query=...` | Semantic search |

### MCP Server API (Authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/mcp/sse` | SSE connection (requires Bearer JWT) |
| POST | `/mcp` | Streamable HTTP (JSON-RPC 2.0, requires Bearer JWT) |

#### MCP Tools Available

| Tool | Description | Input |
|------|-------------|-------|
| `get_weather` | Get current weather | `{"city": "London"}` |
| `get_news` | Get news headlines | `{"topic": "technology"}` |
| `calculate` | Evaluate math | `{"expression": "2 + 2"}` |
| `query_knowledge_base` | Query knowledge base | `{"query": "What is Spring Boot?"}` |
| `rag_search` | Search uploaded documents | `{"query": "refund policy"}` |
| `upload_document` | Upload a document | `{"filename": "...", "content": "...", "contentType": "..."}` |

### Swagger UI

- **UI**: http://localhost:8082/swagger-ui.html
- **JSON**: http://localhost:8082/v3/api-docs

## Example Requests

### Chat

```bash
curl -X POST http://localhost:8082/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Calculate 15 * 23", "sessionId": "math-session"}'
```

### Chat with Tool Filtering

```bash
curl -X POST http://localhost:8082/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather?", "toolsEnabled": ["weather"]}'
```

### Upload Document

```bash
curl -X POST http://localhost:8082/api/documents/upload \
  -F "file=@document.pdf"
```

### Search Documents

```bash
curl "http://localhost:8082/api/documents/search?query=refund+policy"
```

## Architecture

```
User Request
    ↓
┌─────────────────────────────────────┐
│  Spring Security Filter Chain       │
│  • JWT Auth (IP+UA bound)           │
│  • Rate Limiting (Redis-backed)     │
│  • RBAC (USER/ADMIN roles)          │
└─────────────────────────────────────┘
    ↓
Controller (REST API / MCP Server / Auth)
    ↓
┌─────────────────────────────────────┐
│  Resilience4j Circuit Breaker        │
│  • OllamaService (50% fail threshold) │
│  • NvidiaService (50% fail threshold)  │
│  • LmStudioService (50% fail threshold)│
│  • Auto-fallback to next provider    │
└─────────────────────────────────────┘
    ↓
ReActAgent (Orchestrator)
    ↓
AiProviderChain → OllamaService / NvidiaService / LmStudioService (LLM)
    ↓
ToolRegistry → Tools (Weather, News, Calculator, KnowledgeBase, RAG, Upload)
    ↓
AgentMemoryService (PostgreSQL)
    ↓
KafkaEventPublisher (Event Logging)
    ↓
Response
```

## Project Structure

```
spring-ai-agent/
├── docker-compose.yml
├── pom.xml
├── .env.example
└── src/
    ├── main/
    │   ├── java/com/example/aiagent/
    │   │   ├── AiAgentApplication.java
    │   │   ├── agent/ReActAgent.java
    │   │   ├── config/
    │   │   │   ├── AgentConfig.java
    │   │   │   ├── AppConfig.java
    │   │   │   ├── KafkaConfig.java
    │   │   │   ├── NvidiaConfig.java
    │   │   │   ├── OllamaConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── PgVectorStoreConfig.java
    │   │   │   └── RagConfig.java
    │   │   ├── controller/
    │   │   │   ├── AgentController.java
    │   │   │   ├── AuthController.java
    │   │   │   └── DocumentController.java
    │   │   ├── mcp/McpServerController.java
    │   │   ├── memory/AgentMemoryService.java
    │   │   ├── model/
    │   │   │   ├── AgentState.java
    │   │   │   ├── ChatRequest.java
    │   │   │   ├── ChatResponse.java
    │   │   │   └── DocumentInfo.java
    │   │   ├── security/
    │   │   │   ├── AuthRequest.java
    │   │   │   ├── JwtAuthFilter.java
    │   │   │   ├── JwtUtil.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── User.java
    │   │   │   └── UserRepository.java
    │   │   ├── service/
    │   │   │   ├── AiProviderChain.java
    │   │   │   ├── DocumentIngestionService.java
    │   │   │   ├── KafkaEventPublisher.java
    │   │   │   ├── NvidiaService.java
    │   │   │   └── OllamaService.java
    │   │   └── tools/
    │   │       ├── CalculatorTool.java
    │   │       ├── DatabaseTool.java
    │   │       ├── NewsTool.java
    │   │       ├── RAGTool.java
    │   │       ├── Tool.java
    │   │       ├── ToolRegistry.java
    │   │       └── WeatherTool.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── schema.sql
    │       └── static/index.html
    └── test/java/com/example/aiagent/
        └── (30 test files)
```

## Requirements

- Java 21+ (or 25 for full features)
- Maven 3.8+
- Docker & Docker Compose
- Ollama (running natively or in Docker)
