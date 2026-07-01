# CLAUDE.md

This file provides guidance for Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring AI agent is a ReAct-style AI agent built with Spring Boot 4.1.0 + Spring AI 2.0.0. It uses:
- **Ollama** - Local LLM inference (llama3.2:3b)
- **NVIDIA** - Cloud LLM inference (stepfun-ai/step-3.5-flash) with automatic fallback
- **PostgreSQL + pgvector** - Vector storage for RAG with HNSW indexing
- **Kafka** - Event streaming and logging (optional, graceful degradation via NoOpKafkaEventPublisher)
- **JWT Authentication** - Stateless token-based auth
- **MCP Server** - Model Context Protocol for tool integration (SSE + Streamable HTTP)

The agent operates through a reasoning + acting loop where the LLM proposes actions, tools are executed, observations are recorded, and the process repeats until a final answer is produced.

## Build & Test

```bash
# Build
mvn clean package -DskipTests

# Run tests (requires Java 21+)
mvn test

# Run locally
java -jar target/ai-agent-0.0.1-SNAPSHOT.jar

# Docker
docker-compose up -d
```

## Key Architecture Decisions

- **Dual AI provider** with `AiProviderChain` fallback: configured via `app.ai.provider` (ollama/nvidia)
- **PgVectorStore** with 768-dim embeddings, HNSW index, cosine distance
- **Optional Kafka**: `NoOpKafkaEventPublisher` (`@Primary`) takes over when Kafka is unavailable
- **Tool filtering**: Client can send `toolsEnabled` list to restrict available tools per request
- **MCP endpoints** (`/mcp/**`) are unauthenticated (permitted in SecurityConfig)
- **Global exception handler** (`@ControllerAdvice`) prevents stack trace leakage
