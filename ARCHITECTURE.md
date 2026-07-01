# Spring AI Agent Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MCP Clients                                    │
│                    (JSON-RPC over SSE / HTTP POST)                          │
└────────────────────────────────┬────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  Spring Boot Application (Port 8082)                        │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    McpServerController                                │ │
│  │  Endpoints:                                                           │ │
│  │  • GET  /mcp/sse          (Server-Sent Events)                       │ │
│  │  • POST /mcp              (JSON-RPC: initialize, tools/list, call)   │ │
│  │  • POST /mcp/message      (Session-based messages)                   │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                      │                                      │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                        ReActAgent                                     │ │
│  │  Pattern: Thought → Action → Observation → Final Answer              │ │
│  │  Max Iterations: 10 (configurable)                                   │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│           │              │                  │                               │
│           ▼              ▼                  ▼                               │
│  ┌────────────────┐ ┌─────────────────┐ ┌─────────────────────────────┐   │
│  │ ToolRegistry   │ │AgentMemoryService│ │   AiService (NVIDIA/Ollama)│   │
│  │                │ │                 │ │                             │   │
│  │ Registers all  │ │ JDBC-backed     │ │ HTTP Client to LLM         │   │
│  │ available tools│ │ - Chat history  │ │ - Default: NVIDIA          │   │
│  │                │ │ - Session state │ │ - Fallback: Ollama         │   │
│  │                │ │ - Memory size:20│ │                             │   │
│  └───────┬────────┘ └─────────────────┘ └─────────────────────────────┘   │
│          │                                                                │
│          ▼                                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                         Tools                                       │  │
│  │  ┌────────────┐ ┌──────────┐ ┌───────────┐ ┌────────────┐          │  │
│  │  │WeatherTool │ │NewsTool  │ │Calculator │ │DatabaseTool│          │  │
│  │  │            │ │          │ │Tool       │ │            │          │  │
│  │  │get_weather │ │get_news  │ │calculate  │ │query_database│        │  │
│  │  └────────────┘ └──────────┘ └───────────┘ └────────────┘          │  │
│  │  ┌────────────┐ ┌──────────────────────────────────┐               │  │
│  │  │ RAGTool    │ │   DocumentIngestionService       │               │  │
│  │  │            │ │   (Tika + TokenTextSplitter)      │               │  │
│  │  │rag_search  │ │                                   │               │  │
│  │  └────────────┘ └──────────────────────────────────┘               │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│          │                                                                │
│          ▼                                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │              PgVectorStore (PostgreSQL + pgvector)                   │  │
│  │  • Embeddings: nomic-embed-text (via Ollama)                       │  │
│  │  • Document chunks persisted in Postgres                           │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │              KafkaEventPublisher (optional)                          │  │
│  │  Topics:                                                            │  │
│  │  • ai-agent-events  (agent actions, tool calls)                    │  │
│  │  • ai-agent-chat    (chat messages)                                │  │
│  └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
          │                    │                     │
          │                    │                     │
          ▼                    ▼                     ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────────┐
│    PostgreSQL   │ │     Kafka       │ │        Ollama               │
│   (Local/Prod)  │ │   (Optional)    │ │      (Port 11434)           │
│                 │ │   (Port 9093)   │ │                             │
│ • Vector store  │ │                 │ │ • LLM: qwen3.5:4b          │
│ • Chat history  │ │ • Event logging │ │ • Embedding: nomic-embed   │
│ • Doc metadata  │ │ • Audit trail   │ │ • Local inference          │
└─────────────────┘ └─────────────────┘ └─────────────────────────────┘
```

## Component Flow

### 1. Request Flow

```
MCP Client → MCP Server Controller → ReAct Agent → Tool Registry → Tool Execution
     │                                                                      │
     │                              ┌───────────────────────────────────────┘
     │                              │
     └──── SSE Events ◄─────────────┘
```

### 2. RAG Flow

```
Upload Document → DocumentIngestionService → Tika Parser → Text Splitter
                                                        │
                                                        ▼
                                          Ollama Embeddings (nomic-embed-text)
                                                        │
                                                        ▼
                                          SimpleVectorStore (in-memory index)
                                                        │
                                                        ▼
User Query → RAGTool → Vector Search → Top-K Chunks → LLM Context → Answer
```

### 3. ReAct Agent Loop

```
User Message
     │
     ▼
Thought: "What do I need to do?"
     │
     ▼
Action: tool_name
Input: tool_input
     │
     ▼
Observation: tool_result
     │
     ▼
[Repeat up to 10 iterations]
     │
     ▼
Final Answer: response to user
```

## Key Configuration

| Component   | Port/URL        | Purpose                     |
|-------------|-----------------|-----------------------------|
| Spring Boot | 8082            | Main application            |
| MCP SSE     | GET /mcp/sse    | Real-time event stream      |
| MCP HTTP    | POST /mcp       | JSON-RPC requests           |
| Redis       | localhost:6380  | Agent memory & chat history |
| Kafka       | localhost:9093  | Event publishing            |
| Ollama      | localhost:11434 | LLM & embeddings            |

## MCP Tools

| Tool              | Description               | Input                                         |
|-------------------|---------------------------|-----------------------------------------------|
| `get_weather`     | Current weather for city  | `city`                                        |
| `get_news`        | News headlines by topic   | `topic`                                       |
| `calculate`       | Math expression evaluator | `expression`                                  |
| `query_database`  | Knowledge base search     | `query`                                       |
| `rag_search`      | Document semantic search  | `query`                                       |
| `upload_document` | Upload PDF/DOCX/TXT       | `filename`, `content` (base64), `contentType` |

## Security Notes

- All dependencies updated for CVE fixes (June 2026)
- OWASP Dependency-Check: CVSS threshold = 8
- Suppressions: `dependency-check-suppressions.xml`