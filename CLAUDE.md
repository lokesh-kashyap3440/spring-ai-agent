# CLAUDE.md

This file provides guidance for Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring AI agent is a ReAct-style AI agent built with Spring Boot 4.1.0 + Spring AI 2.0.0. It uses:
- **Ollama** - Local LLM inference (llama3.2:3b)
- **NVIDIA** - Cloud LLM inference (stepfun-ai/step-3.5-flash) with automatic fallback
- **PostgreSQL + pgvector** - Vector storage for RAG with HNSW indexing
- **Kafka** - Event streaming and logging (optional, graceful degradation via NoOpKafkaEventPublisher)
- **JWT Authentication** - Stateless token-based auth (15-min access tokens, 7-day refresh tokens)
- **MCP Server** - Model Context Protocol for tool integration (SSE + Streamable HTTP), now JWT-authenticated
- **Micrometer Metrics** - Agent loop timing, per-tool counters, prometheus endpoint

The agent operates through a reasoning + acting loop where the LLM proposes actions, tools are executed, observations are recorded, and the process repeats until a final answer is produced.

## Architecture

Core components are programmed to interfaces for testability:
- `ToolRegistry` (interface) / `DefaultToolRegistry` — tool discovery and filtering
- `AgentMemory` (interface) / `AgentMemoryService` — conversation persistence via JDBC
- `AiService` (interface) / `AiProviderChain` — dual-provider LLM abstraction
- `ReActAgent` delegates prompt building to `PromptBuilder`
- Agent state tracks `Step` records (thought + action + observation) instead of parallel lists
- Tool name constants live in `ToolNames.java` (no more magic strings)

## Recent Major Refactoring (July 3, 2026)

See `AGENTS.md` for the full changelog. Key changes:
- Security hardening across all endpoints (MCP auth, rate limiting, password policy, RBAC)
- ReActAgent decomposed into focused classes (`PromptBuilder`, `Step` records)
- MCP server driven dynamically from `ToolRegistry` instead of hardcoded switch statements
- AI provider fallback now works (both providers registered, chain-of-responsibility pattern)
- RAG pipeline with keyword reranking, context window management, externalized prompts
- Micrometer metrics and token usage tracking added
- CalculatorTool uses SpEL for correct operator precedence
- All 181 tests pass

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
