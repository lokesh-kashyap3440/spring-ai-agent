# Spring Boot AI Agent

AI Agent with Ollama (Local LLM), Redis (Memory), Kafka (Events), MCP Server

## Features

- **Ollama** - Local LLM (Free, runs on your machine)
- **Redis** - Session memory and caching
- **Kafka** - Event streaming and logging
- **ReAct Pattern** - Reasoning + Acting agent loop
- **4 Tools** - Weather, News, Calculator, Database
- **MCP Server** - Model Context Protocol compatible tool server
- **REST API** - Simple chat interface
- **Swagger** - API documentation

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

### 2. Pull Ollama Model

```bash
sleep 30
ollama pull llama3.1:8b
```

### 3. Build and Run

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### 4. Test

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the weather in London?", "sessionId": "test"}'
```

## API Endpoints

### Agent API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/agent/chat` | Send a message to the agent |
| GET | `/api/agent/session/{sessionId}/history` | Get conversation history |
| DELETE | `/api/agent/session/{sessionId}` | Clear session memory |
| GET | `/api/agent/tools` | List available tools |
| GET | `/api/health` | Health check |

### MCP Server API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/mcp/message` | MCP JSON-RPC endpoint |

#### MCP Tools Available

| Tool | Description | Input |
|------|-------------|-------|
| `get_weather` | Get current weather | `{"city": "London"}` |
| `get_news` | Get news headlines | `{"topic": "technology"}` |
| `calculate` | Evaluate math | `{"expression": "2 + 2"}` |
| `query_database` | Query knowledge base | `{"query": "What is Spring Boot?"}` |

#### MCP Client Config

```json
{
  "mcpServers": {
    "ai-agent": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

### Swagger UI

- **UI**: http://localhost:8080/swagger-ui.html
- **JSON**: http://localhost:8080/v3/api-docs

## Example Requests

### Chat
```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Calculate 15 * 23", "sessionId": "math-session"}'
```

### MCP Tool Call
```bash
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "get_weather",
      "arguments": {"city": "London"}
    }
  }'
```

### List MCP Tools
```bash
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

## Architecture

```
User Request
    ↓
Controller (REST API / MCP Server)
    ↓
ReActAgent (Orchestrator)
    ↓
OllamaService (LLM)
    ↓
ToolRegistry → Tools (Weather, News, Calculator, Database)
    ↓
AgentMemoryService (Redis)
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
├── README.md
└── src/main/
    ├── java/com/example/aiagent/
    │   ├── AiAgentApplication.java
    │   ├── agent/
    │   │   └── ReActAgent.java
    │   ├── config/
    │   │   ├── AgentConfig.java
    │   │   ├── AppConfig.java
    │   │   └── OllamaConfig.java
    │   ├── controller/
    │   │   └── AgentController.java
    │   ├── mcp/
    │   │   └── McpServerController.java
    │   ├── memory/
    │   │   └── AgentMemoryService.java
    │   ├── model/
    │   │   ├── AgentState.java
    │   │   ├── ChatRequest.java
    │   │   └── ChatResponse.java
    │   ├── service/
    │   │   ├── KafkaEventPublisher.java
    │   │   └── OllamaService.java
    │   └── tools/
    │       ├── CalculatorTool.java
    │       ├── DatabaseTool.java
    │       ├── NewsTool.java
    │       ├── Tool.java
    │       ├── ToolRegistry.java
    │       └── WeatherTool.java
    └── resources/
        └── application.yml
```

## Requirements

- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- Ollama (running natively)
