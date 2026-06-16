# RAG MCP Setup

## Issue: ChromaDB HTTP/2 ↔ HTTP/1.1 Protocol Mismatch

Spring AI 1.0.8's ChromaDB client attempts HTTP/2/WebSocket upgrades, but ChromaDB 0.4.x/0.5.x Docker images only support HTTP/1.1. This caused:

- `"Unsupported upgrade request"` in ChromaDB logs
- `"Invalid HTTP request received"` after failed upgrade
- Uploads failing with HTTP 500

## Fix: Switched to SimpleVectorStore

Replaced ChromaDB with Spring AI's in-memory `SimpleVectorStore`.

### Changes

| File | Change |
|------|--------|
| `pom.xml` | `spring-ai-starter-vector-store-chroma` → `spring-ai-vector-store` |
| `application.yml` | Removed `spring.ai.vectorstore.chroma.*`, added `spring.ai.vectorstore.simple.initialize-schema: true` |
| `config/ChromaInitializer.java` | Deleted (not needed) |
| `config/SimpleVectorStoreConfig.java` | New — creates `SimpleVectorStore` bean |
| `service/DocumentIngestionService.java` | Removed `@DependsOn("chromaInitializer")` |

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

| Tool | Description |
|------|-------------|
| `get_weather` | Get current weather for a city |
| `get_news` | Get latest news headlines for a topic |
| `calculate` | Evaluate mathematical expressions |
| `query_database` | Query the knowledge base for information |
| `rag_search` | Search uploaded documents for relevant information |
| `upload_document` | Upload a document (PDF, DOCX, TXT, etc.) for RAG search |

### Note

SimpleVectorStore is in-memory — data is lost on restart. For persistence, add PostgreSQL with PgVector or re-add ChromaDB with a compatible HTTP/1.1 client config.

---

# CVE Vulnerability Fixes (June 10, 2026)

## Status: In Progress

All dependency version overrides applied to `pom.xml` — build compiles. OWASP Dependency-Check scan was running but was interrupted (NVD DB download ~20% through 357K records on second attempt).

## Fixes Applied

| Dependency | Before | After | CVEs |
|---|---|---|---|
| jackson-databind (`jackson-bom.version`) | 2.21.2 | 2.21.4 | CVE-2026-24400 |
| logback (`logback.version`) | 1.5.32 | 1.5.34 | CVE-2026-9828 |
| snakeyaml (`snakeyaml.version`) | 2.4 | 2.6 | CVE-2022-1471 |
| Tomcat (`tomcat.version`) | 10.1.53 | 10.1.55 | CVE-2026-41284, 43512, 43515, 34486, 34500, 43514, 34483 |
| Bouncy Castle (`dependencyManagement`) | 1.80 | 1.84 | CVE-2026-5598 (CVSS 8.9) |
| snappy-java (`dependencyManagement`) | 1.1.10.5 | 1.1.10.8 | CVE-2023-34453~55, CVE-2023-43642 |
| OWASP plugin added | — | 12.1.0 | `failBuildOnCVSS=8` |
| `dependency-check-suppressions.xml` | — | stub file | — |

## To Resume (Tomorrow)

1. **Finish OWASP scan** — first run downloads all 357K NVD records (~20-40 min with API key):
   ```bash
   mvn dependency-check:check -DnvdApiKey="3660af2b-d50e-492d-a185-e9ad4b2531ee"
   ```
   The NVD DB is cached in `~/.dependency-check/data/` after first download — subsequent runs are fast.
   
2. **Full package build** — verify `mvn package -DskipTests` passes.

3. **Known issue**: dependency-check 12.1.0 has a DB schema issue with `reference.URL` column (1000 chars) vs newer NVD URLs (1500+ chars). If the scan errors on this, either:
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
