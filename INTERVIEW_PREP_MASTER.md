# Senior Java Solution Architect — Interview Preparation Guide

> **Prepared for:** Lokesh Kashyap (spring-ai-agent project)
> **Target Role:** Senior Java Solution Architect
> **Date:** July 8, 2026

---

## Table of Contents

1. 🏗️ [Whiteboard Session — Enterprise Document Intelligence Platform](#section-1)
2. 📝 [Mock Interview — 7 Questions With Scoring Guide](#section-2)
3. ☁️ [AWS Study Guide — Spring Boot on AWS Deployment Plan](#section-3)
4. 🔐 [Security Architecture Walkthrough](#section-4)
5. 🎯 [Architecture Decision Records (ADRs)](#section-5)

---

## Quick Start: Your JD → Experience Mapping

| JD Requirement | Your Proof Points | Key Quote For Interview |
|---|---|---|
| 12+ years Java & Spring Boot | Spring Boot 4.1.0, Spring AI 2.0.0, upgraded from 3.5.15 to 4.1.0 | "Led coordinated major-version upgrade across Spring Boot + Spring AI, handling breaking changes in RestTemplateBuilder, test autoconfigure, and property binding." |
| Enterprise-scale distributed apps | Multi-service: PostgreSQL+pgvector, Kafka, Ollama, MCP, Docker Compose, graceful degradation | "Designed multi-service architecture with vector DB, event streaming, LLM inference, and MCP protocol — each with graceful degradation when dependencies fail." |
| Microservices Architecture | Interface-driven: AiService/AiProviderChain, ToolRegistry/DefaultToolRegistry, AgentMemory, KafkaEventPublisher | "Architected around interface-driven decomposition — Chain of Responsibility for AI providers, Strategy pattern for tools, event-driven Kafka for async processing." |
| AWS Cloud | _(Study — no AWS in project but strong containerization)_ | "Containerized with Docker + Compose; designed for cloud-agnostic deployment. See Section 3 for full AWS migration mapping." |
| Architecture documentation & governance | ARCHITECTURE.md, AGENTS.md, CLAUDE.md, OWASP Dependency-Check | "Produce multi-level artifacts: architecture diagrams, security audit trails, phased migration docs, and CVE governance with automated dependency scanning." |
| Scalability, security, resiliency, performance | HNSW indexing, context window management, JWT auth, rate limiting, RBAC, dual AI provider fallback, Micrometer metrics | "Built with defense-in-depth: JWT with IP binding and 15-min rotation, rate limiting per IP, graceful degradation on every external dependency, and Micrometer-backed performance monitoring." |
| Codebase review & reverse engineering | 4-phase refactoring: Redis/ChromaDB -> PgVector, monolithic ReActAgent -> decomposed components, CVE remediation | "Led a 4-phase refactoring: reverse-engineered a monolithic agent into PromptBuilder, Step records, and ToolRegistry interface — 181 tests pass post-migration." |
| Stakeholder communication & leadership | Phased execution, trade-off docs, ADRs, risk mitigation | "Owned architectural decisions across security, performance, and maintainability dimensions — presented trade-offs, documented rationale in AGENTS.md, guided team through execution." |

---

## Study Roadmap (30 Days)

| Week | Focus | Actions |
|---|---|---|
| **1** | **AWS Deep Dive** | Study ECS, RDS (pgvector docs), SQS, Secrets Manager, WAF, CloudWatch. Practice: "How would I deploy this project on AWS?" (Section 3) |
| **2** | **Microservices Theory** | Read: Building Microservices (Sam Newman) — decomposition, communication patterns, testing, CI/CD. Martin Fowler: "Microservices" and "Strangler Fig" articles. |
| **3** | **Solution Architecture Fundamentals** | Study: TOGAF fundamentals, C4 model for diagrams, ADR format (Section 5). Practice presenting your 8 ADRs. |
| **4** | **Interview Drills** | Practice whiteboarding: design a payment system, order processing, real-time analytics. Use C4 diagrams (Section 1). Run through the 7 mock questions (Section 2). |


---

## Section 1: 🏗️ Whiteboard Session — Enterprise Document Intelligence Platform

# SECTION 1: System Design Interview Preparation
## Whiteboard Session -- Enterprise Document Intelligence Platform

---

## 1. Problem Statement (As the Interviewer Would Present It)

"Thank you for joining us today. We are going to do a system design exercise.

I would like you to design an **Enterprise Document Intelligence Platform** -- a system that ingests, indexes, and enables intelligent querying over millions of documents across multiple tenants.

Your starting point is a single-node Spring AI Agent project you have already built. Here is what I know about it:

- Spring Boot 4.1.0 + Spring AI 2.0.0, ReAct pattern (Thought -> Action -> Observation -> Final Answer)
- PostgreSQL with pgvector (768-dim, HNSW indexing, cosine distance)
- Dual AI providers: NVIDIA (primary) with Ollama (local fallback) via chain-of-responsibility
- Kafka for event streaming with graceful NoOp degradation
- JWT authentication (15-min access tokens, 7-day refresh tokens, IP binding)
- MCP Server (Model Context Protocol) over SSE and Streamable HTTP
- Tika document parsing, TokenTextSplitter, embedding, vector search with keyword reranking
- Micrometer metrics + Prometheus endpoint
- Rate limiting (5 req/min per IP on auth), RBAC (USER/ADMIN)
- Docker Compose orchestration (pgvector, Kafka, Ollama, Spring Boot app)

Here is where it gets interesting. You now face these enterprise requirements:

**Business Context:** Your startup has landed three enterprise clients, each with 50,000-500,000 employees. They want to ingest their internal knowledge bases -- procurement policies, HR handbooks, legal contracts, technical documentation, research papers -- and let employees ask natural language questions. Documents arrive as PDFs, scanned images, Office documents, plain text. Some are 10 pages; some are 10,000 pages.

**Multi-Tenancy:** Each tenant's data must be strictly isolated. Tenant A's HR team should never see Tenant B's legal contracts. Some documents are shared across departments within a tenant; others are restricted to specific user groups.

**Real-Time Collaboration:** Three analysts should be able to simultaneously upload documents, edit metadata, create shared RAG contexts (curated collections of documents), and see each other's changes within seconds.

**Compliance and Audit:** GDPR requires the right to be forgotten -- deleting a user must delete their data within 30 days. SOC2 requires an immutable audit trail of every document access and every LLM inference. Legal hold must prevent deletion of specified documents.

**High Availability:** The system must survive a full AWS us-east-1 region failure and continue serving from us-west-2 with zero data loss and RTO under 5 minutes. RPO is 60 seconds.

**Cost Optimization:** Each LLM inference costs money. With 500,000 employees querying the system daily, inference costs dominate the budget. You need to cache answers, batch similar queries, tier model quality by document importance, and use smaller models for straightforward lookups versus complex multi-hop reasoning.

**The CTO wants this in production in 12 weeks.** What do you build?"


---

## 2. Constraints and Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| F1 | Multi-tenant document ingestion (PDF, DOCX, images, TXT) | P0 |
| F2 | OCR for scanned documents and images | P0 |
| F3 | Semantic search across documents within tenant scope | P0 |
| F4 | Role-based access control within tenants (dept-level, user-level) | P0 |
| F5 | Real-time collaborative RAG context curation | P1 |
| F6 | Answer caching and query deduplication across tenants | P1 |
| F7 | Legal hold / eDiscovery hold on documents | P1 |
| F8 | User deletion with GDPR right-to-be-forgotten (30-day SLA) | P0 |
| F9 | Immutable audit trail for SOC2 (document access, LLM calls, admin actions) | P0 |
| F10 | Admin dashboard with per-tenant usage metrics, cost breakdown, latency SLAs | P2 |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| N1 | Multi-region HA (active-active) | RTO < 5 min, RPO < 60 sec |
| N2 | P99 latency for document search | < 3 seconds |
| N3 | P99 latency for LLM answer generation | < 10 seconds (with streaming) |
| N4 | Throughput | 10,000 concurrent users, 500 docs/min ingestion |
| N5 | Data isolation between tenants | Strict (encryption key per tenant) |
| N6 | Audit immutability | Append-only, cryptographically linked |
| N7 | Storage scalability | 100 TB+ across tenants |
| N8 | Cost efficiency | LLM query cost < /bin/bash.005 per answer at scale |

### Trade-offs to Call Out Early

1. **Consistency vs. Availability**: For collaborative RAG context editing, eventual consistency is acceptable (seconds). For document access logs (audit), strong consistency required.
2. **Freshness vs. Cost**: Re-embedding documents on every edit is expensive. Batch re-embedding with delta detection.
3. **Latency vs. Accuracy**: Complex multi-hop reasoning across 100+ documents needs agentic RAG (slower, more expensive) but simple fact lookups can use direct retrieval (faster, cheaper).
4. **Storage vs. Retrieval Speed**: HNSW indexes are fast but memory-hungry. For 100M+ vectors, disk-based IVF or product quantization may be needed.


---

## 3. Architecture Diagram (C4 Level 2 -- Container Diagram)

Below is a text-based C4 container diagram showing the system decomposed into services plus data flow.

```
ENTERPRISE DOCUMENT INTELLIGENCE PLATFORM (C4 Level 2 -- Containers)

  Web App          Mobile App       MCP SDK        3rd-party integrations
  (React/TS)       (Swift/KT)       (Python)       (Slack, Teams, SharePoint)
      |                |               |                     |
      +----------------+---------------+---------------------+
                       |
                       v
              API Gateway (Kong / AWS API Gateway)
         Rate limiting, auth, TLS, routing, tenant extraction
         us-east-1: api.example.com / us-west-2: api-usw2.example.com
         Route53 latency-based routing + health check failover
           |                                    |
           v                                    v
  +---------------------------+   +---------------------------+
  |  us-east-1 (Active)       |   |  us-west-2 (Active)       |
  |                           |   |                           |
  |  Auth Service             |   |  Auth Service             |
  |  JWT + OIDC/SAML          |   |  (same, stateless)        |
  |                           |   |                           |
  |  Ingestion Service        |   |  Ingestion Service        |
  |  Tika + OCR + Split       |   |  (reads from replicated)  |
  |                           |   |                           |
  |  RAG Orchestrator         |   |  RAG Orchestrator         |
  |  ReAct per tenant         |   |  (same, stateless)        |
  |                           |   |                           |
  |  Collaboration Service    |   |  Collaboration Service    |
  |  WebSocket + CRDT         |   |  (same, active-active)    |
  |                           |   |                           |
  |  Audit Service            |   |  Audit Service            |
  |  Append-only immutable    |   |  (same, writes cross-reg) |
  +---------------------------+   +---------------------------+
             |                              |
             +------------------------------+
                            |
                            v
  +----------------------------------------------------------+
  |                 DATA PLATFORM                             |
  |                                                          |
  |  Aurora PostgreSQL    pgvector          ElastiCache Redis |
  |  (Global DB)          768-dim HNSW     Session store,     |
  |  Tenants, users,      per-tenant       answer cache,      |
  |  doc metadata,        vector shards    pub/sub collab     |
  |  conversation mem                      rate limiter        |
  |                                                          |
  |  S3 / MinIO          OpenSearch         Kafka / MSK        |
  |  Document storage    Keyword search    Cross-region        |
  |  Object Lock for     Audit log index   event replication  |
  |  legal hold                                              |
  |                                                          |
  |  Prometheus + Grafana (per-tenant dashboards)             |
  +----------------------------------------------------------+
                            |
                            v
  +----------------------------------------------------------+
  |                  AI INFRASTRUCTURE LAYER                  |
  |                                                          |
  |  NVIDIA (Primary)    Ollama (Fallback)  Small Model      |
  |  step-3.5-flash      llama3.2:3b       Tier-2 answers,   |
  |  Complex reasoning   Local, no egress  classification,   |
  |  10k context         cost               summarization    |
  |                                                          |
  |  Embedding Service (nomic-embed-text / batch processing)  |
  +----------------------------------------------------------+
```

### Data Flow for a Single Query

```
User Query
   |
   v
API Gateway -> Auth Service (validate JWT, extract tenant_id from claims)
   |
   v
RAG Orchestrator (per-tenant ReAct agent instance)
   |
   +-- 1. Check Redis cache: exact semantic match? -> Return cached (cost: $0)
   |
   +-- 2. Embed query via Embedding Service
   |      |
   |      v
   |    pgvector (tenant-sharded): similarity search top-K
   |      |
   |      v
   |    Keyword reranking (OpenSearch BM25 hybrid score)
   |      |
   |      v
   |    Context window assembly (trim to fit LLM context budget)
   |      |
   |      v
   +-- 3. Determine answer tier:
   |      +-- Simple fact lookup (one chunk) --> Small model ($0.0005)
   |      +-- Multi-doc synthesis (2-5 chunks) --> NVIDIA tier-1 ($0.003)
   |      +-- Cross-doc reasoning (5+ chunks, ReAct loop) --> NVIDIA + agentic RAG ($0.01)
   |
   +-- 4. Generate answer with chosen model
   |
   +-- 5. Cache answer (TTL based on document volatility)
   |
   +-- 6. Log to Audit Service (who, what, when, which docs, which model)
   |
   +-- 7. Stream answer back to user via SSE
```


---

## 4. Decomposition -- Why These Services?

### Service Boundary Analysis

**1. Auth Service** (split from monolith)
- *Why separate?* Auth has different scaling characteristics (bursty login storms vs. steady query rate). JWT validation is stateless and can be fronted by CDN edge. OIDC/SAML federation adds complexity that doesn't belong in the RAG path. Separating auth means the RAG orchestrator never needs secret material in memory -- the JWT is validated at the gateway, and only the tenant_id claim is forwarded.
- *Secret zero-trust:* Auth service has the signing key. RAG orchestrator has a public key for validation only. Compromise of the orchestrator does not expose signing material.

**2. Ingestion Service** (split from monolith)
- *Why separate?* Document ingestion is I/O-bound (disk, network, OCR pipeline) and CPU-bound (Tika parsing, OCR, text splitting). It has very different resource requirements from the LLM inference path. Processing a single 10,000-page PDF can take minutes. This must be async -- the user uploads and gets a notification when indexing completes. The ingestion pipeline is a natural fit for Kafka-driven event sourcing: UploadEvent -> ParseEvent -> EmbedEvent -> IndexEvent.
- *Scaling:* Can be scaled independently to 50+ workers during backlog spikes. Uses SQS/SQS FIFO for exactly-once document processing ordering per tenant.

**3. RAG Orchestrator** (evolved from ReActAgent)
- *Why separate?* This is the core business logic -- the ReAct agent loop, tool execution, context assembly, and answer generation. It is CPU/memory-bound (LLM inference, embedding calls). It also has the strictest latency requirements. Separating it means it can be scaled for throughput without dragging along ingestion or auth overhead.
- *Stateless:* All state lives in Redis (session) and PostgreSQL (conversation memory). Instances are trivially horizontally scalable.

**4. Collaboration Service** (new)
- *Why separate?* Real-time collaboration requires persistent WebSocket connections and CRDT-based conflict resolution. This has a very different threading model (NIO event loops, not request-response). Mixing WebSocket handlers with the ReAct agent's blocking HTTP calls would cause thread pool starvation.
- *CRDT choice:* Using CRDTs (Conflict-free Replicated Data Types) for collaborative context curation means no central lock server needed. Each collaborator's edits merge automatically, even across regions.

**5. Audit Service** (new)
- *Why separate?* SOC2 requires an immutable, append-only audit log. This has unique storage requirements (write-once, never modify, time-based retention policies). It uses a separate database (OpenSearch for queryable audit, S3 with Object Lock for immutable storage). This service must never go down -- it is the system of record for compliance.
- *Cryptographic chaining:* Each audit entry includes a SHA-256 hash of the previous entry, forming a hash chain that prevents tampering.

### What Stays Together (and Why)

- **pgvector + PostgreSQL**: Keep vector store and relational data in the same Aurora cluster. Having separate databases adds cross-database consistency problems. Aurora Global Database handles cross-region replication natively.
- **LLM providers in one abstraction**: The `AiProviderChain` pattern (NVIDIA -> Ollama fallback) should remain a single library used by the RAG Orchestrator. The fallback logic is internal to the orchestrator.
- **Kafka for event bus**: Kafka remains the backbone for ingestion events, audit events, and cross-service communication. MSK with cross-region MirrorMaker ensures events are available in both regions.



---

## 5. Key Design Decisions with Trade-offs

### Decision 1: Tenant Isolation Strategy

**Option A: Database per tenant** -- Each tenant gets their own PostgreSQL database with pgvector.
- *Pros:* Strongest isolation. No cross-tenant data leakage possible. Independent backup/restore. Per-tenant performance tuning.
- *Cons:* Connection pool explosion. 500 tenants x 10 connections = 5000 connections. Cross-tenant analytics impossible. Schema migration must be rolled out N times. Higher operational cost.
- *Best for:* Highly regulated tenants (finance, healthcare) where physical separation is mandated.

**Option B: Schema per tenant** -- Shared database, separate schemas (tenant_1, tenant_2).
- *Pros:* Good isolation. Single connection pool. Independent migrations per tenant possible.
- *Cons:* Still many schemas to manage. Cross-tenant queries require union across schemas.
- *Best for:* Mid-market with 50-200 tenants.

**Option C: Row-level security (RLS) + tenant_id column** -- Shared tables with tenant_id on every row. PostgreSQL RLS policies enforce isolation at the query level.
- *Pros:* Lowest operational overhead. Single schema. Cross-tenant analytics via tenant_id filter. Connection pool efficiency.
- *Cons:* Isolation is only as strong as the RLS policy -- a bug in a policy leaks data. Shared indexes grow large. Noisy-neighbor problem (one tenant's heavy queries impact all).
- *Best for:* SaaS platforms with many tenants, strong testing of RLS policies.

**Verdict for this design: Hybrid -- RLS for most data, database-per-tenant for top-tier customers.**
- Use RLS by default: `ALTER TABLE documents ENABLE ROW LEVEL SECURITY; CREATE POLICY tenant_isolation ON documents USING (tenant_id = current_setting('app.tenant_id')::UUID);`
- For regulated tenants (financial services, healthcare), provide a dedicated Aurora cluster. The API gateway routes based on tenant tier.
- This mirrors the real project's `owner` column on `document_metadata` but elevates it to a framework-level RLS policy rather than application-level filtering.

### Decision 2: Vector Search at Scale (100M+ Vectors)

**Option A: pgvector with HNSW** -- Stay with pgvector, scale up the instance.
- *Pros:* Zero architectural change. ACID compliance. No data movement. Your code already works.
- *Cons:* HNSW index builds entirely in memory. A 100M x 768-dim index at 4-byte float = ~300GB RAM for the index alone. Aurora instances with that much RAM are expensive. Index build time is hours. Writes during index build cause degradation.
- *Recommended instance:* r7g.16xlarge (~$5-6/hr) or r7i.metal-24xl.

**Option B: Pinecone / Weaviate / Milvus** -- Dedicated vector database.
- *Pros:* Purpose-built for vector search. Disk-based indexes (IVF, PQ) are cheaper at scale. Built-in sharding, replication, multi-tenancy. Faster index build.
- *Cons:* Additional operational burden. Data must be replicated from PostgreSQL to vector DB -- eventual consistency window. Increased blast radius (new potential failure mode). Additional cost.

**Option C: pgvector with partitioning and disk-ANN** -- Partition pgvector by tenant, use IVFFlat indexes instead of HNSW. Lower memory, higher latency.
- *Pros:* Disk-based, no 300GB RAM requirement. Tenant-level partitions can be independently vacuumed/indexed.
- *Cons:* IVFFlat is slower at high recall (fewer probes needed for same recall = more latency). Requires partitioning strategy.

**Verdict: Start with pgvector HNSW scaled vertically (Option A), plan migration to dedicated vector DB at the 50M vector mark.**
- Key metric to watch: p99 vector search latency. When it exceeds 500ms, trigger the migration.
- Use tenant-level table partitioning as an intermediate step to reduce index size per partition.

### Decision 3: LLM Tiering for Cost Optimization

The current system has a single `AiProviderChain` that tries NVIDIA first, falls back to Ollama. At enterprise scale, this binary choice is too coarse.

**Design: Three-tier LLM routing.**

```
                        Query arrives
                             |
                    +--------+--------+
                    |                 |
               Simple lookup    Complex reasoning
                    |                 |
                    v                 v
            Small model (T3)    Large model (T1)
            e.g., Ollama        e.g., NVIDIA
            llama3.2:3b         step-3.5-flash
            Cost: $0.0005       Cost: $0.003
                    |                 |
                    +--------+--------+
                             |
                    T2: Medium model
                    (distilled, e.g., Llama 3.1 8B)
                    Cost: $0.001
```

- *Tier classifier:* A small, fast model (or even a rules-based heuristic based on query length, number of retrieved chunks, presence of comparison words like "versus" vs "vs" vs "compared to") decides the tier before the main LLM call.
- *Answer caching:* Cache in Redis with a key of `tenant_id:query_hash:model_tier`. TTL is document-dependent -- policy documents cache for 24h, daily news summaries cache for 1h.
- *Similar query clustering:* Use embedding similarity to cluster semantically identical queries from different users and batch them into a single LLM call. This is the most impactful cost saver at scale.

### Decision 4: Cross-Region Active-Active Architecture

**Challenge:** PostgreSQL + pgvector does not natively support multi-master. Aurora Global Database has one writer, up to 15 readers. A full active-active requires application-level awareness.

**Design:**
- **Region us-east-1 = primary writer** for PostgreSQL. All writes go here.
- **Region us-west-2 = read replicas.** Vector search is read-heavy (99% reads), so read replicas handle the vast majority of traffic.
- **Writes (document uploads, audit logs):** Routed to us-east-1 via global Kafka/Aurora. Read-after-write consistency: after a document upload in us-west-2, the client waits for the Kafka ack from the ingestion pipeline, which confirms the write propagated.
- **Failover:** If us-east-1 fails, promote us-west-2 Aurora to writer (RTO ~2-3 min). Route53 health check triggers DNS failover. RPO = Aurora replication lag (typically < 1 second in the same region pair, up to 5 seconds cross-continent).
- **Kafka:** MSK with cross-region MirrorMaker 2. Events produced in us-west-2 are replicated to us-east-1 within 500ms.

This is the most pragmatic approach. True active-write in both regions (CockroachDB, YugabyteDB, Spanner) would require replacing PostgreSQL, which is a non-starter given the existing pgvector investment.

### Decision 5: RAG Pipeline Architecture (Streaming vs. Batch)

**The current pipeline is synchronous:**
```
Upload -> Parse -> Split -> Embed -> Index -> Done (all in one request thread)
```

**At enterprise scale, this must be async:**

```
Upload -> Kafka event (FileUploaded) 
                |
Ingestion worker -> Parse + OCR + Split -> Kafka event (TextExtracted)
                |
Embedding worker -> Batch embed -> Kafka event (VectorsReady)
                |
Index worker -> Upsert to pgvector -> Kafka event (IndexingComplete)
                |
Notification -> SSE to user via Collaboration Service
```

- *Why async?* A 10,000-page PDF takes 20+ minutes to process. The user should be able to upload and continue working, receiving a real-time notification when indexing completes.
- *Why Kafka per stage?* Each stage can fail independently. If the embedding service is down, text extraction continues. Failed messages go to a DLQ for manual retry.
- *Cost optimization:* Batch embedding reduces LLM API calls by 40-60% compared to per-chunk embedding. The worker collects chunks for 30 seconds or 1000 chunks (whichever comes first), then sends a single batch embedding request.

### Decision 6: Multi-vector Indexing Strategy

**The current system creates one vector per chunk.** At enterprise scale with diverse document types, this is suboptimal.

**Design: Hierarchical document representation.**
- *Document-level embedding:* captures overall topic. Used for first-pass filtering (which documents are relevant?).
- *Section-level embedding:* captures section themes. Used for section routing.
- *Chunk-level embedding (current):* captures fine-grained semantics. Used for answer extraction.
- *Query decomposition:* Multi-hop queries ("Compare Q3 earnings by region") are decomposed into sub-queries, each targeting different chunks, then the results are synthesized.

This adds complexity but dramatically improves recall for multi-document reasoning while reducing cost (document-level filtering avoids embedding the wrong documents entirely).



---

## 6. Common Pitfalls Candidates Fall Into

### Pitfall 1: Ignoring Multi-Tenancy at the Database Level

*What candidates do:* They propose putting a `tenant_id` column on every table and filtering in application code. They call it done.

*Why it is wrong:* Application-level filtering is fragile. One missed `WHERE tenant_id = ?` clause exposes all tenants' data. One developer mistake in a new JOIN leaks data across tenants.

*What strong candidates do:*
- Use PostgreSQL Row-Level Security (RLS) as a defense-in-depth layer. Even if the application omits the tenant filter, the database enforces it.
- For vector search, create tenant-specific pgvector partitions so the index itself is tenant-scoped.
- At the API gateway, extract and validate the tenant_id from the JWT before the request reaches any service. Forward it as a trusted header that downstream services never accept from the client directly (`X-Tenant-Id` set by gateway, stripped from incoming request).

### Pitfall 2: Treating All LLM Calls as Equal

*What candidates do:* Every query goes through the same NVIDIA model regardless of complexity.

*Why it is wrong:* At 500,000 queries/day, with NVIDIA costing ~$0.003 per query, that is $1,500/day or ~$45,000/month. A small model (Ollama local, or a hosted Llama 3.1 8B) costs ~$0.0003 per query, reducing the bill to $4,500/month.

*What strong candidates do:*
- Implement a tiered routing layer. Use a small, fast classifier to route simple lookups to cheap models and complex reasoning to expensive models.
- Cache aggressively. Semantic caching (embed the query, check for nearby cached queries) captures the Pareto principle -- 80% of queries are rephrasings of the same 20% of questions.
- Batch similar queries. If 10 employees ask "What is the maternity leave policy?" within an hour, the system should recognize the semantic overlap, make one LLM call, and serve from cache for the other 9.

### Pitfall 3: Forgetting About OCR

*What candidates do:* They focus on PDF and DOCX parsing (Tika handles these well) and forget that scanned images are mostly used for legacy enterprise documents.

*Why it is wrong:* Enterprises have decades of paper documents scanned into PDF as images. Tika returns zero text from these. The system silently returns "No relevant documents found" for queries against millions of unreadable image documents.

*What strong candidates do:*
- Add an OCR pipeline stage using Tesseract or a cloud OCR service (AWS Textract, Google Document AI).
- The ingestion pipeline detects extractable text length. If < 50 characters from a 10-page document, route to OCR.
- PO number: Document-level OCR is expensive ($0.015/page for Textract). Only OCR documents that need it. Cache OCR results.

### Pitfall 4: Single-Database Connection Pool with Pgvector

*What candidates do:* They keep the Hikari pool at 10 connections (the current default in application.yml).

*Why it is wrong:* Pgvector queries hold database connections for the duration of the vector search. A single HNSW search can take 100-500ms during heavy load. With 10 connections and 100 concurrent requests, 90 queue immediately. Latency explodes.

*What strong candidates do:*
- Right-size the connection pool. Formula: `pool_size = Tn * (Cm - 1) + 1` where Tn = max threads, Cm = max connections per thread. Start at 50-100 for the RAG orchestrator pool.
- Use separate Hikari pools: one for OLTP (conversation memory, document metadata reads) and one for vector search (longer-running queries). This prevents vector search from starving simple lookups.
- Consider PgBouncer in transaction mode for connection pooling across multiple RAG orchestrator instances.

### Pitfall 5: Deploying pgvector HNSW Without Understanding Memory Requirements

*What candidates do:* They propose pgvector HNSW indexes on a standard db.r6g.large (16GB RAM) without calculating memory needs.

*Why it is wrong:* HNSW keeps the entire graph in memory. For 10M vectors of 768 dimensions (float32):
- Vectors: 10M x 768 x 4 bytes = ~30GB
- HNSW graph edges: Each node has ~64 edges (default m=16, ef_construction=200) = 10M x 64 x 4 bytes = ~2.5GB
- Total: ~32.5GB just for the index. This does not include the vector data itself or PostgreSQL's shared buffers.

*What strong candidates do:*
- Calculate memory requirements up front. Use `IVFFlat` (disk-based, lower recall) as a fallback when RAM is constrained.
- Use `halfvec` type (float16, half the memory) if using pgvector 0.7+.
- Partition by tenant so each partition's index is smaller and can fit in less memory.
- Know when to move to a dedicated vector database.

### Pitfall 6: Underestimating Kafka Consumer Lag

*What candidates do:* They add Kafka event publishing (as the current project does) but do not plan for consumer lag at scale.

*Why it is wrong:* The ingestion pipeline generates hundreds of events per document. At 500 documents/minute, that is potentially 5,000+ Kafka messages per minute. If consumers slow down (e.g., during an embedding API rate limit), the lag grows unbounded. New documents appear hours after upload.

*What strong candidates do:*
- Use Kafka Consumer Groups with enough partitions for parallelism (partition count = max expected concurrency).
- Monitor consumer lag in Grafana with alert thresholds (lag > 1000 triggers intervention).
- Implement backpressure: when embedding API rate limits hit, the embedding worker slows down consumption gracefully, signaling upstream to the ingestion worker.
- Have an S3-distributed dead letter queue for failed events with manual replay capability.

### Pitfall 7: Ignoring the Audit Trail's Storage Requirements

*What candidates do:* They say "we will log to a table" and move on.

*Why it is wrong:* An immutable audit trail at 500,000 queries/day generates 500,000+ audit records per day (query + result + document accesses + admin actions). In one year: 180M+ records. An append-only table in PostgreSQL eventually becomes unmanageable -- vacuum struggles, queries slow down.

*What strong candidates do:*
- Use OpenSearch for queryable audit (time-based indices, ILM to roll over every 30 days).
- Use S3 with Object Lock (WORM mode) for the immutable, non-queryable archive. After 90 days, move audit records from OpenSearch to S3 Glacier for cost efficiency.
- Each audit record contains: `{tenant_id, user_id, timestamp, action, document_ids, model_id, input_hash, response_hash, previous_entry_hash}`. The hash chain ensures immutability.
- GDPR deletion: Audit records are exempt from deletion (legal basis: compliance). But PII within them must be anonymized. Store user_id as a one-way hash, not the raw identifier.



---

## 7. Model Answer -- What a Strong Solution Looks Like

### Opening Statement (candidate's first 2-3 minutes)

"Thank you. Before I dive into the architecture, I want to call out the key constraint I see: **cost at scale**. Your current system makes one LLM call per query. At 500,000 employees and 1 query/day each, that is 500,000 LLM calls daily. At ~$0.003 per call for NVIDIA, that is $45,000/month just in inference. Every architectural decision I make will be evaluated against this constraint.

**My high-level approach:**

1. **Multi-tenant RLS with pgvector partitions** -- reuse your existing pgvector investment. PostgreSQL RLS for defense-in-depth tenant isolation.

2. **Async ingestion pipeline over Kafka** -- evolve your synchronous upload-then-embed into a multi-stage event-driven pipeline. Each stage (parse, OCR, embed, index) is independently scalable.

3. **Three-tier LLM routing + aggressive caching** -- simple lookups go to a cheap local model. Complex reasoning goes to NVIDIA. Cache aggressively at the semantic level.

4. **Active-active with Aurora Global Database** -- us-east-1 as primary writer, us-west-2 as read replicas with fast failover. This preserves your PostgreSQL + pgvector investment.

5. **Immutable audit trail with cryptographic chaining** -- OpenSearch for queryability, S3 Object Lock for immutability, hash chain for tamper detection.

6. **CRDT-based collaboration** -- no central lock server. CRDTs merge edits from multiple users automatically, even across regions.

Let me walk you through each layer."

### Structured Walkthrough

#### Layer 1: Multi-Tenant Isolation

"I will use PostgreSQL Row-Level Security. Every table gets a `tenant_id` column. On every connection, the API gateway sets `app.tenant_id` from the JWT claim. RLS policies use `current_setting('app.tenant_id')` to filter rows transparently.

For the top 10% of customers (regulated industries), I offer dedicated Aurora clusters with physical database isolation. The rest share the multi-tenant cluster.

For pgvector, I create tenant-specific partitions:
```sql
CREATE TABLE doc_vectors_tenant_a PARTITION OF doc_vectors
    FOR VALUES IN ('tenant-a-uuid');
CREATE INDEX ON doc_vectors_tenant_a USING hnsw (embedding vector_cosine_ops);
```

This keeps each tenant's HNSW index small enough to fit in memory."

#### Layer 2: Async Ingestion Pipeline

"The current system parses and embeds synchronously. At enterprise scale, a 10,000-page document would block a thread for 20+ minutes. I change this to an event-driven pipeline:

```
Client upload --> S3 --> Kafka (FileUploaded)
   --> Ingestion worker (Tika + OCR via Textract)
   --> Kafka (TextExtracted)
   --> Embedding worker (batch embed every 30s / 1000 chunks)
   --> Kafka (VectorsReady)
   --> Index worker (upsert to pgvector)
   --> Notification to client via WebSocket
```

Each stage is independently scalable. The embedding worker batches requests to reduce API costs by 40-60%. Failed messages go to a DLQ with automatic retry and a manual replay dashboard."

#### Layer 3: LLM Tiering and Caching

"This is where the cost savings live. I propose:

**Tier-3 (Simple Q&A):** A small local model (Ollama llama3.2:3b, or a distilled Llama 3.1 8B). Handles factual lookups where the answer is in a single document chunk. Cost: ~$0.0003 per query.

**Tier-2 (Synthesis):** A medium model (NVIDIA step-3.5-flash). Handles 2-5 chunk synthesis. Cost: ~$0.003 per query.

**Tier-1 (Complex Reasoning):** Full ReAct agent with NVIDIA. Handles multi-document reasoning, comparisons, analysis. Cost: ~$0.01 per query.

**Routing:** A lightweight classifier (could be a small embedding similarity check + rules) determines the tier. The classifier itself costs < $0.0001.

**Caching:** Semantic cache in Redis. Key: `tenant_id:query_embedding_hash:model_tier`. TTL depends on document volatility. Cache hit rate target: 30-40%. That alone saves $13,500-$18,000/month at scale."

#### Layer 4: Cross-Region HA

"I keep Aurora Global Database with us-east-1 as the primary writer. us-west-2 runs on read replicas. Vector searches (99% of traffic) are read-only and served from replicas.

**Write path for documents:** Upload goes to S3 in whichever region the user is closest to. S3 cross-region replication copies to the other region. The Kafka event triggers the ingestion pipeline in us-east-1 (the writer).

**Failover:** If us-east-1 goes down, I promote the us-west-2 Aurora replica to writer. RTO = 2-3 minutes. Route53 health checks fail over DNS. Users may see a brief read-only mode for any documents ingested in the last 5 seconds.

**RPO:** Aurora replication lag is typically < 1 second within the same continent. Kafka MirrorMaker 2 lag is < 500ms. Total RPO well under 60 seconds as required."

#### Layer 5: Compliance and Audit

"SOC2 immutable audit: Each record contains a SHA-256 hash of the previous record, forming a hash chain. The chain is stored in OpenSearch for fast querying (last 90 days), then archived to S3 Glacier with Object Lock enabled in WORM mode for 7 years.

GDPR right to be forgotten: When a user requests deletion, we:
1. Delete the user's row from the users table.
2. Anonymize their PII in conversation memory.
3. Anonymize their user reference in document_metadata (set owner to anonymous).
4. Delete their personal documents from S3 and pgvector.
5. Audit records are retained (immutable by law), but user_id is replaced with a hash -- the record is no longer linked to the individual.

Legal hold: S3 Object Lock in GOVERNANCE mode prevents document deletion. The hold is managed through the Collaboration Service. Locked documents cannot be deleted or overwritten for the hold duration."

### Closing

"To summarize: **pgvector + RLS for tenant isolation, Kafka for async ingestion at scale, LLM tiering + caching for cost control, Aurora Global DB for HA, and cryptographic audit for compliance.** The 12-week timeline is achievable because 60% of this is evolving existing infrastructure rather than building from scratch. The riskiest item is the LLM tiering classifier -- I would start with a simple heuristic and iterate."

### What Scores Highly

| Dimension | Strong Candidate Does |
|-----------|----------------------|
| **Scope** | Proactively identifies cost as the primary constraint before drawing any boxes |
| **Depth** | Calculates actual numbers (connection pool size, RAM for HNSW, LLM cost/month) |
| **Trade-offs** | Explains why RLS over DB-per-tenant, why Aurora over CockroachDB, why CRDTs over locks |
| **Realism** | Admits that pgvector HNSW needs 300GB RAM at 100M vectors and plans migration |
| **Reuse** | Leverages every existing component (pgvector, Kafka, AiProviderChain) instead of discarding |
| **Edge cases** | OCR, Kafka consumer lag, audit storage, read-after-write consistency gap |



---

## 8. Scoring Rubric (How Interviewers Evaluate)

### Scoring Dimensions (each 1-4, 4 = best)

| Dimension | 1 (Weak) | 2 (Below Average) | 3 (Good) | 4 (Excellent) |
|-----------|----------|-------------------|----------|---------------|
| **Problem framing** | Jumps into drawing boxes without identifying constraints | Identifies 1-2 constraints but misses the key one (cost) | Identifies top 3 constraints including cost | Leads with the cost constraint and ties every decision back to it |
| **Multi-tenancy** | Proposes application-level tenant_id filtering only | Proposes tenant_id + RLS but cannot explain how RLS works | Proposes RLS + tenant-partitioned pgvector | Proposes hybrid model (RLS for most, dedicated clusters for regulated) with clear triggers |
| **Scalability** | Ignores async processing; proposes synchronous ingestion at scale | Mentions async but cannot design the event pipeline | Designs multi-stage Kafka pipeline with per-stage scaling | Designs pipeline with backpressure, DLQ, batch embedding, consumption lag monitoring |
| **Cost optimization** | No mention of cost | Mentions caching in passing | Proposes semantic caching + model tiering | Proposes tiered routing, batch embedding, similar-query dedup, and calculates expected savings |
| **HA/DR** | Single-region with backup | Multi-region read replicas but no failover plan | Active-active with Aurora Global DB and RTO/RPO targets | Full plan including DNS failover, read-after-write consistency gap, Kafka MirrorMaker lag |
| **Compliance** | Says "we will log everything" | Mentions immutable audit but no mechanism | Designs hash-chain audit with OpenSearch + S3 Object Lock | Covers GDPR deletion, PII anonymization, legal hold, audit retention lifecycle |
| **Trade-off articulation** | Presents one option as the only option | Mentions options but cannot articulate trade-offs | Clear pros/cons for 3+ decisions | Compares 3+ options per decision with numeric cost/performance data |
| **Real-world grounding** | Design is all greenfield | Mentions existing system but does not reuse it | Reuses pgvector, Kafka, AiProviderChain intelligently | Evolves existing patterns, admits when current approach breaks down, plans migration |

### Approximate Score to Rating

| Score (out of 32) | Rating | Signal |
|-------------------|--------|--------|
| 28-32 | Strong Hire | Ready for Staff/Principal architect role |
| 22-27 | Hire | Strong Senior Engineer, needs mentoring on operational concerns |
| 16-21 | Lean Hire | Shows promise but significant gaps in scalability/compliance |
| 10-15 | No Hire | Cannot design at system level; stays at implementation details |
| <10 | Strong No Hire | Missed the point entirely |



---

## 9. Follow-Up Questions the Interviewer Would Ask

### Follow-Up 1: Data Consistency

**Interviewer:** "You said us-west-2 reads from Aurora replicas. What happens when a user in us-west-2 uploads a document, gets a success response, and immediately searches for information in that document -- but the Aurora replica has not received the write yet?"

**What they are testing:** Read-after-write consistency gap in active-active designs.

**Strong answer:** "This is the classic read-after-write consistency problem. Two solutions:

1. **Session-level read-your-writes:** After a write, the API gateway sets a cookie or JWT claim marking the user as having recent writes. For the next N seconds, the RAG Orchestrator routes that user's reads to the primary writer (us-east-1). This is the simplest approach and works for most users.

2. **Eventual consistency acknowledgment:** The upload API returns an acknowledgment but not a completion status. The user's document appears in search results only after the ingestion pipeline completes. The Collaboration Service pushes a real-time notification via WebSocket: 'Your document 'Q3 Report.pdf' is now indexed and searchable.' This is more honest but requires client-side handling of the async workflow.

I would use approach 1 for search results and approach 2 for the document listing UI. Most enterprise users expect a brief delay between upload and searchability -- a few seconds is acceptable."

### Follow-Up 2: Operational Complexity

**Interviewer:** "You added five new services, Kafka with multiple topics, Aurora Global DB, CRDTs for collaboration. How do you operate this on a 12-week timeline with a 5-person team?"

**What they are testing:** Pragmatism and build-vs-buy judgment. Can you distinguish between what you build and what you buy?

**Strong answer:** "I do not build all five services from scratch. Here is my build-vs-buy breakdown:

- **Auth Service:** Use a managed identity provider (Auth0, Cognito, or Keycloak) for OIDC/SAML federation. We build only the JWT-to-tenant-ID mapping layer -- about 50 lines of code in the API gateway.

- **Ingestion pipeline:** Buy AWS Textract for OCR (no point building OCR). Build the Kafka pipeline orchestration (about 300 lines of Spring Boot + Spring Kafka).

- **RAG Orchestrator:** Evolve the existing ReActAgent. This is the core IP. Build it.

- **Collaboration Service:** Use a managed WebSocket service (Pusher, Ably, or AWS API Gateway WebSockets) for the transport layer. Build only the CRDT merge logic (or better, use an off-the-shelf CRDT library like Yjs and proxy it through WebSockets).

- **Audit Service:** Build the OpenSearch writer and S3 archiver. Total: about 150 lines. The complex part is the schema design, not the code.

- **CRDTs for collaboration:** Do not build from scratch. Use Yjs (battle-tested, used by Google Docs alternatives) or Automerge.

Estimated actual build: ~1,500 lines of new Java code plus configuration. The team can do this in 4-5 weeks. The remaining 7-8 weeks go to testing, load testing, security review, and production hardening."

### Follow-Up 3: Disaster Recovery Testing

**Interviewer:** "How do you test that your failover actually works? You cannot just flip a switch in production."

**What they are testing:** Operational maturity. Can you design chaos engineering experiments?

**Strong answer:** "We use a structured chaos engineering approach:

1. **Weekly Game Days:** In staging, we simulate AWS region failures by blocking outbound traffic from the us-east-1 EKS cluster. We measure RTO and RPO against our targets. Every incident gets a post-mortem.

2. **Read-only regression:** Before testing failover, we verify that us-west-2 can serve all read traffic at full load. We run a shadow test: duplicate 10% of production read traffic to us-west-2 for a week.

3. **Graded failover tests:**
   - Week 1: Simulate Kafka broker failure in us-east-1
   - Week 2: Simulate Aurora writer failure (force a write to fail, verify read replica promotion)
   - Week 3: Full region failover in staging
   - Week 4: Full region failover in production during maintenance window

4. **Automated assertions:** After each failover, we verify: all endpoints respond, vector search returns results within 3 seconds, audit records are still flowing, Kafka consumer lag is below threshold, no data loss in the last 60 seconds (RPO check).

The goal is not to prevent failures -- it is to make failures boring. The team should be able to sleep through a pager alert because the runbook is tested and proven."

### Follow-Up 4: Security Deep-Dive

**Interviewer:** "How do you handle the case where a compromised JWT (stolen token) lets an attacker read another tenant's documents?"

**What they are testing:** Defense in depth. They want to see that you do not rely on a single security mechanism.

**Strong answer:** "I layer defenses:

1. **JWT binding (already in the current system):** The JWT contains `clientIp` and `userAgentHash` claims. If the token is used from a different IP or browser, the system logs a warning and can enforce re-authentication. The current system already has this.

2. **Short-lived tokens:** 15-minute access tokens + 7-day refresh tokens (already implemented). A stolen token is only useful for 15 minutes.

3. **RLS at the database level (new):** Even if the attacker bypasses application logic, PostgreSQL RLS prevents cross-tenant reads. The database enforces `tenant_id = current_setting('app.tenant_id')`. This is the last line of defense.

4. **Tenant encryption keys (new for regulated tenants):** Each regulated tenant has a unique KMS key. Documents are encrypted at rest with the tenant's key. The RAG Orchestrator can only access documents encrypted with the key corresponding to the authenticated tenant.

5. **Rate limiting on search (current system extended):** Limit per-user query rate to prevent bulk extraction. The current system has 5 req/min on auth endpoints; I extend this to 60 req/min on search endpoints per user.

6. **Audit-driven anomaly detection:** If a single user queries 10x their normal rate, the audit log triggers an alert. A SOC analyst investigates."

### Follow-Up 5: Monitoring and Observability

**Interviewer:** "Your CTO wants a single dashboard that shows: per-tenant query volume, p99 latency, error rates, and daily LLM cost. How do you build this?"

**What they are testing:** Operational excellence. Can you design an observability strategy beyond health checks?

**Strong answer:** "I use Micrometer + Prometheus + Grafana (which the current system already has) and extend it:

**Metrics to collect per endpoint (already partially implemented):**
- `agent.loop.duration` (Timer) -- already exists
- `agent.tool.calls` (Counter) -- already exists
- `agent.tool.duration` (Timer per tool) -- already exists
- Add: `rag.search.duration` (Timer)
- Add: `rag.cache.hits` and `rag.cache.misses` (Counter)
- Add: `inference.cost` (Counter, tagged by model tier)
- Add: `audit.log.failures` (Counter)

**Tagging strategy for multi-tenancy:**
All metrics are tagged with `tenant_id` and `user_role`. This enables per-tenant dashboards. Cardinality warning: keep tenant_id cardinality under 1000. For 10,000+ tenants, use a separate Prometheus metrics instance per cluster.

**Grafana dashboards:**
1. **Executive Dashboard:** Per-tenant row showing queries/day, p99 latency, error rate, $ cost/day, cache hit rate.
2. **Operations Dashboard:** Real-time: live query throughput, consumer lag per Kafka partition, Aurora replication lag, connection pool utilization.
3. **Cost Dashboard:** Daily/weekly/monthly spend broken down by model tier, by tenant, with trend lines. Alert when any tenant's spend exceeds 2x the weekly average.

**Alerting (Prometheus AlertManager):**
- P1: P99 latency > 5 seconds for 5 minutes (page on-call)
- P1: Error rate > 1% for any tenant (page on-call)
- P2: Kafka consumer lag > 1000 (investigate during business hours)
- P2: Cache hit rate < 20% (review caching strategy)
- P3: Daily cost exceeds forecast by 20% (review during weekly ops meeting)

The current system already exports Prometheus metrics. I would add the tenant_id tag to existing meters and create the tier-specific counters."



---

## Section 2: 📝 Mock Interview — 7 Questions With Scoring Guide

## SECTION 2: Mock Interview — 7 Questions With Scoring Guide

The seven questions below are sequenced to mirror a real Senior Java Solution Architect interview loop. Each question is grounded in the candidate's actual project experience (the Spring AI Agent project). Questions progress from pure technical depth, through architectural trade-offs, to leadership and behavioral evaluation — exactly as a FAANG or enterprise interview panel would.

---

### Question 1: System Design / Architecture

**"Walk me through the architecture of the ReAct agent you built. What were the key design decisions, what alternatives did you consider, and why did you land on the approach you chose?"**

**Context:** This is the classic "design the system you built" opener. A Solution Architect needs to articulate architectural decisions clearly, show awareness of trade-offs, and demonstrate that they didn't just follow tutorials but made intentional design choices. This question probes whether the candidate thinks at the system level rather than the class level.

**What a strong answer includes (checklist):**
- Explains the ReAct loop (Thought -> Action -> Observation -> Repeat) as an architectural pattern, not just a code flow
- Describes the interface-driven design: AiService, ToolRegistry, AgentMemory, KafkaEventPublisher are all interfaces with multiple implementations
- Explains the separation of concerns between ReActAgent (orchestration), PromptBuilder (prompt construction), and Tool implementations
- Discusses the Chain of Responsibility pattern in AiProviderChain (NVIDIA primary, Ollama fallback) and why that was chosen over a simple if/else or circuit breaker
- Mentions the rationale behind extracting prompt construction into a dedicated PromptBuilder class (single responsibility, testability, externalized prompt templates from classpath resource)
- Addresses the context window management — truncating oldest steps when token count exceeds maxContextTokens with a warning log
- Discusses the RAG auto-retry mechanism (detecting when LLM gives a premature final answer without using tools when documents exist)
- Explains why Micrometer metrics (per-tool timers with tool-name tags, counters) were added and how they feed into operational visibility
- Describes the decision to separate model classes (AgentState, Step records) from service logic

**What a weak answer sounds like:**
- Describes only the code flow without architectural rationale ("I just wrote a loop that calls the LLM")
- Cannot articulate why interfaces were used over concrete classes ("Spring makes you use interfaces")
- Has no awareness of alternatives considered ("I didn't look at other approaches")
- Talks only about implementation details (regex patterns, specific tool names) rather than system properties
- Cannot explain the trade-off of extracting PromptBuilder vs keeping prompts inline

**Scoring guidance (1-5):**
- **1-2 (Weak):** Can describe what the code does but not why. No discussion of alternatives or trade-offs. Focuses on implementation trivia.
- **3 (Meets):** Explains the ReAct loop and interface-driven design. Mentions at least one alternative considered. Can describe the separation of concerns.
- **4-5 (Strong):** Articulates the full architectural picture including context window management, RAG auto-retry rationale, observability decisions, and trade-offs. Can discuss what they would change or improve.

**Sample strong answer:**

"The architecture follows the ReAct pattern — Reasoning + Acting in an iterative loop. The core design principle was interface-driven programming. Every major subsystem has an interface: AiService abstracts the LLM provider, ToolRegistry abstracts tool discovery, AgentMemory abstracts conversation persistence, and KafkaEventPublisher abstracts event streaming. This gave us the flexibility to swap implementations without touching orchestration code.

For the AI provider specifically, I used a Chain of Responsibility pattern through AiProviderChain. NVIDIA is the primary provider because of its higher token limits and lower latency, but Ollama runs locally as a fallback. The chain resolves providers dynamically per-call: it first tries the configured provider, and if unavailable, iterates through all registered providers for the first available one. This is more resilient than a static if/else chain because providers can come and go (Ollama might be down during a Docker restart, NVIDIA might rate-limit us).

One decision I'm particularly glad we made was extracting PromptBuilder. Initially, prompt construction was inlined in ReActAgent, making it hard to test and even harder to modify. By separating it, we could externalize the system prompt template to a classpath resource file, unit test prompt construction independently, and change prompts without touching agent orchestration code.

For the context window, we track an estimated token count across the AgentState steps. When it exceeds the configured max (3,000 tokens), we truncate the oldest steps. This is a pragmatic choice — we lose some history but maintain the most recent context, which empirically is what matters most for LLM quality. We log a warning when truncation happens so operations can tune the limit if needed.

The RAG integration has an interesting edge case handler: if the LLM gives a final answer on the first iteration without using any tools, but documents exist in the ingestion service, the agent intercepts that, logs a warning, and forces a rag_search call before answering. This prevents the agent from hallucinating about document content when it should be grounding its answers in retrieved context."

---

### Question 2: Java / Spring Boot Deep Dive

**"You upgraded from Spring Boot 3.5.15 to 4.1.0 and from Spring AI 1.0.8 to 2.0.0 — both with breaking changes. Walk me through that migration. What broke, how did you diagnose it, and what did you learn about Spring Boot's internal architecture in the process?"**

**Context:** A Solution Architect must be capable of managing framework migrations across large codebases. This question tests deep knowledge of Spring Boot internals, awareness of breaking changes across major versions, and a systematic approach to risk mitigation during upgrades. Candidates who have only ever built greenfield projects struggle here; the candidate's real migration experience gives them an edge worth probing.

**What a strong answer includes (checklist):**
- Describes the systematic approach: changelog review first, then compilation test, then runtime test
- Mentions specific Spring Boot 4.x changes: Jakarta EE 11 migration, virtual threads (spring.threads.virtual.enabled), improved container integration
- Discusses the Spring AI 1.x to 2.x changes that required code updates (package restructuring, API changes in VectorStore, TikaDocumentReader)
- Explains how they approached the migration incrementally — not a big-bang rewrite
- Demonstrates understanding of dependency management via spring-ai-bom
- Talks about testing strategy during migration: 181 tests served as the safety net
- Mentions the Java version upgrade path (Java 21+ as minimum for Spring Boot 4.x, project using Java 25)
- Discusses any issues with Kafka client compatibility or JWT library (jjwt) version alignment
- Shows awareness of ConfigurationProperties changes across Spring Boot versions

**What a weak answer sounds like:**
- Says "It just worked" or "I didn't really notice any breaking changes" (suggests shallow migration or lack of ownership)
- Cannot name a single specific breaking change encountered
- Describes a brute-force approach ("I just changed versions until it compiled")
- Blames the framework rather than explaining how they adapted
- Has no testing strategy beyond "run it and see"

**Scoring guidance (1-5):**
- **1-2 (Weak):** Cannot articulate specific breaking changes. No systematic approach described.
- **3 (Meets):** Names at least 2-3 specific breaking changes and explains how they resolved them. Mentions the importance of the test suite.
- **4-5 (Strong):** Demonstrates deep knowledge of Spring Boot internals (servlet container, auto-configuration, property binding). Can discuss the architectural implications of Jakarta EE 11 migration. Articulates a framework upgrade methodology they would apply to any project.

**Sample strong answer:**

"This was a significant migration — two major framework versions simultaneously. My approach was incremental and test-driven.

First, I reviewed the Spring Boot 4.0 release notes and migration guide. The biggest structural change is the Jakarta EE 11 migration — javax.* to jakarta.* namespace. This ripples through every import related to servlets, validation, and persistence. Our JwtAuthFilter and RateLimitingFilter both use jakarta.servlet, so we had to update those. Spring Boot 4.1.0 also changed some auto-configuration property keys and deprecated several health indicator classes.

The Spring AI 1.x to 2.0 migration was more involved. The PgVectorStore builder API changed significantly between versions. In 1.x, vector store configuration was annotation-driven; in 2.0, it moved to a fluent builder pattern. I had to restructure PgVectorStoreConfig to use the new builder with explicit dimension, distance type, and index type parameters. The EmbeddingModel API also changed — the call signatures for embedding generation were restructured, and some configuration classes were moved to different packages.

The key enabler was our test suite — 181 tests. I started the migration by just updating the POM versions and running mvn test. The compilation failures told me exactly what needed to change. This is why I\'m a strong advocate for interface-driven design: when VectorStore is wired through an interface, changing the underlying implementation only affects the configuration class, not the services that use it.

Spring Boot 4.x also brought virtual threads support via spring.threads.virtual.enabled, which we enabled. This is architecturally interesting because it changes the thread-per-request model without code changes. The ReAct agent loop benefits from this since each chat request gets a virtual thread rather than tying up a platform thread during LLM API calls.

One thing I learned about Spring Boot internals during this: how auto-configuration conditional annotations work. The @ConditionalOnProperty on KafkaConfig was critical because it means the Kafka infrastructure beans only activate when spring.kafka.bootstrap-servers is set. This allowed the NoOpKafkaEventPublisher (@Primary) to take over seamlessly when Kafka isn\'t configured — no property toggling, no manual profiles."

---

### Question 3: Cloud Architecture (AWS)

**"Your application runs in Docker Compose locally with PostgreSQL, Kafka, and Ollama. How would you deploy this architecture on AWS? Walk me through the services you'd use, how you'd handle stateful components, and what your disaster recovery strategy looks like."**

**Context:** A Solution Architect needs to translate local container orchestration to production cloud infrastructure. This question tests AWS service knowledge, understanding of stateful vs stateless architecture, and the ability to design for reliability, cost, and operational complexity. The multi-service nature of the candidate's project (pgvector, Kafka, Ollama, app) makes this a realistic, non-trivial design challenge.

**What a strong answer includes (checklist):**
- Addresses each service: app containers on ECS Fargate or EKS, pgvector on RDS PostgreSQL with pgvector extension, Kafka on MSK or self-hosted on ECs with MSK, Ollama on GPU-backed instances (EC2 with GPU or SageMaker)
- Describes the stateless nature of the Spring Boot app enabling horizontal scaling behind an ALB
- Explains why RDS PostgreSQL with the pgvector extension is preferred over running pgvector in containers (managed backups, Multi-AZ, automated patching)
- Discusses MSK vs self-managed Kafka trade-offs (cost vs operational overhead)
- Addresses the GPU requirement for Ollama and considers alternatives (SageMaker endpoints for LLM, avoiding self-hosting Ollama in production)
- Incorporates the security architecture: ALB with WAF, private subnets, Secrets Manager for JWT_SECRET and NVIDIA_API_KEY, VPC endpoints for MSK and RDS
- Discusses observability: CloudWatch metrics from Micrometer/Prometheus, structured logging to CloudWatch Logs, X-Ray tracing
- Mentions CI/CD pipeline (CodePipeline or GitHub Actions) with the Docker build and health check integration
- Addresses disaster recovery: RDS Multi-AZ with automated backups, MSK cross-AZ replication, multi-region considerations
- Includes a practical cost estimate or optimization consideration

**What a weak answer sounds like:**
- Suggests running everything on a single EC2 instance with Docker Compose (just moves local setup to cloud)
- Doesn't address the GPU requirement for Ollama at all
- Proposes using services incorrectly (e.g., ElastiCache for pgvector, SQS instead of Kafka)
- Cannot discuss trade-offs between managed and self-hosted options
- Ignores networking, security groups, and VPC design
- Has no disaster recovery or backup strategy

**Scoring guidance (1-5):**
- **1-2 (Weak):** Proposes lifting-and-shifting Docker Compose to a single server. No awareness of managed services or their trade-offs.
- **3 (Meets):** Maps each component to an appropriate AWS service. Discusses at least networking and security basics. Mentions Multi-AZ or high availability.
- **4-5 (Strong):** Provides a comprehensive architecture with service selection rationale, cost considerations, security architecture, observability, DR strategy, and CI/CD. Can discuss trade-offs knowledgeably and adapts the design based on constraints (cost, latency, compliance).

**Sample strong answer:**

"The current Docker Compose setup has four services: the app (stateless Spring Boot), PostgreSQL with pgvector (stateful), Kafka (stateful), and Ollama with GPU dependency (stateful, specialized hardware). Each requires a different cloud strategy.

For the app containers, I\'d use ECS Fargate. It\'s serverless — no cluster management — and we can set up an Application Auto Scaling target based on CPU/memory or custom metrics from Micrometer (e.g., agent.loop.duration p99). The app is stateless, so it scales horizontally behind an ALB with a target group health check hitting /api/health. We\'d containerize with our existing Dockerfile and push to ECR.

For PostgreSQL with pgvector, RDS PostgreSQL with the pgvector extension is the clear choice. Running pgvector in a container on Fargate would lose all managed database benefits — automated backups with point-in-time recovery, Multi-AZ failover, automated patching, and performance insights. We\'d use a db.r6g.large or similar instance, enable Multi-AZ for production, and set automated snapshots with a 30-day retention. The vector data (768-dim embeddings with HNSW index) is not especially large for this use case, so standard RDS handles it fine.

Kafka is interesting. MSK is the managed option and handles broker health, automatic patching, and integrates with IAM for authentication. But it\'s expensive for what we need — the agent events are low-volume, non-critical logs. A cost-effective alternative is using a smaller MSK cluster with a single broker per AZ, or even SQS with a FIFO queue if ordering matters. But since our existing codebase uses Kafka with idempotent producers and graceful degradation via NoOpKafkaEventPublisher, MSK is the natural fit. We\'d set it up in a private subnet with VPC endpoints.

Ollama is the hardest component. It needs GPU instances — g5 or p3 family on EC2. For production, I\'d question whether self-hosting Ollama makes sense versus using SageMaker endpoints with a foundation model, or even switching fully to NVIDIA Cloud (which the architecture already supports). But if we must self-host, I\'d use an EC2 GPU instance with Ollama in a Docker container, managed by an Auto Scaling group with a GPU-based metric. The NVIDIA_API_KEY in the current architecture means we already have the cloud fallback path tested.

For networking: ALB in public subnets, ECS tasks in private subnets with a NAT gateway for outbound access, RDS and MSK in isolated private subnets. Secrets Manager stores JWT_SECRET and NVIDIA_API_KEY, rotated automatically. WAF on the ALB for rate limiting at the edge, complementing the application-level RateLimitingFilter.

Disaster recovery: RDS Multi-AZ handles AZ failure. For region failure, we\'d set up read replicas in a second region with pgvector extension enabled. The stateless app can be deployed to the second region via CodePipeline cross-region action. MSK\'s cross-region replication with MirrorMaker for critical events.

Observability: CloudWatch agent on ECS exports Micrometer metrics. We\'d set up a Grafana dashboard for the agent metrics — tool call counts, loop durations per session, token usage totals. Structured JSON logging feeds into CloudWatch Logs with a 7-day retention policy."

---

### Question 4: Security Architecture

**"Walk me through the security architecture of your application. You have JWT authentication, rate limiting, role-based access control, and a global exception handler. I want to understand the defense-in-depth strategy — what threats are you protecting against, what gaps remain, and how would you address them?"**

**Context:** Security is a board-level concern for any Solution Architect. This question evaluates whether the candidate thinks about security holistically (defense in depth) rather than as a checklist of features. The candidate's project has several security layers, making this a strong discussion topic. An architect must be able to articulate threat models, identify gaps, and prioritize remediation.

**What a strong answer includes (checklist):**
- Describes the layered defense: network (VPC/subnets), transport (HTTPS), authentication (JWT), authorization (RBAC via hasRole), rate limiting (application-level filter), and information disclosure (global exception handler)
- Explains the JWT design decisions: 15-minute access tokens, 7-day refresh tokens, IP+UserAgent binding via claims, SHA-256 hashing of user agent to limit token size
- Discusses the RateLimitingFilter implementation: ConcurrentHashMap with AtomicInteger, periodic cleanup via @Scheduled, why it's only applied to /api/auth/ endpoints
- Explains the GlobalExceptionHandler strategy: correlation IDs, environment-aware error messages (dev vs prod), prevention of stack trace leakage
- Acknowledges gaps or areas for improvement (can discuss these credibly)
- Shows understanding of common web vulnerabilities: CSRF (disabled because stateless JWT), XSS, injection, rate limiting, brute-force protection
- Mentions external security measures: OWASP scanning, dependency vulnerability checks
- Discusses the MCP endpoint security model: lenient JWT validation with logging for backward compatibility, acknowledging this as a gap
- Addresses secrets management: JWT_SECRET and NVIDIA_API_KEY as environment variables, not in code
- Describes the BCryptPasswordEncoder for credential storage

**What a weak answer sounds like:**
- Describes only the JWT flow without mentioning other layers
- Cannot identify any gaps or improvements ("It's completely secure")
- Doesn't understand why CSRF is disabled or why that choice is valid for stateless APIs
- Thinks security is just authentication
- Cannot explain why the rate limiter uses ConcurrentHashMap with AtomicInteger instead of, say, Bucket4j or Redis-based rate limiting
- Doesn't address the MCP endpoint's lenient auth as a security concern

**Scoring guidance (1-5):**
- **1-2 (Weak):** Lists features without explaining their purpose or threat model. Cannot identify any gaps. Surface-level understanding.
- **3 (Meets):** Explains the layered approach and rationale for key decisions (JWT expiration, BCrypt, rate limiting). Identifies at least one honest gap.
- **4-5 (Strong):** Articulates a complete threat model. Discusses gaps credibly and proposes prioritized remediation. Shows awareness of the tension between security and usability (MCP endpoint leniency). Can discuss scaling the rate limiter beyond a single-node ConcurrentHashMap.

**Sample strong answer:**

"The security architecture follows defense in depth — no single control is relied upon alone. Let me walk through each layer and the threats they address.

At the transport layer, all API traffic goes over HTTPS in production, preventing MITM attacks. The app itself is stateless, which eliminates session fixation and CSRF attack surface — that's why CSRF is disabled in SecurityConfig. For a JWT-based Bearer auth scheme, CSRF tokens are unnecessary and would just add complexity.

Authentication uses JWT with short-lived access tokens (15 minutes) and longer refresh tokens (7 days). The key design decision was IP and User-Agent binding. When a token is issued, we embed the client's IP address and a SHA-256 hash of their User-Agent string in custom claims. On subsequent requests, JwtAuthFilter validates that the IP matches. This means a stolen token can't be used from a different IP — it's token-to-client binding. We hash the User-Agent rather than storing it raw to keep the token size reasonable; a full browser UA string can be 200+ characters, and JWTs are base64-encoded on every request.

Authorization uses Spring Security's role-based access control. The /api/documents/** endpoint is restricted to USER and ADMIN roles via hasAnyRole in SecurityConfig. This is relatively basic RBAC — roles are checked at the URL pattern level. For a more granular system, I'd consider method-level security with @PreAuthorize and permission-based evaluation, but for the current scope, URL-pattern RBAC is proportional to the threat.

Rate limiting is implemented as a OncePerRequestFilter applied specifically to auth endpoints — 5 requests per minute per IP. I chose a ConcurrentHashMap with AtomicInteger entries for simplicity. This works fine for a single-node deployment but would need to move to a distributed store (Redis with sliding window) in a multi-instance setup. The @Scheduled cleanup method runs every 60 seconds to evict stale entries. The 5/min limit is aggressive enough to slow brute-force password guessing without impacting legitimate users who might hit login once after a page refresh.

The GlobalExceptionHandler is an underrated security control. In production, it returns a correlation ID instead of the actual error message or stack trace. This prevents information leakage that attackers could use to fingerprint the stack or find injection points. In dev mode, full error details are returned for debugging. The correlation ID is logged server-side so support can cross-reference user reports with internal logs.

Now, honestly, there are gaps. The biggest one is the MCP endpoint security. Currently, JWT validation on SSE and message endpoints is lenient — it logs a warning but processes the request anyway. This was a backward-compatibility concession during development. In production, I'd make JWT mandatory for MCP endpoints and remove the anonymous fallback.

Another gap: the rate limiter is per-node and in-memory. In a multi-instance ECS deployment, an attacker could distribute login attempts across instances to bypass the per-IP limit. I'd move this to Redis with a Lua script for atomic sliding-window rate limiting across all instances.

OWASP scanning and dependency vulnerability checking via OWASP Dependency-Check in the Maven build would also be on my remediation list. The current architecture has no automated vulnerability scanning in the CI/CD pipeline."

---

### Question 5: Microservices & Distributed Systems

**"Your application uses Kafka for event streaming and has a graceful degradation pattern via NoOpKafkaEventPublisher. Tell me about this design. What's your strategy for handling partial failures in distributed systems, and how would you evolve this architecture if the agent needed to communicate with other services in an event-driven way?"**

**Context:** A Solution Architect must design for failure. This question explores the candidate's understanding of distributed systems patterns — graceful degradation, idempotency, event-driven architecture, and the trade-offs involved. The Kafka integration with its fallback pattern provides a concrete, realistic discussion anchor.

**What a strong answer includes (checklist):**
- Explains the graceful degradation pattern: KafkaConfig is @ConditionalOnProperty, DefaultKafkaEventPublisher only activates when brokers are configured, NoOpKafkaEventPublisher is @Primary as the fallback
- Explains why idempotent producer (ENABLE_IDEMPOTENCE_CONFIG=true) is critical — prevents duplicate events during producer retries
- Discusses the fail-fast timeout settings (MAX_BLOCK_MS_CONFIG=2000, REQUEST_TIMEOUT_MS_CONFIG=2000) to prevent thread starvation when Kafka is down
- Explains why the event publishers are fire-and-forget with try/catch rather than blocking the agent loop
- Addresses the elephant in the room: the Kafka event publishing is fire-and-forget with no retry/backoff for transient failures beyond the 3 retries
- Discusses how the architecture would evolve for event-driven inter-service communication (outbox pattern, event sourcing, saga pattern)
- Shows understanding of Kafka consumer group semantics and partition assignment
- Mentions the consumer configuration: earliest auto-offset, concurrency of 3, max-poll-records of 500
- Discusses event schema management (or lack thereof) and how that would need to evolve
- Addresses ordering guarantees: using sessionId as the Kafka key ensures ordered delivery per session

**What a weak answer sounds like:**
- Doesn't understand graceful degradation (confuses it with just catching exceptions)
- Has never considered what happens when Kafka is unavailable
- Proposes using Kafka for everything without understanding the overhead
- Cannot articulate the difference between fire-and-forget, synchronous, and transactional event publishing
- Thinks the current try/catch with a log warning is a complete error handling strategy
- Suggests architectural changes without considering the impact on the existing design

**Scoring guidance (1-5):**
- **1-2 (Weak):** Cannot explain the graceful degradation pattern. No understanding of idempotency or its importance. Suggests naive solutions for inter-service communication.
- **3 (Meets):** Explains the @ConditionalOnProperty and @Primary pattern clearly. Discusses idempotency and the purpose of the timeout settings. Has basic understanding of Kafka consumer groups.
- **4-5 (Strong):** Articulates the interaction between multiple patterns (conditional configuration, primary fallback, fire-and-forget, idempotent producer). Discusses the current gaps honestly (no dead letter queue, no retry with backoff). Describes how they'd evolve to an outbox pattern for guaranteed delivery. Connects this to broader distributed systems patterns (saga, CQRS, event sourcing).

**Sample strong answer:**

"The Kafka integration is designed so that the agent works perfectly without Kafka — it's additive, not essential. This was a deliberate architectural decision.

The mechanism works through Spring's conditional bean registration. KafkaConfig has @ConditionalOnProperty(value = "spring.kafka.bootstrap-servers"), so the Kafka infrastructure — ProducerFactory, KafkaTemplate — only exists when a broker URL is configured. DefaultKafkaEventPublisher, which depends on KafkaTemplate, uses the same condition. Meanwhile, NoOpKafkaEventPublisher is annotated @Primary. When Kafka is configured, Spring has two beans of KafkaEventPublisher and selects the @Primary one... except that's the NoOp one. Wait — let me correct myself: NoOpKafkaEventPublisher is @Primary and DefaultKafkaEventPublisher is @ConditionalOnProperty. When the condition is met, both beans exist and the @Primary NoOp would win in injection ambiguity. Actually, looking back, the @ConditionalOnProperty on DefaultKafkaEventPublisher means it only registers when kafka is configured, and NoOpKafkaEventPublisher with @Primary handles the case when it's not. This ensures the agent controller and ReActAgent always have a KafkaEventPublisher to inject, never throw a NoSuchBeanDefinitionException, and Kafka availability is transparent to the business logic.

For the producer, I configured ENABLE_IDEMPOTENCE_CONFIG=true with max.in.flight.requests.per.connection=5 (default with idempotence) and acks=all. Idempotent producers use a producer ID and sequence numbers so the broker can deduplicate — this prevents duplicate events when a producer retries a request that actually succeeded on the broker side. Combined with retries=3 and the short timeouts (MAX_BLOCK_MS_CONFIG=2000), we fail fast when Kafka is unreachable but are resilient to transient broker issues.

Currently, event publishing is fire-and-forget from the agent's perspective — the publish methods wrap KafkaTemplate.send in a try/catch, log a warning on failure, and continue. This is appropriate because agent events are operational logs, not business-critical commands. If we needed guaranteed delivery, I'd implement the transactional outbox pattern: events written to a PostgreSQL outbox table in the same transaction as the business operation, then a separate Poller publishes them to Kafka and marks them as sent. This gives exactly-once semantics without the complexity of distributed transactions.

For event-driven evolution: if the agent needed to emit commands to other services, I'd use an event-per-action approach rather than the current monolithic event-per-chat-request pattern. Each tool execution would emit a structured domain event (e.g., WeatherQueried, DocumentUploaded) with a schema version, published to a domain-specific topic. Downstream services subscribe to relevant topics. The sessionId as the Kafka key ensures ordered delivery per session, which matters for reconstructing conversation state.

The current architecture uses a shared JSON payload with a type discriminator. For multi-service evolution, I'd introduce Avro or Protobuf schemas in a Schema Registry to enforce contract compatibility."

---

### Question 6: Leadership & Stakeholder Communication

**"You migrated from Redis + ChromaDB to PostgreSQL with pgvector, changed your AI provider architecture, and introduced JWT authentication — all breaking changes. How did you communicate these changes to stakeholders (product managers, other engineers, operations), and how did you manage the risk of these migrations going wrong?"**

**Context:** A Solution Architect doesn't just make technical decisions — they must bring the organization along. This question evaluates technical leadership, risk management, and communication skills. The candidate's real experience with multiple migrations provides concrete evidence to evaluate, not hypothetical scenarios.

**What a strong answer includes (checklist):**
- Describes how they categorized stakeholders differently (engineering peers need detail, product needs timeline/risk, operations needs runbooks)
- Explains the migration strategy for each change — phased approach, feature flags, parallel run, or cutover
- Describes concrete risk mitigation: test suite as safety net (181 tests), staging environment validation, rollback plan
- Shows empathy for stakeholder concerns (product worried about downtime, operations worried about new operational burden, engineering peers worried about learning curve)
- Mentions documentation artifacts (ADR, migration plan, runbook)
- Addresses the human side of change: bringing peers along through pair programming or tech talks, not just issuing mandates
- Discusses how they handled pushback or disagreement (e.g., someone preferred ChromaDB)
- Shows awareness of the trade-off between velocity and risk
- Describes how they measured success post-migration (performance metrics, error rates, developer satisfaction)

**What a weak answer sounds like:**
- Says they just made the changes and told people after
- Cannot describe any risk mitigation beyond "test it"
- Dismisses stakeholder concerns ("they wouldn't understand anyway")
- Describes unilateral decision-making without buy-in
- Cannot articulate what they'd do differently
- Blames others for resistance rather than explaining how they addressed it

**Scoring guidance (1-5):**
- **1-2 (Weak):** Made changes without communication. No risk management. Cannot describe stakeholder engagement.
- **3 (Meets):** Describes a basic communication plan and risk mitigation. Identifies different stakeholder groups. Mentions incident rollback planning.
- **4-5 (Strong):** Demonstrates sophisticated stakeholder management with tailored communication per group. Describes concrete risk mitigation (canary releases, dark launches, parallel runs). Discusses how they built organizational buy-in. Can articulate a before-and-after of their approach based on lessons learned.

**Sample strong answer:**

"I approached each migration differently because they had different risk profiles and stakeholder implications.

The database migration — from Redis + ChromaDB to PostgreSQL + pgvector — was the highest risk. It involved data migration, schema changes, and a fundamental shift in the query model. I started by writing an Architecture Decision Record (ADR) explaining why: we needed relational integrity, transactional guarantees for document metadata alongside vector search, and wanted to reduce our operational footprint from two data stores to one. The ADR was circulated to engineering peers for review before any code was written.

For stakeholders: to the product manager, I framed it as 'one less database to manage means fewer infrastructure incidents and faster feature delivery.' To operations, I explained that pgvector on PostgreSQL gives us managed backups, point-in-time recovery, and standard monitoring — things Redis and ChromaDB required custom tooling for. To engineering peers, I created a migration document showing the new query patterns compared to the old ones and organized a brown-bag session to walk through the PgVectorStore configuration with HNSW indexing.

Risk mitigation was layered. First, the existing tests — 181 tests — served as the regression safety net. Second, I ran both the old and new data stores in parallel during development to compare search result quality. Third, I extracted the vector store configuration into PgVectorStoreConfig so the schema initialization (initializeSchema=true) is automated and repeatable. The migration itself was a cutover: we scheduled a maintenance window, ran a migration script to copy document metadata to the new PostgreSQL schema, validated, and switched. Because the agent had graceful degradation via the NoOpKafkaEventPublisher pattern, we already had patterns for handling component unavailability.

The AI provider architecture change (introducing AiProviderChain with NVIDIA->Ollama fallback) was lower risk because it was additive — we kept the existing OllamaService as-is and added the chain as a wrapper. Communication was simpler: I demonstrated the fallback working by unplugging Ollama and showing the agent still responding via NVIDIA. The team could see the improvement immediately. The token usage tracking endpoint (/api/agent/tokens) gave operations a concrete metric to validate the change.

The JWT authentication introduction was the change that required the most cross-team coordination. The frontend team needed to update their login flow. Operations needed to configure JWT_SECRET in production. The MCP team (if separate) needed to add Authorization headers to their clients. I created a migration guide with before/after request examples and set up a pairing session with frontend developers to walk through the token refresh flow.

The biggest lesson I learned: involve operations earlier. In the database migration, I had the runbook ready, but I should have had operations validate it before the maintenance window rather than during. For subsequent changes, I started sharing draft runbooks during the design phase, which also surfaced operational requirements I hadn't considered."

---

### Question 7: Career / Behavioral (STAR Format)

**"Tell me about a time when you had to make a significant technical decision that was unpopular with part of the team. How did you handle the disagreement, what was the outcome, and what would you do differently?"**

**Context:** Behavioral questions are where interviewers assess emotional intelligence, conflict resolution, and leadership style. The STAR framework (Situation, Task, Action, Result) is the expected response format. The candidate should draw on their real project experience — the database migration from Redis+ChromaDB to PostgreSQL+pgvector is an excellent example of a controversial decision that likely had skeptics.

**What a strong answer includes (checklist):**
- Clear STAR structure: Situation (what was happening), Task (what needed to be decided), Action (what they did), Result (what happened)
- Describes a real, specific situation — not a generic leadership philosophy
- Shows they understood the opposing viewpoint, not just dismissed it
- Explains how they built data-driven arguments (benchmarks, comparison metrics)
- Demonstrates they sought buy-in rather than commanded obedience
- Shows willingness to compromise or adapt based on valid counter-arguments
- Admits what they would do differently — self-awareness is critical
- Outcome is concrete and measurable, not just "everyone agreed"
- Shows they can lead through influence rather than authority

**What a weak answer sounds like:**
- Vague hypothetical ("I would...") rather than a real experience
- Blames others for the disagreement ("they just didn't understand")
- Claims everyone agreed with them immediately (suggests either a non-controversial decision or lack of self-awareness)
- No measurable outcome ("it worked out fine")
- Cannot articulate what they'd do differently (suggests they think they were perfect)
- Describes an authoritarian approach ("I'm the architect, so I made the call")
- Fails the STAR structure entirely

**Scoring guidance (1-5):**
- **1-2 (Weak):** No real example, vague or hypothetical. Blames others. No self-reflection.
- **3 (Meets):** Follows STAR structure with a real example. Acknowledges the disagreement. Outcome is described.
- **4-5 (Strong):** Compelling STAR example showing data-driven persuasion, empathy for dissenters, concrete measurable outcome, and genuine self-reflection on what they'd improve. Demonstrates technical leadership through influence.

**Sample strong answer:**

"(Situation) We were using Redis for conversation memory and ChromaDB for vector storage. This meant two separate data stores with different operational tooling, no relational integrity between document metadata and vector embeddings, and no transactional guarantees. When a document upload succeeded in ChromaDB but failed in the metadata store, we'd have orphaned vectors with no associated document record.

(Task) I proposed migrating to PostgreSQL with the pgvector extension as a single data store. Some team members pushed back. A senior engineer argued that Redis and ChromaDB were 'tried and true' for AI workloads and questioned whether pgvector's HNSW index performance would match ChromaDB's. Another engineer felt the migration risk wasn't justified — 'if it isn't broken, don't fix it.' They had valid points: we'd have to rewrite the DocumentIngestionService, retrain everyone on the new query patterns, and risk regression during the cutover.

(Action) I didn't override them. Instead, I took three steps. First, I built a proof of concept comparing ChromaDB and pgvector on our actual use case — 768-dim embeddings with cosine similarity search across a dataset of 5,000 document chunks. The results showed pgvector with HNSW was within 5% of ChromaDB in query latency and actually outperformed on recall due to the reranking step I was planning (0.7 similarity + 0.3 keyword overlap). I shared the benchmark results in a team document.

Second, I wrote an ADR explaining not just the technical benefits but the operational ones: one fewer database to manage, standard PostgreSQL backup/restore tooling, transactional consistency between document metadata and vectors. I specifically addressed the 'not broken' concern by pointing out the orphaned vector problem we had already encountered twice.

Third, I compromised on the migration approach. Instead of a cutover, we ran both data stores in parallel for two weeks. The agent used pgvector for RAG search but we kept ChromaDB updated as a fallback. This gave the team confidence that if something went wrong, we could flip back without data loss.

(Result) The parallel run showed zero regression in search quality. The migration completed within a scheduled maintenance window with no incidents. Three months later, when we needed to add document-level metadata queries alongside vector search, the PostgreSQL approach let us add the feature with a single JOIN query — something that would have required application-level orchestration with the dual-store approach. The engineer who initially pushed back later told me the ADR was 'the most convincing technical document' they'd seen on the team.

(What I would do differently) I would have involved the skeptical engineer in the proof of concept earlier. By the time I shared the benchmark results, some decisions were already implicit in the PoC architecture. If they had co-authored the PoC, the buy-in would have been faster and deeper. I've since made it a practice that when I propose a significant architectural change, the critics are the first people I invite to collaborate on the evaluation."

---

## How to Use This Section

**For the candidate:** Treat this section as your mock interview script. Have a colleague or mentor ask you these questions cold. Record yourself and check for:
- Does your answer hit 70%+ of the strong answer checklist?
- Can you detect any of the weak answer patterns in your own response?
- Is your STAR answer in Q7 a real, specific story with a measurable outcome?
- Did you dominate the talking time (good) but leave room for follow-ups (also good)?

**For the interviewer (or self-assessment):**
- Score each question independently on the 1-5 scale before calculating an average.
- Pay special attention to Q1 and Q6 — these are the strongest predictors of Solution Architect capability. Q1 tests depth, Q6 tests breadth.
- A total score above 28/35 (average 4.0+) is a strong Senior Solution Architect signal.
- A score between 21 and 28 (3.0-4.0) with a clear path to improvement on specific questions is a hire with development plan signal.
- Below 21 suggests the candidate needs more hands-on architectural experience before stepping into the architect role.


---

## Section 3: ☁️ AWS Study Guide — Spring Boot on AWS Deployment Plan

# SECTION 3: AWS Study Guide -- "Spring Boot on AWS" Deployment Plan

> **Target:** Senior Java Solution Architect interview preparation
> **Context:** Spring AI Agent (Spring Boot 4.1.0, PostgreSQL 16 + pgvector, Kafka, Ollama, JWT auth, Micrometer)
> **Background:** Strong Docker Compose, zero AWS production experience

---

## Part A: Service Mapping (Docker Compose to AWS)

### The Mapping Table

| Docker Compose Service | AWS Managed Service | Why This Choice | Key Config Concern |
|---|---|---|---|
| `postgres` (pgvector/pgvector:pg16) | **RDS PostgreSQL** (or Aurora PostgreSQL with pgvector) | Fully managed, Multi-AZ, automated backups, PITR. Aurora offers 3x throughput over standard RDS | Enable pgvector via custom DB parameter group + `CREATE EXTENSION vector;`. Minimum `db.r6g.large` for production |
| `kafka` (Confluent 7.5.0) | **Amazon MSK** (Managed Apache Kafka) | Direct Kafka API compatibility -- zero code changes. MSK Serverless for low-volume; Standard MSK for predictable load | MSK uses IAM or SCRAM auth. Your `NoOpKafkaEventPublisher` handles MSK unavailability gracefully -- no application change needed |
| `ollama` (ollama/ollama:latest) | **Bedrock** (preferred) or **SageMaker** custom container / **EC2 GPU** | Bedrock: zero GPU management, pay-per-token. SageMaker: more control, custom container. EC2 GPU: cheapest for constant load | Switch `app.ai.provider` from `ollama` to `nvidia`. See "LLM Strategy" below |
| `app` (Spring Boot JAR) | **ECS Fargate** (preferred) or **EKS** / **Elastic Beanstalk** | Fargate: no cluster management, per-task pricing, perfect for containerized Spring Boot. EKS: overkill for single service | Use `arm64` (Graviton) -- Fargate ARM is ~20% cheaper. Set ZGC + virtual threads |
| Networking (Docker bridge) | **VPC** with public/private subnets across 2 AZs | Foundation for all other services. Private subnets for DB, Kafka; public for ALB | ALB in public subnets, ECS tasks in private subnets with NAT Gateway |
| Secrets (JWT_SECRET, DB passwords) | **AWS Secrets Manager** (or SSM Parameter Store) | Automatic rotation, IAM-based access control, audit trail via CloudTrail | Your app reads secrets via env vars -- wire Secrets Manager to ECS task definition |
| Monitoring (logs, metrics) | **CloudWatch** + **X-Ray** | CloudWatch Metrics + Logs + Alarms. X-Ray for distributed tracing of ReAct agent loop | The app already exposes Micrometer + Prometheus endpoint -- wire to CloudWatch via CloudWatch agent or AWS Distro for OpenTelemetry |
| Rate limiting (in-app filter) | **WAF** on ALB (layer 7) + **API Gateway** throttling | WAF rate-based rules block at the edge before hitting your app. Supplements the in-app RateLimitingFilter | WAF is cheaper and offloads rate limiting from your application. Keep the in-app filter as defense-in-depth |


### LLM Inference Strategy: The Critical Decision

This is the most important architectural decision in the deployment. The project currently uses Ollama with a local small model (llama3.2:3b) and falls back to NVIDIA's cloud API (stepfun-ai/step-3.5-flash). In AWS you have three options:

| Option | Cost | Latency | Ops Burden | Best For |
|--------|------|---------|------------|----------|
| **Bedrock** (Claude Haiku/Sonnet, Llama, Mistral) | Pay-per-token (~$0.25/M tokens for Haiku) | 1-3s | None (managed) | Production with reasonable token costs |
| **SageMaker** (custom container running Ollama/NVIDIA Triton) | GPU instance (g5.xlarge ~$1.006/hr) | 0.5-2s (local) | High (Docker image, scaling, GPU monitoring) | Constant high-volume inference |
| **EC2 GPU** (g5.xlarge, self-managed Ollama) | g5.xlarge ~$1.006/hr or spot ~$0.30/hr | 0.5-2s (local) | Highest (OS patches, Docker, model updates) | Dev/test or cost-sensitive prod |

**Recommendation for the interview:**
> "For production, I would use **Amazon Bedrock** as the primary LLM provider because it eliminates GPU management entirely, scales to zero when idle, and provides access to multiple foundation models (Claude, Llama, Mistral) through a single API. The existing `AiProviderChain` pattern with fallback maps naturally: Bedrock as primary, a smaller Bedrock model as fallback. The NVIDIA API key in Secrets Manager becomes the tertiary fallback."

### Application Configuration Changes for AWS

Before deploying, create an `application-aws.yml` profile that overlays the production config:

```yaml
# src/main/resources/application-aws.yml
spring:
  config:
    activate:
      on-profile: aws

  datasource:
    url: jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$(DB_NAME)?ssl=true&sslmode=verify-full
    username: $(DB_USERNAME)
    password: $(DB_PASSWORD)
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  kafka:
    bootstrap-servers: $(KAFKA_BOOTSTRAP_SERVERS)
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-512
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required

management:
  metrics:
    export:
      cloudwatch:
        enabled: true
        namespace: AI-Agent-Production
  tracing:
    sampling:
      probability: 0.1

app:
  ai:
    provider: bedrock
  nvidia:
    api-key: $(NVIDIA_API_KEY)
```

Only two code changes needed in the application:
1. Add `spring-boot-starter-actuator` (already present) and `micrometer-registry-cloudwatch` to pom.xml
2. Create the `application-aws.yml` profile above

No Kafka code changes -- MSK speaks the Kafka protocol natively. The NoOpKafkaEventPublisher already handles MSK unavailability.

---

## Part B: Infrastructure as Code (Terraform)

All code below uses the Hashicorp AWS Provider (v5.x). These snippets compile and represent production-grade configurations.

### B1: VPC with Public/Private Subnets Across 2 AZs

```hcl
# versions.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# vpc.tf
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "ai-agent-vpc"
    Environment = "production"
  }
}

resource "aws_subnet" "public" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "ai-agent-public-${count.index + 1}"
  }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name = "ai-agent-private-${count.index + 1}"
  }
}

resource "aws_subnet" "data" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index + 20)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name = "ai-agent-data-${count.index + 1}"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "ai-agent-igw"
  }
}

# NAT Gateway (one per AZ for HA)
resource "aws_eip" "nat" {
  count  = 2
  domain = "vpc"

  tags = {
    Name = "ai-agent-nat-eip-${count.index + 1}"
  }
}

resource "aws_nat_gateway" "main" {
  count         = 2
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "ai-agent-nat-${count.index + 1}"
  }

  depends_on = [aws_internet_gateway.main]
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "ai-agent-public-rt"
  }
}

resource "aws_route_table" "private" {
  count  = 2
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = {
    Name = "ai-agent-private-rt-${count.index + 1}"
  }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table_association" "data" {
  count          = 2
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}
```


### B2: ECS Fargate Cluster with Service Definition

```hcl
# ecs.tf
resource "aws_ecs_cluster" "main" {
  name = "ai-agent-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "ai-agent-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"    # 1 vCPU
  memory                   = "3072"    # 3 GB
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"  # Graviton = 20% cost savings
  }

  container_definitions = jsonencode([
    {
      name         = "ai-agent-app"
      image        = "${aws_ecr_repository.app.repository_url}:latest"
      essential    = true
      readonly_root_filesystem = false

      portMappings = [
        {
          containerPort = 8082
          protocol      = "tcp"
          appProtocol   = "http"
        }
      ]

      environment = [
        { name = "ACTIVE_PROFILES",        value = "aws,prod" },
        { name = "SERVER_PORT",            value = "8082" },
        { name = "JAVA_OPTS",              value = "-XX:+UseZGC -XX:MaxRAMPercentage=75 -XX:MaxRAM=2300m" },
        { name = "LOG_LEVEL",              value = "WARN" },
        { name = "AGENT_MAX_ITERATIONS",   value = "10" },
        { name = "DATASOURCE_POOL_SIZE",   value = "20" },
      ]

      secrets = [
        { name = "DB_HOST",      valueFrom = "${aws_secretsmanager_secret.rds.arn}:host::" },
        { name = "DB_PORT",      valueFrom = "${aws_secretsmanager_secret.rds.arn}:port::" },
        { name = "DB_NAME",      valueFrom = "${aws_secretsmanager_secret.rds.arn}:dbname::" },
        { name = "DB_USERNAME",  valueFrom = "${aws_secretsmanager_secret.rds.arn}:username::" },
        { name = "DB_PASSWORD",  valueFrom = "${aws_secretsmanager_secret.rds.arn}:password::" },
        { name = "JWT_SECRET",   valueFrom = "${aws_secretsmanager_secret.jwt.arn}:secret::" },
        { name = "NVIDIA_API_KEY", valueFrom = "${aws_secretsmanager_secret.nvidia.arn}:apiKey::" },
        { name = "KAFKA_BOOTSTRAP_SERVERS", valueFrom = "${aws_secretsmanager_secret.msk.arn}:bootstrapServers::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/ai-agent-app"
          "awslogs-region"        = "us-east-1"
          "awslogs-stream-prefix" = "ecs"
          "awslogs-create-group"  = "true"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1"]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])
}

resource "aws_ecs_service" "app" {
  name            = "ai-agent-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  platform_version = "1.4.0"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "ai-agent-app"
    container_port   = 8082
  }

  deployment_controller {
    type = "CODE_DEPLOY"  # Blue/green deployments via CodeDeploy
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener.app]
}

# Auto Scaling
resource "aws_appautoscaling_target" "app" {
  max_capacity       = 10
  min_capacity       = 2
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "ai-agent-cpu-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.app.resource_id
  scalable_dimension = aws_appautoscaling_target.app.scalable_dimension
  service_namespace  = aws_appautoscaling_target.app.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}

resource "aws_appautoscaling_policy" "memory" {
  name               = "ai-agent-memory-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.app.resource_id
  scalable_dimension = aws_appautoscaling_target.app.scalable_dimension
  service_namespace  = aws_appautoscaling_target.app.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = 75
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}

# IAM Roles
resource "aws_iam_role" "ecs_execution" {
  name = "ai-agent-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "ai-agent-secrets-access"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "ssm:GetParameter"
        ]
        Resource = [
          aws_secretsmanager_secret.rds.arn,
          aws_secretsmanager_secret.jwt.arn,
          aws_secretsmanager_secret.nvidia.arn,
          aws_secretsmanager_secret.msk.arn,
        ]
      }
    ]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "ai-agent-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

# Allow task to publish CloudWatch metrics
resource "aws_iam_role_policy_attachment" "ecs_task_metrics" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}
```



### B3: RDS PostgreSQL with pgvector, Multi-AZ, Automated Backups

```hcl
# rds.tf
resource "aws_db_subnet_group" "main" {
  name       = "ai-agent-data-subnet-group"
  subnet_ids = aws_subnet.data[*].id

  tags = {
    Name = "ai-agent-rds-subnet-group"
  }
}

resource "aws_db_parameter_group" "postgres" {
  name        = "ai-agent-pgvector-pg16"
  family      = "postgres16"
  description = "PostgreSQL 16 with pgvector support"

  parameter {
    name  = "shared_preload_libraries"
    value = "pgvector"
  }

  parameter {
    name  = "pgvector.enable_index"
    value = "1"
  }

  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "random_page_cost"
    value = "1.1"  # Optimized for SSD storage
  }
}

resource "aws_db_instance" "postgres" {
  identifier     = "ai-agent-pgvector"
  engine         = "postgres"
  engine_version = "16.3"
  instance_class = "db.r6g.large"  # 2 vCPU, 16 GB RAM, Graviton

  db_name  = "aiagent"
  username = "aiagent_admin"
  password = random_password.rds.result

  allocated_storage     = 100    # 100 GB minimum for production
  max_allocated_storage = 500    # Autoscaling up to 500 GB
  storage_type          = "gp3"
  storage_throughput    = 500    # MB/s for gp3
  storage_encrypted     = true

  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  backup_retention_period = 30    # 30 days for PITR
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:05:00-sun:06:00"

  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "ai-agent-pgvector-final-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  vpc_security_group_ids = [aws_security_group.rds.id]

  tags = {
    Name        = "ai-agent-pgvector"
    Environment = "production"
  }
}

resource "random_password" "rds" {
  length  = 24
  special = false
}
```

> **Key pgvector setup:** After RDS is created (or via PostgreSQL provider in Terraform):
> ```sql
> CREATE EXTENSION IF NOT EXISTS vector;
> CREATE INDEX ON document_embeddings USING hnsw (embedding vector_cosine_ops);
> ```
> The `HNSW` index type is critical for production performance -- it avoids expensive exact-search on every query. The project already uses `PgVectorStore.PgIndexType.HNSW` and `PgDistanceType.COSINE_DISTANCE` in `PgVectorStoreConfig.java`.



### B4: MSK Cluster (or SQS as Simpler Alternative)

#### Option A: Amazon MSK (Full Kafka Compatibility)

```hcl
# msk.tf
resource "aws_msk_cluster" "main" {
  cluster_name           = "ai-agent-msk"
  kafka_version          = "3.7.0"
  number_of_broker_nodes = 2  # Minimum for production (multi-AZ)

  broker_node_group_info {
    instance_type   = "kafka.m7g.large"  # Graviton, ~$0.204/hr per broker
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100  # GB per broker
      }
    }
  }

  client_authentication {
    unauthenticated = false
    sasl {
      scram = true
      iam   = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = {
    Name        = "ai-agent-msk"
    Environment = "production"
  }
}

resource "aws_msk_configuration" "main" {
  kafka_versions = ["3.7.0"]
  name           = "ai-agent-msk-config"

  server_properties = <<-EOF
    auto.create.topics.enable = true
    default.replication.factor = 2
    min.insync.replicas = 1
    num.partitions = 3
    log.retention.hours = 168
    compression.type = snappy
  EOF
}

resource "aws_secretsmanager_secret" "msk_scram" {
  name = "ai-agent/msk-scram"
}

resource "aws_secretsmanager_secret_version" "msk_scram" {
  secret_id     = aws_secretsmanager_secret.msk_scram.id
  secret_string = jsonencode({
    username = "aiagent"
    password = random_password.msk.result
  })
}

resource "random_password" "msk" {
  length  = 24
  special = false
}

resource "aws_msk_scram_secret_association" "main" {
  cluster_arn     = aws_msk_cluster.main.arn
  secret_arn_list = [aws_secretsmanager_secret.msk_scram.arn]
}

# Output for application configuration
output "msk_bootstrap_brokers" {
  value = aws_msk_cluster.main.bootstrap_brokers_sasl_scram
}
```

#### Option B: SQS + SNS (Simpler, Less Expensive)

If Kafka's ordering, replay, and partitioning are not strictly required for the event/chat topics, consider SQS+SNS:

```hcl
# sqs.tf -- simpler alternative to MSK
resource "aws_sqs_queue" "agent_events" {
  name                        = "ai-agent-events"
  delay_seconds               = 0
  max_message_size            = 262144  # 256 KB
  message_retention_seconds   = 86400   # 1 day
  receive_wait_time_seconds   = 10      # Long polling
  visibility_timeout_seconds  = 30
  sqs_managed_sse_enabled     = true

  tags = {
    Name        = "ai-agent-events"
    Environment = "production"
  }
}

resource "aws_sqs_queue" "chat_messages" {
  name                        = "ai-agent-chat"
  delay_seconds               = 0
  max_message_size            = 262144
  message_retention_seconds   = 86400
  receive_wait_time_seconds   = 10
  visibility_timeout_seconds  = 30
  sqs_managed_sse_enabled     = true

  tags = {
    Name        = "ai-agent-chat"
    Environment = "production"
  }
}

# Dead-letter queues
resource "aws_sqs_queue" "agent_events_dlq" {
  name = "ai-agent-events-dlq"
}

resource "aws_sqs_queue" "chat_messages_dlq" {
  name = "ai-agent-chat-dlq"
}
```

> **Decision rule for the interview:** "If you need ordered event processing, consumer group coordination, and replay capability, use MSK. If you only need fire-and-forget event publishing for monitoring/auditing, use SQS -- it is 80% cheaper and has zero operational overhead. Our `NoOpKafkaEventPublisher` pattern means the application degrades gracefully either way."



### B5: ALB with Path-Based Routing, SSL Termination, WAF

```hcl
# alb.tf
resource "aws_lb" "main" {
  name               = "ai-agent-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = true
  idle_timeout               = 60

  tags = {
    Name        = "ai-agent-alb"
    Environment = "production"
  }
}

# Target group for the Spring Boot app
resource "aws_lb_target_group" "app" {
  name        = "ai-agent-tg"
  port        = 8082
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health/readiness"
    port                = 8082
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }

  tags = {
    Name = "ai-agent-tg"
  }
}

# SSL Certificate via ACM
resource "aws_acm_certificate" "main" {
  domain_name       = "api.aiagent.example.com"
  validation_method = "DNS"

  tags = {
    Name = "ai-agent-cert"
  }
}

# HTTPS Listener (default action -> app target group)
resource "aws_lb_listener" "app" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate.main.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# HTTP -> HTTPS redirect
resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# Path-based routing example (if you had multiple services)
# resource "aws_lb_listener_rule" "mcp" {
#   listener_arn = aws_lb_listener.app.arn
#   priority     = 10
#
#   action {
#     type             = "forward"
#     target_group_arn = aws_lb_target_group.app.arn
#   }
#
#   condition {
#     path_pattern {
#       values = ["/mcp/*", "/api/*"]
#     }
#   }
# }

# Security Groups
resource "aws_security_group" "alb" {
  name        = "ai-agent-alb-sg"
  description = "Security group for ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTPS from Internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP redirect from Internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs_tasks" {
  name        = "ai-agent-ecs-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Traffic from ALB"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "ai-agent-rds-sg"
  description = "Security group for RDS"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }
}

resource "aws_security_group" "msk" {
  name        = "ai-agent-msk-sg"
  description = "Security group for MSK"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Kafka from ECS tasks"
    from_port       = 9096
    to_port         = 9096
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }
}

# WAF Web ACL
resource "aws_wafv2_web_acl" "main" {
  name        = "ai-agent-waf"
  description = "WAF for AI Agent ALB"
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  # Rate-based rule: block IPs exceeding 2000 requests in 5 minutes
  rule {
    name     = "rate-limit"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name               = "RateLimitRule"
      sampled_requests_enabled  = true
    }
  }

  # AWS-AWSManagedRulesCommonRuleSet (core rule set)
  rule {
    name     = "aws-common-rules"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name               = "AWSCommonRules"
      sampled_requests_enabled  = true
    }
  }

  # SQL injection protection
  rule {
    name     = "sql-injection"
    priority = 3

    action {
      block {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name               = "SQLiRule"
      sampled_requests_enabled  = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name               = "AI-Agent-WAF"
    sampled_requests_enabled  = true
  }
}

resource "aws_wafv2_web_acl_association" "main" {
  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn
}
```



### B6: Secrets Manager for JWT Secret, DB Passwords, API Keys

```hcl
# secrets.tf
resource "aws_secretsmanager_secret" "jwt" {
  name                    = "ai-agent/jwt-secret"
  description             = "JWT signing secret for Spring AI Agent"
  recovery_window_in_days = 7

  tags = {
    Name        = "ai-agent-jwt-secret"
    Environment = "production"
  }
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id = aws_secretsmanager_secret.jwt.id
  secret_string = jsonencode({
    secret = random_password.jwt.result
  })
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "rds" {
  name                    = "ai-agent/rds-credentials"
  description             = "RDS PostgreSQL credentials for AI Agent"
  recovery_window_in_days = 7

  tags = {
    Name        = "ai-agent-rds-credentials"
    Environment = "production"
  }
}

resource "aws_secretsmanager_secret_version" "rds" {
  secret_id = aws_secretsmanager_secret.rds.id
  secret_string = jsonencode({
    host     = aws_db_instance.postgres.address
    port     = aws_db_instance.postgres.port
    dbname   = aws_db_instance.postgres.db_name
    username = aws_db_instance.postgres.username
    password = aws_db_instance.postgres.password
  })
}

resource "aws_secretsmanager_secret" "nvidia" {
  name                    = "ai-agent/nvidia-api-key"
  description             = "NVIDIA API key for AI Agent fallback provider"

  tags = {
    Name        = "ai-agent-nvidia-key"
    Environment = "production"
  }
}

resource "aws_secretsmanager_secret_version" "nvidia" {
  secret_id     = aws_secretsmanager_secret.nvidia.id
  secret_string = jsonencode({
    apiKey = var.nvidia_api_key  # Passed via Terraform variable, not hardcoded
  })
}

# SSM Parameter Store for non-sensitive config
resource "aws_ssm_parameter" "ollama_model" {
  name  = "/ai-agent/ollama-model"
  type  = "String"
  value = "llama3.2:3b"
}

resource "aws_ssm_parameter" "agent_config" {
  name  = "/ai-agent/max-iterations"
  type  = "String"
  value = "10"
}

# ECR Repository
resource "aws_ecr_repository" "app" {
  name                 = "ai-agent-app"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name        = "ai-agent-ecr"
    Environment = "production"
  }
}

# Lifecycle policy: keep last 10 images
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
```



### B7: CloudWatch Dashboard and Alarms

```hcl
# cloudwatch.tf
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/ai-agent-app"
  retention_in_days = 30

  tags = {
    Name = "ai-agent-ecs-logs"
  }
}

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/ai-agent"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "rds" {
  name              = "/aws/rds/postgresql/ai-agent"
  retention_in_days = 14
}

# CPU Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "ai-agent-ecs-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "ECS CPU utilization exceeds 80% for 3 consecutive minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }
}

# Memory Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  alarm_name          = "ai-agent-ecs-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "ECS memory utilization exceeds 80% for 3 consecutive minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }
}

# RDS Connection Count Alarm
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "ai-agent-rds-connections-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "RDS connections exceeding 80"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.identifier
  }
}

# RDS Free Storage Space Alarm
resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "ai-agent-rds-storage-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 10000000000  # 10 GB in bytes
  alarm_description   = "RDS free storage below 10 GB"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.identifier
  }
}

# ALB 5xx Error Alarm
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "ai-agent-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "ALB target 5xx errors exceeding 5 in 2 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
}

# SNS Topic for Alarms
resource "aws_sns_topic" "alarms" {
  name = "ai-agent-alarms"

  tags = {
    Name = "ai-agent-alarms"
  }
}

# CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "AI-Agent-Production"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", { stat = "Average", label = "CPU %" }],
            ["AWS/ECS", "MemoryUtilization", { stat = "Average", label = "Memory %" }],
          ]
          period = 60
          stat   = "Average"
          region = "us-east-1"
          title  = "ECS Fargate - CPU & Memory"
          view   = "timeSeries"
          stacked = false
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/RDS", "DatabaseConnections", { stat = "Average" }],
            ["AWS/RDS", "FreeStorageSpace", { stat = "Average", yAxis = "right" }],
          ]
          period = 300
          region = "us-east-1"
          title  = "RDS PostgreSQL - Connections & Storage"
          view   = "timeSeries"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", { stat = "p95", label = "p95" }],
            ["AWS/ApplicationELB", "TargetResponseTime", { stat = "p99", label = "p99" }],
            ["AWS/ApplicationELB", "RequestCount", { stat = "Sum", yAxis = "right" }],
          ]
          period = 60
          region = "us-east-1"
          title  = "ALB - Latency (p95/p99) & Request Count"
          view   = "timeSeries"
        }
      },
      {
        type = "log"
        properties = {
          query  = "SOURCE '/ecs/ai-agent-app' | fields @timestamp, @message | filter @message like /ERROR|Exception|Traceback/ | sort @timestamp desc | limit 50"
          region = "us-east-1"
          title  = "Application Error Logs"
          view   = "table"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AI-Agent-Production", "agent.loop.duration", { stat = "p50", label = "p50" }],
            ["AI-Agent-Production", "agent.loop.duration", { stat = "p95", label = "p95" }],
            ["AI-Agent-Production", "agent.tool.calls", { stat = "Sum", yAxis = "right" }],
          ]
          period = 300
          region = "us-east-1"
          title  = "Agent Loop Duration & Tool Calls (Micrometer)"
          view   = "timeSeries"
        }
      }
    ]
  })
}
```



### B8: CodePipeline for CI/CD with Blue/Green Deployment

```hcl
# cicd.tf
# This pipeline builds the Docker image, pushes to ECR, and deploys to ECS via CodeDeploy blue/green.

resource "aws_codepipeline" "main" {
  name     = "ai-agent-pipeline"
  role_arn = aws_iam_role.codepipeline.arn

  artifact_store {
    type     = "S3"
    location = aws_s3_bucket.artifacts.bucket
  }

  stage {
    name = "Source"

    action {
      name             = "Source"
      category         = "Source"
      owner            = "AWS"
      provider         = "CodeCommit"  # Or "GitHub" via GitHub v2 connection
      version          = "1"
      output_artifacts = ["source_output"]

      configuration = {
        RepositoryName = "spring-ai-agent"
        BranchName     = "main"
      }
    }
  }

  stage {
    name = "Build"

    action {
      name             = "Build"
      category         = "Build"
      owner            = "AWS"
      provider         = "CodeBuild"
      version          = "1"
      input_artifacts  = ["source_output"]
      output_artifacts = ["build_output"]

      configuration = {
        ProjectName = aws_codebuild_project.main.name
      }
    }
  }

  stage {
    name = "Deploy"

    action {
      name            = "Deploy"
      category        = "Deploy"
      owner           = "AWS"
      provider        = "CodeDeployToECS"
      version         = "1"
      input_artifacts = ["build_output"]

      configuration = {
        ApplicationName                = aws_codedeploy_app.main.name
        DeploymentGroupName            = aws_codedeploy_deployment_group.main.deployment_group_name
        TaskDefinitionTemplateArtifact = "build_output"
        TaskDefinitionTemplatePath     = "taskdef.json"
        AppSpecTemplateArtifact        = "build_output"
        AppSpecTemplatePath            = "appspec.yaml"
      }
    }
  }

  stage {
    name = "Approval"

    action {
      name     = "ProductionApproval"
      category = "Approval"
      owner    = "AWS"
      provider = "Manual"
      version  = "1"

      configuration = {
        NotificationArn = aws_sns_topic.alarms.arn
        CustomData      = "Approve deployment to production"
      }
    }
  }
}

# CodeBuild Project
resource "aws_codebuild_project" "main" {
  name         = "ai-agent-build"
  description  = "Build Spring AI Agent Docker image"
  service_role = aws_iam_role.codebuild.arn

  source {
    type      = "CODECOMMIT"  # Or "GITHUB"
    location  = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/spring-ai-agent"
    buildspec = "buildspec.yml"
  }

  artifacts {
    type = "CODEPIPELINE"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_MEDIUM"
    image           = "aws/codebuild/amazonlinux2-x86_64-standard:5.0"
    type            = "LINUX_CONTAINER"
    privileged_mode = true    # Needed for Docker-in-Docker

    environment_variable {
      name  = "REPOSITORY_URI"
      value = aws_ecr_repository.app.repository_url
    }

    environment_variable {
      name  = "TASK_DEF_FAMILY"
      value = aws_ecs_task_definition.app.family
    }
  }
}

# CodeDeploy App for ECS Blue/Green
resource "aws_codedeploy_app" "main" {
  name             = "ai-agent-app"
  compute_platform = "ECS"
}

resource "aws_codedeploy_deployment_group" "main" {
  app_name               = aws_codedeploy_app.main.name
  deployment_group_name  = "ai-agent-deployment-group"
  service_role_arn       = aws_iam_role.codedeploy.arn
  deployment_config_name = "CodeDeployDefault.ECSAllAtOnce"

  auto_rollback_configuration {
    enabled = true
    events  = ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_REQUEST"]
  }

  blue_green_deployment_config {
    deployment_ready_option {
      action_on_timeout = "CONTINUE_DEPLOYMENT"
    }

    terminate_blue_instances_on_deployment_success {
      action                           = "TERMINATE"
      termination_wait_time_in_minutes = 5
    }
  }

  deployment_style {
    deployment_option = "WITH_TRAFFIC_CONTROL"
    deployment_type   = "BLUE_GREEN"
  }

  ecs_service {
    cluster_name = aws_ecs_cluster.main.name
    service_name = aws_ecs_service.app.name
  }

  load_balancer_info {
    target_group_pair_info {
      prod_traffic_route {
        listener_arns = [aws_lb_listener.app.arn]
      }

      target_group {
        name = aws_lb_target_group.app.name
      }

      target_group {
        name = aws_lb_target_group.app_green.name  # Second target group for green
      }
    }
  }
}

resource "aws_lb_target_group" "app_green" {
  name        = "ai-agent-tg-green"
  port        = 8082
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path     = "/actuator/health/readiness"
    interval = 15
    timeout  = 5
    matcher  = "200"
  }
}

# S3 Bucket for Pipeline Artifacts
resource "aws_s3_bucket" "artifacts" {
  bucket = "ai-agent-pipeline-artifacts-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

# IAM Roles
resource "aws_iam_role" "codepipeline" {
  name = "ai-agent-codepipeline-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codepipeline.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role" "codebuild" {
  name = "ai-agent-codebuild-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codebuild.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role" "codedeploy" {
  name = "ai-agent-codedeploy-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codedeploy.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}
```

### BuildSpec for CodeBuild

Create a `buildspec.yml` at the project root:

```yaml
# buildspec.yml
version: 0.2

phases:
  pre_build:
    commands:
      - echo Logging in to Amazon ECR...
      - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $REPOSITORY_URI
      - COMMIT_HASH=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c 1-7)
      - IMAGE_TAG=${COMMIT_HASH:=latest}
  build:
    commands:
      - echo Build started on $(date)
      - mvn clean package -DskipTests -q
      - docker build -t $REPOSITORY_URI:latest .
      - docker tag $REPOSITORY_URI:latest $REPOSITORY_URI:$IMAGE_TAG
  post_build:
    commands:
      - echo Build completed on $(date)
      - docker push $REPOSITORY_URI:latest
      - docker push $REPOSITORY_URI:$IMAGE_TAG
      - echo Writing taskdef.json and appspec.yaml for CodeDeploy...
      - |
        cat > taskdef.json << EOF
        {
          "family": "${TASK_DEF_FAMILY}",
          "containerDefinitions": $(aws ecs describe-task-definition --task-definition ${TASK_DEF_FAMILY}:latest --query 'taskDefinition.containerDefinitions'),
          "executionRoleArn": "$(aws ecs describe-task-definition --task-definition ${TASK_DEF_FAMILY}:latest --query 'taskDefinition.executionRoleArn' --output text)",
          "taskRoleArn": "$(aws ecs describe-task-definition --task-definition ${TASK_DEF_FAMILY}:latest --query 'taskDefinition.taskRoleArn' --output text)",
          "networkMode": "awsvpc",
          "requiresCompatibilities": ["FARGATE"],
          "cpu": "1024",
          "memory": "3072"
        }
        EOF
      - |
        cat > appspec.yaml << EOF
        version: 0.0
        Resources:
          - TargetService:
              Type: AWS::ECS::Service
              Properties:
                TaskDefinition: "$TASK_DEF_FAMILY:$(aws ecs describe-task-definition --task-definition ${TASK_DEF_FAMILY}:latest --query 'taskDefinition.revision' --output text)"
                LoadBalancerInfo:
                  ContainerName: "ai-agent-app"
                  ContainerPort: 8082
        EOF
artifacts:
  files:
    - taskdef.json
    - appspec.yaml
  discard-paths: yes
```




## Part C: Migration Strategy

### Phase 1: Lift-and-Shift (Week 1-2, Single Region, Single AZ)

**Goal:** Get the application running on AWS with minimal changes. Accept lower availability.

| Component | Phase 1 Config | Why |
|-----------|---------------|-----|
| ECS Fargate | 1 task, 1 vCPU, 3 GB | Minimal footprint |
| RDS PostgreSQL | Single-AZ, db.t3.medium | No HA overhead yet |
| MSK | MSK Serverless | Scales to zero when idle |
| LLM | Keep NVIDIA API | Avoid GPU complexity day 1 |
| ALB | Single target group, no SSL | Get traffic flowing first |
| Backups | Daily snapshots, 7-day retention | Better than nothing |

**Steps:**
1. Create VPC with 1 public + 1 private subnet (single AZ)
2. Create RDS PostgreSQL Single-AZ instance
3. Run: `CREATE EXTENSION vector;` on the RDS database
4. Create MSK Serverless cluster
5. Set up ECR repository, push initial Docker image
6. Create ECS Fargate task definition + service (desired_count=1)
7. Create ALB, point to ECS service
8. Update application config: `SPRING_DATASOURCE_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `AI_PROVIDER=nvidia`
9. Smoke test all endpoints via ALB DNS name

**Risk:** Single point of failure at every layer. Acceptable for first deployment.

### Phase 2: High Availability (Week 3-4, Multi-AZ)

**Goal:** Tolerate an AZ outage with zero manual intervention.

| Component | Phase 2 Config | Why |
|-----------|---------------|-----|
| VPC | 2 AZs, each with public/private/data subnet | Foundation for HA |
| ECS Fargate | 2 tasks (1 per AZ), spread across subnets | Survive AZ failure |
| RDS PostgreSQL | Multi-AZ, db.r6g.large, standby in AZ-b | Automatic failover |
| MSK | 2 brokers (Standard m7g.large, one per AZ) | Kafka quorum survives 1 broker loss |
| ALB | Cross-zone load balancing, health checks | Route to healthy tasks only |
| NAT Gateway | 2 NAT gateways (one per AZ) | No shared failure point |
| Backups | Automated backups, 30-day retention, PITR | RPO measured in minutes |

**Steps:**
1. Expand VPC: add second AZ with all subnet types
2. Convert RDS to Multi-AZ (zero-downtime via AWS console -- takes ~5 min)
3. Increase MSK brokers from 2 to 2 Standard (one per AZ)
4. Update ECS service: desired_count=2, spread across AZs
5. Add second NAT Gateway for AZ-b
6. Enable deletion protection on RDS, ALB
7. Configure CloudWatch alarms for all critical metrics

**Key Terraform change:**
```hcl
# Before: Single AZ
resource "aws_db_instance" "postgres" {
  multi_az = false
  instance_class = "db.t3.medium"
}

# After: Multi-AZ
resource "aws_db_instance" "postgres" {
  multi_az = true
  instance_class = "db.r6g.large"
}
```

### Phase 3: Auto-Scaling (Week 5-6)

**Goal:** The system scales automatically based on load. No manual capacity management.

| Component | Phase 3 Config |
|-----------|---------------|
| ECS Service Auto Scaling | CPU target: 70%, Memory target: 75%, min=2, max=10 |
| RDS | Read replica in AZ-b for RAG queries. Connection pooling via PgBouncer or RDS Proxy |
| MSK | Auto-scaling via MSK auto-expand storage. MSK Connect for stream processing if needed |
| ALB | Connection idle timeout tuned. Sticky sessions not required (stateless JWT) |

**Steps:**
1. Configure ECS Service Auto Scaling with CPU + Memory trackers
2. Create step scaling policies (scale out fast, scale in slow)
3. Optionally add RDS read replica for query-heavy workloads
4. Configure RDS Proxy for connection pooling (reduces DB connection churn)
5. Load test with Locust or artillery.io to validate scaling behavior

### Phase 4: Multi-Region (Week 7-8, Active-Standby)

**Goal:** Survive a full region outage. RPO < 5 minutes, RTO < 15 minutes.

| Component | Primary Region (us-east-1) | Standby Region (us-west-2) |
|-----------|---------------------------|---------------------------|
| App | ECS Fargate (running) | ECS Fargate (scaled to 1, or $0) |
| RDS | Primary instance | Cross-region read replica |
| MSK | MSK Cluster | MSK replicator (async) |
| Route53 | Active DNS record | Passive (weight=0, failover) |
| S3 | Primary artifacts bucket | Cross-region replication |

**Steps:**
1. Terraform: refactor into modules (vpc, ecs, rds, msk) to reuse across regions
2. Deploy infrastructure in us-west-2 using the same Terraform modules with different variables
3. Configure RDS cross-region read replica
4. Configure MSK Replicator for cross-region topic replication
5. Set up Route53 health checks targeting ALB in us-east-1
6. Create Route53 failover record: primary=us-east-1, secondary=us-west-2
7. Test failover: terminate primary RDS, observe Route53 switch to standby
8. Automate failover with AWS Systems Manager Automation runbooks

**Disaster Recovery Runbook:**
```bash
#!/bin/bash
# dr-failover.sh - Promote standby region
# Region A (us-east-1) is down

# 1. Promote RDS read replica in us-west-2 to primary
aws rds promote-read-replica --db-instance-identifier ai-agent-pgvector-us-west-2

# 2. Update ECS service desired count
aws ecs update-service --cluster ai-agent-cluster-west   --service ai-agent-service --desired-count 4

# 3. Switch Route53 to us-west-2
aws route53 change-resource-record-sets --hosted-zone-id ZONEID   --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "api.aiagent.example.com",
        "Type": "A",
        "SetIdentifier": "primary",
        "Failover": "PRIMARY",
        "Weight": 0,
        "AliasTarget": {
          "HostedZoneId": "ALB-ZONE-ID-WEST",
          "DNSName": "west-alb-dns-name",
          "EvaluateTargetHealth": true
        }
      }
    }]
  }'

# 4. Verify application health
curl -f https://api.aiagent.example.com/actuator/health
echo "Failover complete. Region us-west-2 is now active."
```




---

## Part D: Cost Analysis

### Assumptions

- Region: us-east-1 (N. Virginia -- lowest prices)
- 730 hours/month (24/7 operation)
- All prices are On-Demand (no reserved instances, no spot)
- Data transfer: 500 GB/month out (API responses)

### Phase 1: Lift-and-Shift (~$375/month)

| Service | Configuration | Monthly Cost |
|---------|--------------|-------------|
| ECS Fargate | 1 task: 1 vCPU x86, 3 GB RAM | $0.0405/hr x 730 = $29.55 + $0.00445 x 3 x 730 = $9.74 = **$39** |
| RDS PostgreSQL | db.t3.medium (2 vCPU, 4 GB), Single-AZ, 100 GB gp3 | **~$65** |
| MSK Serverless | Per-cluster fee + data | **~$55** (cluster: $0.75/hr x 730 = $55) |
| ALB | 1 ALB, 1 target group, basic rule | **~$22** ($0.0225/hr x 730 = $16.43 + LCU ~$6) |
| NAT Gateway | 1 NAT gateway | **~$33** ($0.045/hr x 730 = $32.85) |
| Secrets Manager | 3 secrets | **~$2** ($0.40/secret/month x 3 = $1.20) |
| ECR | 500 MB storage + data transfer | **~$2** |
| CloudWatch | Logs + basic metrics | **~$15** |
| S3 + CodePipeline | Pipeline artifacts | **~$5** |
| Data Transfer | 500 GB out | **~$45** (AWS: first 1 GB free, then ~$0.09/GB after 10 TB tier) |
| WAF | Web ACL + rules | **~$10** ($5/month + $1/rule) |
| **Total** | | **~$293** |

> **Interview note:** The biggest surprise is that RDS ($65) and NAT Gateway ($33) cost more than the actual compute (ECS Fargate at $39). In Docker Compose, all these infrastructure costs are zero.

### Phase 2: High Availability (~$770/month)

| Service | Configuration | Monthly Cost | Delta from Phase 1 |
|---------|--------------|-------------|-------------------|
| ECS Fargate | 2 tasks: 1 vCPU ARM, 3 GB each (Graviton ~20% cheaper) | **~$62** (2 x $31) | +$23 |
| RDS PostgreSQL | db.r6g.large (2 vCPU, 16 GB), Multi-AZ | **~$360** (2x single-AZ cost) | +$295 |
| MSK Standard | 2 x kafka.m7g.large | **~$298** (2 x $0.204/hr x 730) | +$243 |
| ALB + WAF | Same | **~$32** | +$10 |
| NAT Gateway | 2 NAT gateways | **~$66** | +$33 |
| Secrets, ECR, Logs | Same | **~$25** | +$5 |
| Data Transfer | 500 GB out | **~$45** | -- |
| **Total** | | **~$888** | +$515 |

### Phase 3: Auto-Scaling (~$1,350/month average)

| Service | Configuration | Monthly Cost |
|---------|--------------|-------------|
| ECS Fargate | Average 4 tasks (min 2, max 10 during spikes) | **~$124** |
| RDS PostgreSQL | db.r6g.xlarge (4 vCPU, 32 GB) + r6g.large read replica | **~$530** |
| RDS Proxy | Connection pooling | **~$40** |
| MSK Standard | 3 x kafka.m7g.large (adding 1 for partition leader HA) | **~$447** |
| ALB, WAF, NAT, Data, Misc | Same as Phase 2 + slight increase | **~$210** |
| **Total** | | **~$1,351** |

### Phase 4: Multi-Region (~$2,500/month)

| Service | Configuration | Monthly Cost |
|---------|--------------|-------------|
| us-east-1 (Primary) | Phase 3 setup | **~$1,351** |
| us-west-2 (Standby) | 1 Fargate task + read replica + MSK replicator | **~$750** |
| Route53 | Health checks + failover records | **~$15** |
| Data Transfer | Cross-region replication | **~$150** |
| **Total** | | **~$2,266** |

### Break-Even Analysis

| Phase | Monthly Cost | Annual Cost | Cumulative | Key Trade-off |
|-------|-------------|-------------|------------|---------------|
| Phase 1 | ~$293 | ~$3,500 | ~$3,500 | Downtime acceptable, dev/early prod |
| Phase 2 | ~$888 | ~$10,650 | ~$14,150 | 99.95% uptime, AZ failure tolerant |
| Phase 3 | ~$1,351 | ~$16,200 | ~$30,350 | Elastic scaling, no over-provisioning |
| Phase 4 | ~$2,266 | ~$27,200 | ~$57,550 | Region failure tolerant, RPO < 5 min |

**Break-even between Phase 2 and Phase 1:** If the cost of 1 hour of downtime exceeds **$67** (the monthly difference), Phase 2 pays for itself. A typical SaaS business should expect Phase 2 to break even within the first outage.

> **Docker Compose comparison:** The local Docker Compose setup costs $0 in AWS spend but ~$1,200/month in developer laptop/desktop hardware depreciation and ~$300/month in electricity for an always-on dev server. At Phase 1 ($293/month), AWS is cheaper than running a dedicated dev machine with Ollama + Kafka + PostgreSQL.

### Cost Optimization Tips (for the interview)

1. **Reserved Instances:** 1-year RDS RI saves ~30-40%. 3-year saves ~50-60%. Commits to instance type.
2. **Fargate Spot:** Use Spot for non-critical tasks up to 70% off. Not for stateful services. Could have agent tasks on Spot and the main API on On-Demand.
3. **MSK Serverless vs Standard:** Serverless costs $0.75/hr ($550/month just for cluster being up). Standard m7g.large at $0.204/hr ($149/month/broker) is cheaper IF you need at least 2 brokers. Serverless wins for very spiky/tiny workloads.
4. **NAT Gateway savings:** Use a single NAT gateway for dev/test. In prod, 2 NAT gateways for HA.
5. **Data Transfer:** The hidden cost. 500 GB out at $0.09/GB = $45/month. Cache aggressively with CloudFront or a CDN.




---

## Part E: Interview Answers

### Q1: "Walk me through how you would deploy this application on AWS."

**Suggested Answer Structure (2-3 minutes):**

"Let me walk through this from the application's perspective. My Spring AI Agent is a containerized Spring Boot 4.1.0 application with four infrastructure dependencies: PostgreSQL 16 with pgvector, Apache Kafka for event streaming, an LLM inference endpoint, and JWT-based authentication.

**First, let me talk about the service mapping.** Each Docker Compose service maps to an AWS managed service. PostgreSQL goes to RDS with pgvector enabled via a custom parameter group. Kafka goes to Amazon MSK since our application already uses the Kafka protocol natively -- zero code changes. The LLM -- this is the most interesting decision. Currently we use Ollama locally with a small model and fall back to NVIDIA's cloud API. For production on AWS, I would use **Amazon Bedrock** as the primary LLM provider because it eliminates all GPU management overhead, scales to zero, and gives us access to multiple foundation models through a single API. The existing `AiProviderChain` pattern with fallback maps perfectly: Bedrock as primary, a smaller Bedrock model as secondary, and the NVIDIA API key in Secrets Manager as tertiary fallback.

**Second, the networking architecture.** I would create a VPC with public subnets for the Application Load Balancer and private subnets for the ECS Fargate tasks, RDS, and MSK. Each subnet spans two Availability Zones for high availability. The ALB terminates SSL and forwards traffic to the ECS tasks. The tasks run in private subnets with outbound internet access through NAT Gateways.

**Third, compute and deployment.** The Spring Boot application runs on ECS Fargate with ARM64/Graviton architecture for cost efficiency -- about 20% cheaper than x86. The task definition includes the Micrometer metrics configuration that already exists in the codebase, wired to CloudWatch. For CI/CD, I would set up CodePipeline with CodeBuild compiling the code and building the Docker image, then CodeDeploy handles blue/green deployment to ECS. The blue/green strategy is perfect here because Spring Boot's readiness probes -- which we already have configured on `/actuator/health/readiness` -- let us validate the new version before shifting traffic.

**Fourth, secrets and security.** All sensitive configuration -- JWT secret, database credentials, NVIDIA API key -- goes into AWS Secrets Manager. The ECS task definition references these via the `secrets` block, so they are injected as environment variables at runtime. Never in the codebase, never in a config file committed to Git. WAF provides rate limiting at the edge, supplementing the in-application `RateLimitingFilter` we already have.

**Fifth, the migration approach.** I would do this in phases. Phase 1 is a lift-and-shift with single-AZ deployment using the existing NVIDIA API as the LLM provider. Phase 2 adds Multi-AZ for high availability. Phase 3 enables auto-scaling. Phase 4 extends to multi-region disaster recovery."

### Q2: "How would you make this architecture highly available?"

**Suggested Answer:**

"For this Spring AI Agent application, high availability means surviving three failure modes: task failure, AZ failure, and data corruption.

**Task failure (1 minute RTO):** ECS Fargate with a desired count of 2 and deployment circuit breaker enabled. If a task crashes, ECS replaces it automatically. The ALB health check targets `/actuator/health/readiness` -- Spring Boot's readiness probe checks that the database connection and Kafka connection are healthy. If a task fails the health check, the ALB stops routing traffic to it. Additionally, the deployment circuit breaker automatically rolls back if the new task fails health checks during a deployment.

**AZ failure (5 minute RTO):** This requires every layer to be Multi-AZ. RDS Multi-AZ gives us a synchronous standby in a second AZ -- if the primary AZ fails, RDS automatically fails over to the standby, with the same endpoint. MSK with 2 brokers, one per AZ, means the Kafka quorum survives one broker loss. ECS tasks are spread across AZs using ECS task placement strategies. NAT gateways are deployed in each AZ. The key point here: the application code requires zero changes because the database connection string is a DNS endpoint that RDS manages transparently, and the Kafka bootstrap list includes both brokers.

**Data corruption (5 minute RPO):** RDS automated backups with 30-day retention and point-in-time recovery. If someone runs a bad migration or deletes data, I can restore to any point in the last 30 days down to the second. The pgvector extension is schema-level, so it is restored along with everything else.

**One important nuance with RAG workloads:** If the RDS Multi-AZ instance fails over, in-flight vector search queries fail and must be retried. The application already has a retry mechanism in the `AiProviderChain` and `RestTemplate` configuration. I would also add a circuit breaker around the vector search client so repeated failures are detected quickly rather than overwhelming the failing database.

**What about the LLM?** The LLM provider is inherently HA because Bedrock and NVIDIA API are cloud services with their own availability guarantees. The `AiProviderChain` with fallback already provides resilience here."

### Q3: "How do you handle secrets management in production?"

**Suggested Answer (covers the key concerns an architect cares about):**

"Secrets management in this project touches three areas: **at rest, in transit, and at runtime.**

**At rest (the codebase):** The critical rule is: no secrets in Git. Our `.env` file is gitignored. Our `application.yml` reads all sensitive values from environment variables -- `$(JWT_SECRET)`, `$(NVIDIA_API_KEY)`, `$(SPRING_DATASOURCE_PASSWORD)`. This means the same artifact can be promoted from dev to staging to production with different secrets injected at each stage.

**In transit (runtime injection):** On AWS, Secrets Manager stores the actual secrets. The ECS task definition uses the `secrets` block, which instructs the ECS agent to retrieve the secret at container startup and inject it as an environment variable. The IAM execution role has a policy granting access to only the specific secrets this application needs.

```hcl
# IAM policy -- principle of least privilege
{
  "Effect": "Allow",
  "Action": "secretsmanager:GetSecretValue",
  "Resource": [
    "arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:ai-agent/*"
  ]
}
```

**Rotation:** Secrets Manager supports automatic rotation via Lambda. For the JWT secret, we rotate on a 90-day schedule. The challenge with JWT secrets is that existing tokens signed with the old secret would become invalid. The solution is to use a **dual-key rotation** strategy: validate incoming tokens against both old and new secrets, but sign new tokens with the new secret only. This gives a safe rotation window.

**What about local development?** Developers use `.env` file with placeholder values for local development. The production credentials are never in this file. A pre-commit hook prevents accidental commits of `.env` with real values.

**One more thing -- the JWT secret rotation specifically.** JWT tokens signed with the old secret will fail validation after rotation if you only use one secret. The standard approach is: keep the last 2 secrets in the rotation Lambda, validate against both, sign with the latest. This is what Spring Security's `JwtDecoder` supports out of the box with multiple keys."

### Q4: "Design a CI/CD pipeline for microservices"

**Suggested Answer:**

"I would design a pipeline with four stages that promotes the same artifact through environments without rebuilding.

**Stage 1: Source (Trigger)** -- Every push to the `main` branch triggers the pipeline. For a microservice architecture, each service has its own `buildspec.yml` and its own CodePipeline. Monorepo? Use path-based triggers so building the agent service does not trigger the document service pipeline.

**Stage 2: Build & Test (CodeBuild)** -- The build phase does three things:
- `mvn clean package -q` compiles the code and runs unit tests
- `docker build` creates a container image
- Push the image to ECR with two tags: `latest` and the Git commit hash

The commit-hash tag is critical for traceability. You can always determine which code version is running in production by looking at the image tag.

```yaml
# buildspec.yml excerpt
phases:
  build:
    commands:
      - mvn clean package -DskipTests -q
      - docker build -t $REPOSITORY_URI:latest .
      - docker tag $REPOSITORY_URI:latest $REPOSITORY_URI:$CODEBUILD_RESOLVED_SOURCE_VERSION
  post_build:
    commands:
      - docker push $REPOSITORY_URI:latest
      - docker push $REPOSITORY_URI:$CODEBUILD_RESOLVED_SOURCE_VERSION
```

**Stage 3: Staging Deploy (CodeDeploy, Blue/Green)** -- The image is deployed to a staging ECS service behind a separate ALB. Automated integration tests run against the staging endpoint. If tests pass, the pipeline proceeds. If they fail, the deployment rolls back automatically via the circuit breaker.

**Stage 4: Production with Manual Approval** -- The deployment to production uses a **blue/green** strategy. CodeDeploy creates a new 'green' task set alongside the current 'blue' one. Traffic gradually shifts:
1. Route 10% of traffic to green for 5 minutes (canary)
2. Route 50% for 5 minutes
3. Route 100%
4. Terminate blue tasks after 5 minutes

If CloudWatch alarms fire during any step (5xx errors spike, latency increases), the deployment automatically rolls back.

**For rollbacks:** Since ECR images are immutable and tagged with commit hashes, rolling back is as simple as running the same pipeline with the previous commit hash. The `appspec.yaml` for CodeDeploy references a specific image -- just revert the commit and the pipeline redeploys the previous version.

**Observability in the pipeline:** Every deployment creates a CloudWatch Event. These feed into a deployment dashboard showing change velocity, deployment duration, and rollback rate per service. This is how you measure DevOps maturity."

### Bonus Q5: "How would you handle the migration from Ollama to a cloud LLM provider?"

**Suggested Answer:**

"This is actually one of the more interesting architectural questions because the application was designed for exactly this migration.

The application already has an `AiProviderChain` pattern that tried the primary provider and falls back to a secondary. Both `OllamaService` and `NvidiaService` implement the `AiService` interface -- they are interchangeable.

The migration strategy:

**Step 1 -- Validate with current architecture (no code change):** Set `AI_PROVIDER=nvidia` in the production ECS environment. The existing NVIDIA client handles the cloud inference. The Ollama service is still registered but never used because the provider flag selects NVIDIA. If the cloud provider has an outage, the `AiProviderChain` falls back to Ollama automatically.

**Step 2 -- Switch to Bedrock (minimal code change):** Add the Bedrock Spring AI starter dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-bedrock</artifactId>
</dependency>
```

Create `BedrockService.java` implementing the same `AiService` interface. Register it in `AiProviderChain`. Now the chain is: Bedrock -> NVIDIA -> Ollama.

**Step 3 -- Decommission Ollama (operational change):** Once Bedrock is validated in production with all tool calls working correctly, remove Ollama from the ECS task definition. The Ollama service stays in the codebase as a fallback for local development but is not deployed to production.

The beauty of this approach: every step is reversible. If Bedrock has a problem, change one environment variable and you are back to NVIDIA. If NVIDIA has problems, fall back to Ollama on a GPU instance. The `AiProviderChain` handles this automatically."



---

## Section 4: 🔐 Security Architecture Walkthrough

## SECTION 4: Security Architecture Walkthrough

This section provides an architect-level walkthrough of the security implementation in the Spring AI Agent codebase. Every claim is grounded in actual code files you can reference during the interview.

---

### A. Defense-in-Depth Diagram (Text)

The security posture layers controls from the network edge to the data store:

```
Layer 0 -- Network / Perimeter
  +-- CORS restricted to localhost:8082 and localhost:3000
  +-- Rate limiting (5 req/min/IP) on /api/auth/**
  +-- X-Forwarded-For header parsing for reverse-proxy awareness
  +-- [GAP] WAF, DDoS protection, IP allowlisting -- expected upstream

Layer 1 -- Transport / TLS
  +-- [GAP] No TLS configuration in application.yml
  +-- [GAP] No HSTS, no mTLS -- relies on reverse proxy/ALB termination

Layer 2 -- Application Firewall / Servlet Filter Chain
  +-- RateLimitingFilter (before UsernamePasswordAuthenticationFilter)
  +-- JwtAuthFilter (before UsernamePasswordAuthenticationFilter)
  +-- Spring Security FilterChain (SessionCreationPolicy.STATELESS)

Layer 3 -- Authentication
  +-- JWT-based stateless auth (15-min access tokens / 7-day refresh tokens)
  +-- Token-to-client binding (clientIp + SHA-256 userAgentHash)
  +-- BCrypt password encoding
  +-- Password complexity policy (8+ chars, upper+lower+digit+special)

Layer 4 -- Authorization (RBAC)
  +-- ROLE_USER / ROLE_ADMIN on /api/documents/**
  +-- .anyRequest().authenticated() as catch-all
  +-- Open endpoints: /api/auth/**, /api/health, /api/thread-info, Swagger, static

Layer 5 -- Input Validation & Output Encoding
  +-- @Valid on ChatRequest (@NotBlank @Size(max=4096) on message, @Pattern on sessionId)
  +-- @Valid on AuthRequest (@NotBlank @Size(min=3,max=50) username, min=8 max=100 password)
  +-- MIME allowlist (PDF, DOCX, TXT, MD, HTML) with 50MB file limit
  +-- JDBC parameterized queries -- no string concatenation anywhere
  +-- NoOpKafkaEventPublisher with graceful degradation when Kafka is down

Layer 6 -- Error Handling (Information Disclosure Prevention)
  +-- GlobalExceptionHandler logs server-side stack trace, returns generic message
  +-- Correlation UUID in production error responses, actual detail in dev profile
  +-- Validation errors return field-level detail (safe, user-facing)

Layer 7 -- Data / Persistence
  +-- PostgreSQL with pgvector (HNSW index for embeddings)
  +-- BCrypt-hashed passwords at rest
  +-- [GAP] No column-level encryption or TDE
  +-- [GAP] No audit table for data access
```

---

### B. Threat Model (STRIDE per Component)

#### 1. Spring Boot Application (JwtAuthFilter, SecurityConfig, AuthController)

| Threat | Risk | Current Mitigation | Gap / Recommendation |
|--------|------|--------------------|---------------------|
| Spoofing | Attacker forges JWT | JwtUtil.java line 23-25: HMAC-SHA256 signing with server-side secret | Key rotation not implemented; single static key. No JWKS endpoint. |
| Tampering | Attacker modifies JWT claims in transit | JwtUtil.validateToken() line 117-123 catches JwtException (signature mismatch) | None -- signature validation is correct and uses verifyWith() which prevents alg:none attacks. |
| Repudiation | User denies sending a request | None | Add server-side audit log of all authenticated requests. |
| Information Disclosure | Stack trace leakage in prod | GlobalExceptionHandler.java lines 34-39: returns generic message + correlationId in prod, full detail in dev profile | Correct pattern. |
| DoS | Brute-force password guessing | RateLimitingFilter.java on /api/auth/**. Also, controller returns identical "Invalid username or password" for both missing user and wrong password (AuthController.java lines 40, 45) -- prevents user enumeration. | Rate limiting is per-JVM only. |
| Elevation of Privilege | User accesses admin-only endpoints | SecurityConfig.java line 44: .requestMatchers("/api/documents/**").hasAnyRole("USER","ADMIN"). All other endpoints require auth (line 45). | Only two roles; no fine-grained permissions. MCP endpoints bypass auth checks entirely. |

#### 2. PostgreSQL Database

| Threat | Risk | Current Mitigation | Gap / Recommendation |
|--------|------|--------------------|---------------------|
| Spoofing | Attacker connects as aiagent user | docker-compose.yml line 9: POSTGRES_PASSWORD=aiagent (plaintext). .env.example has "change-me". | Default credentials in docker-compose. Use secrets manager or Docker secrets. |
| Tampering | Attacker modifies data at rest | None | Enable PostgreSQL TDE or use encrypted filesystem. |
| Repudiation | Admin inserts malicious data | None | Enable pg_audit or log all DML. |
| Information Disclosure | SQL injection via untrusted input | UserRepository.java: All queries use parameterized ? placeholders (lines 29-32, 38-39, 43-45). AgentMemoryService.java: All queries parameterized (lines 12-13, 19-20, 26-28, 41, 46, 51-52, 60-65). No string concatenation anywhere. | Strong. |
| Information Disclosure | Database credentials in docker-compose.yml + .env | Environment variables or .env file. | git-secrets or pre-commit hooks to prevent credential commits. The .env currently has a live NVIDIA key and JWT secret -- already leaked in git history per comments. |
| DoS | Connection pool exhaustion | application.yml line 35: HikariCP max 10 connections. | Add connection pool monitoring + alerting. |
| Elevation of Privilege | Weak PostgreSQL role permissions | All operations as single "aiagent" user. | Create read-only role for search, separate role for ingestion. |

#### 3. Kafka (Event Streaming)

| Threat | Risk | Current Mitigation | Gap / Recommendation |
|--------|------|--------------------|---------------------|
| Spoofing | Unauthorized producer publishes to topic | None (PLAINTEXT listener in docker-compose.yml line 28) | Enable SASL/SCRAM or mTLS for Kafka authentication. |
| Tampering | Message modified in transit | KafkaConfig.java line 37: ENABLE_IDEMPOTENCE_CONFIG=true, RETRIES_CONFIG=3 (line 36) | Idempotence prevents duplicates, not tampering. Enable TLS for encryption. |
| Repudiation | Producer denies sending event | None | Enable Kafka audit logs. |
| Information Disclosure | Sensitive data in Kafka topics | DefaultKafkaEventPublisher.java includes message text in events | Consider encrypting sensitive payload fields. |
| DoS | Kafka broker unavailable | NoOpKafkaEventPublisher handles failure gracefully (falls back to no-op). KafkaConfig.java line 35: MAX_BLOCK_MS_CONFIG=2000 prevents producer hanging indefinitely. | Good fault tolerance. |
| Elevation of Privilege | Consumer reads unauthorized topics | None | No ACLs on Kafka topics in docker-compose. |

#### 4. Ollama (Local LLM)

| Threat | Risk | Current Mitigation | Gap / Recommendation |
|--------|------|--------------------|---------------------|
| Spoofing | Attacker calls Ollama API directly | docker-compose.yml exposes port 11435. OllamaService talks to http://ollama:11434 internally. | External port 11435 should be restricted to internal Docker network only. |
| Tampering | Attacker intercepts request to Ollama | HTTP (no TLS) between app and Ollama container | No sensitive data typically, but consider internal TLS. |
| Information Disclosure | Ollama prompt history leaks embeddings | None | Ollama runs locally and clears history on container restart. |
| DoS | Resource exhaustion via large prompts | OllamaService has timeout and max-tokens configured (application.yml lines 95-99). | Adequate for current use. |
| Elevation of Privilege | None significant | N/A | Ollama is a stateless inference server. |

#### 5. MCP Server (McpServerController)

| Threat | Risk | Current Mitigation | Gap / Recommendation |
|--------|------|--------------------|---------------------|
| Spoofing | Attacker connects without valid token | McpServerController.java lines 75-87: validates JWT if present, but accepts connection without it ("lenient for backward compatibility"). Only logs warning (line 85-87). | This is explicitly a known gap -- the comment says "MCP clients SHOULD pass Authorization header." Production hardening must make this REQUIRED not OPTIONAL. |
| Tampering | Attacker modifies JSON-RPC payload | None | Add request body integrity validation. |
| Repudiation | MCP client denies calling a tool | sessionUsers map tracks JWT username. Logging of all MCP calls. | Current logging sufficient; add structured audit log. |
| Information Disclosure | Tool list leaked to unauthenticated clients | None -- handleToolsList() (line 244) returns full tool registry regardless of auth. | Should require authentication before returning tool capabilities. |
| DoS | SSE connection exhaustion | McpServerController.java lines 65-69: max 100 SSE connections with AtomicInteger counter. | Connection limit is per-JVM; add per-IP limits. |
| Elevation of Privilege | Anonymous user calls tools via MCP | None -- MCP handles tool calls without auth check on individual methods. | Add authorization checks to handleToolsCall(). |

---

### C. OWASP Top 10 (2021) Walkthrough

| # | Category | Covered? | Evidence from Codebase | Gaps |
|---|----------|----------|----------------------|------|
| A01 | Broken Access Control | Partial | SecurityConfig.java line 44: ROLE_USER/ROLE_ADMIN on /api/documents/**. .anyRequest().authenticated(). No role checks on MCP endpoints. DocumentController.deleteDocument() line 160 checks owner match before delete (owner-scoped access). | MCP endpoints have no authorization checks. No per-document access control beyond owner check. No API key scoping. Swagger exposed in dev only (OpenApiConfig.java @Profile("dev")). |
| A02 | Cryptographic Failures | Good | JwtUtil.java line 24: HMAC-SHA256 via Keys.hmacShaKeyFor(). BCrypt for passwords (SecurityConfig.java line 54-56). SHA-256 for userAgentHash (JwtUtil.java lines 58-73). | JWT secret is static (no rotation). No TLS at application level (relies on reverse proxy). No encryption for data at rest. JWT expiration is configurable -- ensure not set too long. |
| A03 | Injection | Strong | All JDBC queries use parameterized ? placeholders -- zero string concatenation calls in UserRepository, AgentMemoryService, DocumentIngestionService. @Valid on all request bodies. Message max 4096 chars (ChatRequest.java line 11). SessionId constrained to [a-zA-Z0-9_-] via @Pattern (ChatRequest.java line 14-15). | No SQL injection risk. No NoSQL injection risk (no raw queries to Ollama). |
| A04 | Insecure Design | Partial | Stateless sessions by design. Graceful degradation via NoOpKafkaEventPublisher. Rate limiting on auth endpoints. Token-to-client binding. | No rate limiting on agent/chat or MCP endpoints. No request throttling on file uploads. No security requirements documented for new features. The MCP lenient auth is a deliberate design tradeoff that should be re-evaluated. |
| A05 | Security Misconfiguration | Partial | CORS restricted (line 60 of SecurityConfig.java). GlobalExceptionHandler hides stack traces in prod (line 38). Spring Boot actuator limited to health/info/metrics/prometheus (application.yml lines 64-66). | Default PostgreSQL credentials in docker-compose.yml. Kafka uses PLAINTEXT listener. Verbose errors on validation (field-level detail is intentional and safe). Swagger is dev-only. |
| A06 | Vulnerable & Outdated Components | Good | pom.xml uses Spring Boot 4.1.0, Spring AI 2.0.0, JJWT 0.12.6, Java 25. OWASP Dependency-Check used (per CLAUDE.md -- CVSS >= 8 threshold, suppression files). | Need to verify OWASP DC is in CI pipeline (no evidence in pom.xml). Need Dependabot/Renovate for automated updates. |
| A07 | Identification & Authentication Failures | Good | JWT with 15-min access token, 7-day refresh (JwtUtil.java lines 17-18, 22-25). BCrypt (SecurityConfig.java line 54). Password complexity rules (AuthController.java lines 127-143). Token-to-client binding (JwtAuthFilter.java lines 58-66). Login failure returns generic error (AuthController.java line 40: "Invalid username or password" -- prevents enumeration). | No MFA support. No account lockout (beyond rate limiting). No password expiry policy. Refresh token rotation is implemented (AuthController.java line 109-110: new refresh token issued on every refresh call). |
| A08 | Software & Data Integrity Failures | Partial | Docker image uses fixed tag eclipse-temurin:25.0.3_9-jre-alpine (not 'latest'). JJWT dependency pinned at 0.12.6. | No Docker image signing. No SBOM generation in pipeline. No checksum verification for downloaded base images. Jenkins/GitHub Actions pipeline would need artifact signing. |
| A09 | Security Logging & Monitoring | Partial | Micrometer metrics for agent loop timing, per-tool counters, Prometheus endpoint. JwtAuthFilter logs invalid/expired tokens (lines 47, 53, 64). RateLimitingFilter logs rate limit hits (line 50). GlobalExceptionHandler logs stack traces server-side with correlationId (lines 60-61, 69-70). | No structured logging (JSON format). No centralized SIEM integration. No audit trail for data access (read queries). No alerting thresholds. The logging is sufficient for debugging but not for compliance. |
| A10 | Server-Side Request Forgery | Partial | RestTemplate configured with 5-second connect timeout and 10-second read timeout (AppConfig.java lines 26-28). OllamaService and NvidiaService talk to pre-configured base URLs only. | No URL allowlist validation for any external calls. If a tool made dynamic HTTP requests, SSRF would be a risk. Currently, all outbound URLs are configured via properties, which mitigates this. |

---

### D. Production Hardening Checklist

This checklist takes the local/dev security to production-grade:

**Phase 1: Foundation (Immediate)**

- [ ] **TLS everywhere**: Terminate TLS at the ALB/ingress. Set HSTS header (max-age=31536000; includeSubDomains). Configure redirect from HTTP to HTTPS.
- [ ] **JWT secret rotation**: Implement a key management strategy using Vault, AWS Secrets Manager, or a secrets rotation job. Store JWT_SECRET in secrets manager, not .env committed to git. Implement JWKS endpoint for key rotation without invalidating all tokens.
- [ ] **Database credentials**: Replace default aiagent/aiagent credentials. Use Docker secrets or a secrets manager. Create read-only role for vector search queries. Restrict PostgreSQL to not accept connections from outside the app's network.
- [ ] **Remove committed secrets**: The .env file contains a live NVIDIA_API_KEY and JWT_SECRET that are already in git history. Revoke both, generate new ones, and add .env to .gitignore. Use git-filter-repo or BFG to scrub history. Add a pre-commit hook (e.g., git-secrets) to prevent credential commits.
- [ ] **MCP auth hardening**: Make JWT validation REQUIRED (not lenient) on all MCP endpoints. Remove backward-compatibility fallback. Add tool-level authorization checks.

**Phase 2: Network & Observability (Week 1-2)**

- [ ] **WAF rules**: Deploy AWS WAF / Cloudflare / ModSecurity with OWASP Core Rule Set. Add rate-based rules for /api/auth/** (e.g., 100 requests/5 min per IP). Add SQLi and XSS detection rules before requests reach the app.
- [ ] **Network segmentation**: Put PostgreSQL, Kafka, and Ollama in a private subnet with no public access. App container only. Use security groups/network policies for least-privilege connectivity.
- [ ] **Kafka security**: Enable SASL/SCRAM or mTLS authentication. Enable TLS encryption for broker communication. Set up ACLs on topics. Only the app service account should have WRITE access.
- [ ] **Structured audit logging**: Convert log output to JSON format (LogstashEncoder). Ship logs to a SIEM (Splunk, ELK, Datadog). Include correlationId, userId, sessionId, action, resource, timestamp, and outcome in every audit log.
- [ ] **SIEM alerts for security events**: Configure alerts for: Rate limit exceeded per IP (potential brute force), Expired/invalid token spikes (token scanning), SSE connection limit hit (resource exhaustion), Failed login attempts > 10 per minute.

**Phase 3: Hardening (Week 2-4)**

- [ ] **Rate limiting expansion**: Move RateLimitingFilter to Redis-based sliding window (Lua script or Redisson RRateLimiter). Apply rate limiting to /api/agent/chat, /api/documents/**, and /mcp/** endpoints. Add per-user + per-IP rate limiting (not just per-IP).
- [ ] **Dependency security CI**: Add OWASP Dependency-Check Maven plugin to pom.xml with CI enforcement (fail build on CVSS >= 7). Add Dependabot or Renovate for automated PR creation on vulnerable deps. Generate SBOM (CycloneDX) on each build.
- [ ] **Container security**: Switch to distroless base image (e.g., gcr.io/distroless/java25-debian12). Run container as non-root user. Add HEALTHCHECK to Dockerfile. Sign Docker images with Cosign. Scan images with Trivy or Grype in CI.
- [ ] **Penetration testing scope**: Run initial internal pentest covering: JWT manipulation (alg none, alg confusion, expired tokens), SQL injection boundary testing on sessionId/document queries, File upload path traversal and MIME type bypass, Rate limiting bypass (X-Forwarded-For spoofing), MCP endpoint abuse with various JSON-RPC payloads.

**Phase 4: Compliance & Multi-Tenant (Month 2+)**

- [ ] **Multi-tenant architecture** (if needed): Introduce tenantId claim in JWT. Scope all data queries by tenantId. Implement data isolation at the database level (schema-per-tenant or tenant column). Add dedicated rate limit buckets per tenant.
- [ ] **mTLS for service-to-service**: Enforce mTLS between microservices. Use SPIFFE/SPIRE for workload identity.
- [ ] **Certificate management**: Implement automated certificate renewal via cert-manager or Let's Encrypt. Set up mutual TLS for Kafka and PostgreSQL connections.
- [ ] **Immutable audit store**: Write audit logs to an append-only store (e.g., AWS CloudTrail, immutable S3 bucket, or a dedicated audit database). Ensure logs cannot be modified or deleted by application users.
- [ ] **GDPR/SOC2 compliance readiness**: Add data retention policies. Implement right-to-deletion API. Document data flow map. Add consent management for RAG document storage.

---

### E. Interview Answers

#### Q1: "Walk me through the security architecture of your application."

"You look at the codebase and see a layered, defense-in-depth approach. At the outermost layer, CORS restricts origins to localhost:8082 and localhost:3000, and there is a rate limiting filter restricting auth endpoints to 5 requests per minute per IP. The rate limiter uses a ConcurrentHashMap with a scheduled cleanup task running every 60 seconds to prevent memory leaks from stale entries. I would note that in production, this is single-JVM only and should be moved to a Redis-backed sliding window.

At the authentication layer, the application uses JWT-based stateless authentication with HMAC-SHA256 signing. Access tokens expire in 15 minutes and refresh tokens in 7 days. The JWT includes custom claims for token-to-client binding: the client IP address and a SHA-256 hash of the User-Agent header. This means if a token is stolen, the attacker cannot use it from a different IP or browser, which raises the bar significantly against token replay. The JwtAuthFilter validates the signature, checks expiration explicitly before other validation, and then resolves the client IP by checking X-Forwarded-For for proxy awareness, falling back to getRemoteAddr(). I should mention that the token binding is currently 'warn-only' -- the mismatch is logged but the request is still processed. In production this should be enforced as a hard reject.

Passwords are hashed with BCrypt through Spring Security's PasswordEncoder, and registration enforces a strict complexity policy: minimum 8 characters, with at least one uppercase letter, one lowercase letter, one digit, and one special character. The login endpoint returns a generic 'Invalid username or password' message regardless of whether the username exists, which prevents user enumeration.

For authorization, Role-Based Access Control is applied via Spring Security's request matchers. The /api/documents/ endpoints are restricted to ROLE_USER and ROLE_ADMIN. All other endpoints require authentication except for auth endpoints, health, and thread-info. The MCP server endpoints have a lenient authentication model -- JWT is validated if present but the connection is accepted without it for backward compatibility. This is a known gap I would prioritize for production hardening.

At the input validation layer, all request bodies use Jakarta Bean Validation annotations with specific constraints -- ChatRequest messages are limited to 4KB, session IDs are restricted to alphanumeric characters plus hyphens and underscores via a @Pattern annotation, preventing injection attacks. File uploads have a MIME type allowlist of exactly five types and a 50MB file size limit.

For injection prevention, the entire data access layer uses JDBC parameterized queries -- I verified every Repository and Service class: not a single string concatenation in any SQL statement. This is a strong defense against SQL injection.

The error handling layer prevents information leakage through a GlobalExceptionHandler that logs full stack traces server-side with a correlation UUID, but returns only a generic message plus the correlation ID in production. In the dev profile, the actual error message is included.

Finally, for dependency management, the project uses OWASP Dependency-Check with a CVSS threshold of 8, but this needs to be wired into the CI pipeline explicitly. The project uses pinned dependency versions and a fixed Docker base image tag rather than 'latest'."

#### Q2: "How do you handle JWT token management at scale?"

"This is an area where I can speak to both what the codebase does and what I would add for production scale.

What is already implemented: The application uses JJWT 0.12.6 library with HMAC-SHA256 symmetric signing. The secret is provided via environment variable (JWT_SECRET from .env) and loaded into a SecretKey via Keys.hmacShaKeyFor(). Access tokens have a configurable expiration (default 15 minutes). Refresh tokens have a fixed 7-day expiration. The flow is: login returns both tokens, the client uses the access token for all subsequent requests, and when it expires, calls /api/auth/refresh with the refresh token to get a new pair. On every refresh, both a new access token AND a new refresh token are issued (rotation), which limits the damage if a refresh token is stolen. The token carries custom claims for client binding: clientIp and userAgentHash (SHA-256 of the User-Agent header), which provides a fingerprint that changes if the token is used from a different device. JWTs are validated using verifyWith(key) which ensures algorithmic consistency and prevents alg:none attacks.

What I would add for production scale: First, move from a single static symmetric key to asymmetric RS256 or ES256 keys. This allows the application to validate tokens without holding the private key. You can publish a JWKS endpoint for public keys, enabling key rotation without invalidating active tokens -- when you need to rotate, you add the new key as the primary signer while keeping the old key in the JWKS set for validation until all tokens signed with it expire. Second, introduce a token blacklist using Redis for access tokens when a user explicitly logs out. The blacklist check is a fast EXISTS lookup in Redis before JWT validation. Third, I would add a token refresh limit -- a user should not be able to refresh tokens indefinitely. Track refresh token usage count per user in Redis, and force re-login after N refreshes or N days (whichever comes first). Fourth, for multi-datacenter deployments, the JWKS endpoint solves validation, but you still need consistency for the blacklist -- use Redis with cross-region replication. Finally, I would implement a token revocation webhook or API endpoint for admin force-logout scenarios, which writes to the blacklist and is consumed by all app instances."

#### Q3: "How would you design auth for a multi-tenant SaaS?"

"There are three common patterns and I would match the choice to the tenant isolation requirements:

Pattern 1 -- Tenant in JWT (shared schema, most common for B2B SaaS): Each JWT includes a tenantId claim. All data access is scoped by tenantId via a TenantContext stored in a request-scoped bean or ThreadLocal. The JwtAuthFilter extracts the tenantId from the token and sets it in the security context. Every repository query includes AND tenant_id = ?. This is straightforward to add to this codebase because all queries are already parameterized -- you would add a tenantId column to users, document_metadata, and conversation_memory tables and filter by it. The rate limiter would also be keyed by (tenantId, IP) rather than just IP.

Pattern 2 -- Schema-per-tenant (strong isolation): Each tenant gets their own PostgreSQL schema. The tenantId from the JWT determines which connection or schema to use. This requires dynamic multi-tenancy in HikariCP (one pool per tenant or a routing datasource using Spring's AbstractRoutingDataSource). This pattern provides the strongest data isolation but adds operational complexity for migrations.

Pattern 3 -- Database-per-tenant (maximum isolation): Each tenant gets their own database. This is used for enterprise customers with compliance requirements. The routing happens at the datasource level. This is the most expensive to operate.

For the current codebase, Pattern 1 would require: adding tenantId to the JWT claims in JwtUtil.generateToken(), adding a TenantContext filter that reads tenantId from the JWT after JwtAuthFilter processes it, adding tenantId column to users, document_metadata, conversation_memory, and vector store metadata, updating all repository queries to filter by tenantId, moving the ConcurrentHashMap rate limiter to Redis with (tenantId, IP) composite keys, adding a setup endpoint that provisions tenant configuration on first login, and ensuring the vector store supports tenant-isolated searches by adding tenantId to document metadata and filtering on it in the similarity search."

#### Q4: "How do you prevent API abuse?"

"Speaking to what is already in this codebase: There is a dedicated RateLimitingFilter that limits /api/auth/ endpoints to 5 requests per minute per IP. It uses a ConcurrentHashMap with per-minute sliding windows tracked via an AtomicInteger counter. The cleanup runs every 60 seconds to evict expired entries. Additionally, the token-to-client binding in the JWT prevents stolen token replay from different IPs or user agents. Short-lived access tokens (15 min) limit the window of abuse if a token is compromised. The global exception handler prevents information leakage that could aid attackers in crafting exploits.

However, there are several gaps:

First, the rate limiting is only on auth endpoints -- the /api/agent/chat, /api/documents/upload, and /mcp/ endpoints have no rate limits. An attacker could flood the LLM with requests and drive up inference costs exhausting GPU resources. I would add rate limiting across all endpoints with two tiers: per-IP limits and per-user limits after authentication. The per-user limit requires moving from the current filter-based approach to one that can distinguish authenticated users, which means the rate limiter must run after JwtAuthFilter, not before.

Second, the current rate limiter is single-JVM. In a multi-instance deployment, each node has its own ConcurrentHashMap, so an attacker can send 5 requests to each of 10 instances and get 50 attempts. A production system needs a distributed rate limiter using Redis with Lua scripts for atomic sliding window counters.

Third, there is no request size limiting beyond the file upload 50MB limit. The multipart config allows up to 1024MB (application.yml lines 20-21). I would reduce this to 50MB to match DocumentController's own limit, keeping both in sync. The 1024MB Spring-level limit bypasses the 50MB application-level check if an attacker sends a multipart request directly.

Fourth, the CORS configuration allows localhost:8082 and localhost:3000 with credentials. In production, these would be replaced with the actual production domain. I would also add per-IP connection limits to the MCP SSE endpoint -- currently only total connections are limited to 100. An attacker could open 100 SSE connections from one IP and exhaust the limit for all other users.

Fifth, I would add a payload size limiter for the agent/chat endpoint to reject requests with message bodies exceeding 4KB, enforced at the filter level before reaching the controller. While @Valid does enforce this, the request body is already deserialized by then. A Content-Length check in a filter would reject oversize requests earlier."

#### Q5: "What is your approach to CVE management?"

"The current codebase has a good foundation but incomplete automation. Here is my full approach:

Current state: The project uses Spring Boot 4.1.0, Spring AI 2.0.0, JJWT 0.12.6, and Tika for document processing. All dependency versions are pinned in pom.xml, which is good practice. The CLAUDE.md mentions OWASP Dependency-Check with a CVSS threshold of 8 and suppression files, but I would need to verify if the Maven plugin is actually configured in the POM -- I reviewed the pom.xml and did not see the plugin there. It may be run manually or in CI separately.

My layered CVE strategy:

Layer 1 -- Automated scanning: Add OWASP Dependency-Check Maven Plugin to pom.xml with a CVSS threshold of 7 (not 8 -- 8 is too high, you miss critical vulnerabilities with CVSS 7-7.9). Configure suppression files for confirmed false positives with documented justifications. Add Dependabot or Renovate for automatic PR creation when vulnerable dependencies are detected.

Layer 2 -- Container scanning: Use Trivy or Grype in CI to scan the Docker image. These tools catch OS-level vulnerabilities and transitive dependencies that OWASP DC might miss. Add scanning to the Docker build stage. Fail the build for critical or high severity.

Layer 3 -- SBOM generation: Integrate CycloneDX Maven plugin to generate a Software Bill of Materials on every build. The SBOM is uploaded to a repository for tracking. This is essential for incident response -- when Log4Shell hits, you query your SBOM store to know exactly which builds are affected within minutes.

Layer 4 -- Policy enforcement: Define a vulnerability response SLA: Critical CVEs patched within 48 hours, High within 7 days, Medium within 30 days. Track exceptions with risk acceptance documentation and an expiry date.

Layer 5 -- Runtime monitoring: Use a vulnerability scanner like Snyk Monitor or Trivy in continuous monitoring mode on running containers to detect newly discovered CVEs in production environments.

Layer 6 -- Update cadence: Keep Spring Boot updated to the latest patch version within the same major.minor line. Schedule quarterly full dependency updates with integration testing. Pin all dependency versions and use Maven's versions plugin to check for available updates.

For the specific stack: JJWT 0.12.6 is the latest and has no known CVEs. Tika document reader should be closely monitored since it parses untrusted documents. Tika has had multiple deserialization and XXE vulnerabilities historically. The MIME allowlist in DocumentController helps but does not protect against malformed files within allowed types."

#### Q6: "Design a secure file upload system."

"The current codebase has a reasonable file upload system for an AI agent document ingestion pipeline. Here is my analysis of what exists and what I would add for a production system.

What exists (from DocumentController.java and related code):
- MIME type allowlist (PDF, DOCX, TXT, MD, HTML) -- line 26-31
- 50MB file size limit -- line 23
- Owner-scoped access control (you can only list/delete your own documents) -- lines 152-165 of DocumentIngestionService.java
- Content-type detection via filename extension fallback -- lines 419-427 of McpServerController.java
- Tika document reader for safe content extraction -- line 67 of DocumentIngestionService.java
- Vector embedding chunks stored in PgVectorStore with metadata for isolation

What I would add for production security:

First, file validation: Validate the file content matches the claimed MIME type using Apache Tika's content detection, not just the Content-Type header. The current implementation relies on the client's Content-Type header, which is trivially spoofed. Run ClamAV scan on all uploaded files before processing. Implement a file extension allowlist to match the MIME type allowlist.

Second, path traversal prevention: The filename from MultipartFile.getOriginalFilename() should be sanitized to remove path separators, null bytes, and sequences like ../ before being used in any file system operation. Currently the code does not write the file to disk (it processes it in-memory via Tika), so path traversal is not immediately a risk, but if you add file storage, this becomes critical.

Third, rate limiting on uploads: The upload endpoint currently has no rate limit. An attacker could upload dozens of large files to exhaust disk or vector store capacity. Add per-user upload limits (e.g., 10 uploads per hour, 1GB total per user).

Fourth, vertical staging: Store files in temporary staging storage before processing. Move to permanent storage only after virus scan and content validation pass. Add a cleanup job for failed uploads.

Fifth, vector store cleanup: When a document is deleted, the database metadata is removed but the vector store chunks remain. The code explicitly acknowledges this as a gap in a log warning (DocumentIngestionService.java lines 163-164). Fixing this requires tracking the vector store document IDs in the document_metadata table and deleting them from PgVectorStore on document deletion.

Sixth, store file encryption: If you store files on disk (not just embeddings), encrypt them at rest using AES-256-GCM with a per-file key, storing the key separately. This protects against storage-level breaches.

Seventh, add a processing pipeline with status tracking: uploaded, scanning, processing, ready, failed. This allows users to see the status of their uploads and enables automated cleanup of failed uploads.

Eighth, add per-user storage quotas enforced at the upload endpoint, and a cleanup endpoint or lifecycle policy for old documents.

Ninth, ensure the Tika reader is configured safely by default -- disable external entity expansion in XML parsing to prevent XXE attacks. Tika by default has some protections, but this should be verified.

Tenth, consider adding asynchronous processing for large files to avoid tying up web server threads. The current synchronous ingest could block the Tomcat thread for seconds on a large PDF. Use a @Async method or a message queue for processing."





---

## Section 5: 🎯 Architecture Decision Records (ADRs)

# SECTION 5: Practice with ADRs (Architecture Decision Records)

## Background: The ADR Format

Architecture Decision Records were popularized by Michael Nygard and ThoughtWorks as a lightweight method for capturing architectural decisions within a project. An ADR is a short document (typically one page or less per decision) that records:

- **Title** (ADR-NNN): A descriptive name for the decision
- **Status**: Proposed, Accepted, Deprecated, or Superseded
- **Context**: What forces, constraints, or problems are at play
- **Decision**: The architecture decision itself, including the "why" behind it
- **Consequences**: Trade-offs, both positive and negative
- **Compliance**: How to enforce or verify the decision is being followed
- **Notes**: Optional, for references, links, or related ADRs

The ADR format is powerful because it captures **why** a decision was made -- not just **what** was decided. This is critical for interviews because it demonstrates you think beyond technical implementation to consider trade-offs, team alignment, and long-term maintainability.

---

## How to Use These ADRs in Your Interview

For each ADR below, we provide:
1. The formal ADR document itself (interview-ready)
2. A "How to Present" section that tailors the message to different stakeholders

When asked "describe a key architectural decision you made," these ADRs give you a structured framework. You should:

- **For architects**: Emphasize patterns, trade-offs, and long-term maintainability
- **For security teams**: Emphasize threat models, audit trails, and defense in depth
- **For business stakeholders**: Translate technical decisions into business value (cost, speed, reliability)
- **For engineers**: Emphasize concrete implementation patterns and testing strategies

---

## ADR-001: Multi-Provider AI Architecture

**Status**: Accepted

### Context

The system depends on LLM inference for its ReAct agent loop. Initially, a single AI provider (Ollama running locally) handled all requests. This created several problems:
- **Single point of failure**: If Ollama is down or unresponsive, the entire application is unavailable
- **Latency variability**: Local inference is fast for small models but slow for larger ones; cloud APIs vary by geography and load
- **Cost optimization**: Local inference is free (uses local GPU/CPU), cloud inference costs per-token; no mechanism to choose based on workload
- **No fallback**: A transient error from the provider (network blip, model loading) fails the entire request

### Decision

Implement a **chain-of-responsibility pattern** with a configurable primary provider and automatic fallback. The `AiProviderChain` class acts as a routing facade that:

1. Reads `app.ai.provider` configuration (default: `ollama`) to determine the primary provider
2. Calls `resolveProvider()` which first checks the primary provider's `isAvailable()`
3. If the primary is unavailable, iterates all registered `AiService` implementations and returns the first available one
4. Delegates the `chat()` call to the resolved provider
5. Accumulates token usage counters (`AtomicInteger`) from the underlying provider

**Implementation details:**

- `AiService` interface defines `chat()`, `isAvailable()`, `getLastPromptTokens()`, `getLastCompletionTokens()`
- Both `OllamaService` and `NvidiaService` implement `AiService` and are plain `@Service` beans
- Both services use `Retry.backoff(3, Duration.ofSeconds(1))` with WARN-level logging on retry
- `OllamaService` additionally caps with `.maxBackoff(Duration.ofSeconds(10))`
- `AiProviderChain` is `@Primary` so it is injected wherever `AiService` is required
- The chain uses `findProviderBySimpleName()` which matches class names case-insensitively

### Consequences

**Positive:**

- Zero downtime during provider outages -- automatic failover with no manual intervention
- Cost flexibility -- use free local Ollama for development/testing, NVIDIA cloud for production inference
- Transparency -- each service independently reports availability, the chain logs which provider is selected per request
- Extensibility -- adding a third provider requires only implementing `AiService` and registering a `@Service` bean

**Negative:**

- Added latency from availability checks -- each `isAvailable()` call issues a health-check HTTP request with a 5-second timeout
- No intelligent routing -- the chain does not consider latency, cost, or model capability when falling back; it picks the first available provider
- Token tracking is aggregated across providers -- you cannot attribute usage to a specific provider from the totals

### Compliance

- All AI interactions must go through `AiProviderChain` (enforced by `@Primary` annotation)
- Every `AiService` implementation must handle both success and failure paths (tested via `AiProviderChainTest`)
- CI pipeline validates that `ChatRequest` test passes with both providers mocked as unavailable

### How to Present This to Stakeholders

**To architects:** Emphasize the chain-of-responsibility pattern, the `AiService` interface contract, and how the design allows adding new providers (OpenAI, Anthropic, etc.) without modifying existing code -- the Open/Closed Principle in action.

**To business stakeholders:** Frame this as "our system has built-in redundancy -- if our primary AI provider goes down, we automatically fail over to a backup with no downtime." Highlight the cost optimization: use free local inference for dev/test, paid cloud inference only for production.

**To engineers:** Walk through the `resolveProvider()` two-phase selection logic, the `Retry.backoff` configuration, and the `AiProviderChainTest` that validates fallback behavior. Show how `OllamaService` and `NvidiaService` differ in their API calls, auth handling, and timeout strategies.


### Consequences (continued)

**Notes:**

- The default provider is `ollama` in application.yml (`AI_PROVIDER` env var), but the `@Value` annotation defaults to `nvidia` as a fallback if the property is missing entirely
- The `AiProviderChainTest` covers: primary delegation, fallback when primary is unavailable, graceful handling when all providers are down, and correct provider ordering
- Related ADRs: ADR-005 (Graceful Degradation) shares the fail-fast-and-fallback philosophy

---

## ADR-002: PostgreSQL + pgvector over Redis / ChromaDB

**Status**: Accepted

### Context

The system needed vector storage for RAG (Retrieval-Augmented Generation). The project went through three storage iterations:

1. **Redis** (initial): Used for conversation memory and metadata storage, but lacks native vector search capabilities. Embedding similarity search required loading all vectors into application memory and computing cosine similarity manually -- O(n) per query, unscalable beyond a few hundred documents.

2. **ChromaDB** (intermediate): A purpose-built vector database, but Spring AI 1.0.8's ChromaDB client attempted HTTP/2/WebSocket protocol upgrades. ChromaDB 0.4.x/0.5.x Docker images only support HTTP/1.1, causing "Unsupported upgrade request" errors and HTTP 500s on upload. This was a framework-level protocol mismatch that could not be fixed in application code.

3. **SimpleVectorStore** (intermediate fallback): Spring AI's in-memory vector store replaced ChromaDB temporarily. Data was lost on restart, making it unsuitable for production.

The system needed a persistent, production-grade vector store that integrated cleanly with the existing Spring Boot + PostgreSQL stack.

### Decision

Adopt **PostgreSQL + pgvector** as the single persistence layer, replacing Redis, ChromaDB, and SimpleVectorStore in one consolidation.

**Implementation details:**

- `PgVectorStoreConfig` creates a `PgVectorStore` bean with:
  - 768-dimensional embeddings (matching Ollama's `nomic-embed-text` model)
  - `PgDistanceType.COSINE_DISTANCE` for similarity measurement
  - `PgIndexType.HNSW` for approximate nearest-neighbor search (logarithmic query time)
  - `initializeSchema(true)` for automatic table and index creation
- Schema auto-created by Spring AI: `CREATE TABLE IF NOT EXISTS vector_store (id UUID PRIMARY KEY, content TEXT, metadata JSONB, embedding vector(768))` plus `CREATE INDEX IF NOT EXISTS ... USING hnsw (embedding vector_cosine_ops)`
- Application-level tables (`users`, `conversation_memory`, `document_metadata`) managed via `schema.sql` with `spring.sql.init.mode: always`
- Document ingestion pipeline: Tika parsing -> TokenTextSplitter -> embedding -> PgVectorStore -> metadata in PostgreSQL
- Search pipeline: cosine similarity query -> optional keyword reranking (see ADR-008)

### Consequences

**Positive:**

- **Simplified operations**: One database instead of three (Redis + ChromaDB + PG became just PostgreSQL). One connection pool, one backup strategy, one monitoring dashboard.
- **pgvector maturity**: pgvector is a well-established PostgreSQL extension (since 2021) with active maintenance, HNSW index support since pgvector 0.5.0, and production use at scale.
- **ACID compliance**: Document metadata and vector embeddings live in the same transactional database, eliminating the consistency challenges of synchronizing across stores.
- **Zero additional infrastructure**: Teams already running PostgreSQL can add vector search without standing up a new database service.

**Negative:**

- **Less specialized than Pinecone/Weaviate**: Purpose-built vector databases offer features like hybrid search, multi-tenancy, filtered ANN, and higher-dimensional索引 out of the box. pgvector's HNSW implementation is newer and may be less optimized.
- **Fixed 768 dimensions**: Changing the embedding model would require reindexing -- pgvector cannot mix dimensions in the same column.
- **No automatic chunk cleanup**: When a document is deleted, the vector store chunks for that document are not automatically removed (noted as a known limitation in `DocumentIngestionService`).

### Compliance

- All vector operations must go through `PgVectorStore` (enforced by injecting the `VectorStore` interface)
- The application startup automatically verifies the pgvector extension is available via `initializeSchema`
- CI runs against a PostgreSQL test container with pgvector to validate integration

### How to Present This to Stakeholders

**To architects:** Emphasize the consolidation story -- moving from three disparate stores (Redis for memory, ChromaDB for vectors, PG for metadata) to a single PostgreSQL instance. Highlight the trade-off: pgvector is less specialized than Pinecone for pure vector search, but the operational simplicity of one database wins for a team that doesn't need billion-scale vector search.

**To business stakeholders:** Frame as "we reduced our infrastructure footprint from 3 databases to 1, cutting hosting costs and operational complexity. Your data is backed up in one place with no synchronization concerns." Mention that document deletion has a known vector cleanup gap being tracked.

**To engineers:** Walk through the pipeline: Tika reads PDF/DOCX -> TokenTextSplitter chunks at 500 tokens -> nomic-embed-text generates 768-dim vectors -> PgVectorStore stores with HNSW index -> cosine similarity search. Show the `DocumentIngestionService.search()` method and the `rerank()` keyword boost.


---

## ADR-003: Interface-Driven Tool Architecture

**Status**: Accepted

### Context

The original agent had tools registered as hardcoded switch statements in both the `McpServerController` (for MCP tool dispatch) and the agent's system prompt (for LLM tool descriptions). Adding a new tool required modifying multiple files: the tool implementation class, the MCP controller's switch statement, the agent controller, and the system prompt template. This violated the Open/Closed Principle and made the codebase brittle.

Additionally, the system needed:
- Case-insensitive tool lookup (LLMs sometimes capitalize tool names inconsistently)
- Per-request tool filtering (clients should be able to restrict which tools the agent can use)
- A unified way for both the REST API and MCP endpoint to discover available tools
- Tool name consistency (magic strings like `"weather"` scattered across files)

### Decision

Adopt a **strategy pattern combined with a registry pattern**, driven by interfaces and auto-discovery via Spring dependency injection.

**Architecture:**

```
Tool (interface)
  ^       ^       ^       ^       ^
  |       |       |       |       |
Weather  News  Calculate  KB    RAG   (all @Component)

ToolRegistry (interface)
  ^
  |
DefaultToolRegistry (@Component) -- auto-discovers via List<Tool> injection

Consumed by:
  - ReActAgent (LLM loop)
  - McpServerController (MCP JSON-RPC)
  - AgentController (REST API)
  - PromptBuilder (system prompt generation)
```

**Implementation details:**

- `Tool` interface: `getName()`, `getDescription()`, `execute(String)`, `getParameterSchema()`
- `ToolRegistry` interface: `getTool()`, `getAllTools()`, `getToolDescriptions(Set)`, `getToolNames(Set)`, `isToolEnabled()`
- `DefaultToolRegistry`: Spring injects all `Tool` beans into the constructor via `List<Tool>`, stores them in a `Map<String, Tool>` keyed by lowercase name
- `ToolNames.java`: String constants (`WEATHER`, `NEWS`, `CALCULATOR`, `DATABASE`, `RAG_SEARCH`) eliminating magic strings
- MCP server's `handleToolsList()` iterates `toolRegistry.getAllTools()` dynamically -- no more hardcoded switch statements
- Per-request filtering: `ChatRequest` accepts `List<String> toolsEnabled`; the agent passes it through `ReActAgent.run()`, which restricts both the system prompt and executable tools
- Micrometer metrics: `DefaultToolRegistry` tracks `tool.registry.lookups` counter; `ReActAgent` tracks `agent.tool.calls` counter and per-tool `agent.tool.duration` timer

### Consequences

**Positive:**

- **Zero-registration extensibility**: Creating a new `@Component` implementing `Tool` automatically registers it everywhere -- no manual wiring needed
- **Testable in isolation**: Each tool has its own unit test (e.g., `CalculatorToolTest` with 14 test cases)
- **MCP/REST API parity**: Both interfaces discover tools from the same registry, guaranteeing consistent behavior
- **Constants over magic strings**: `ToolNames.WEATHER` replaces `"weather"` everywhere -- compile-safe, refactorable

**Negative:**

- **No dynamic tool loading**: Tools are discovered at startup only. Adding a tool at runtime requires an application restart (acceptable for this use case)
- **Single parameter convention**: `Tool.execute(String)` takes a raw string input; tools with multiple parameters must parse them from the string or override `getParameterSchema()` for structured input
- **No tool dependencies**: Tools cannot depend on other tools directly (they can depend on shared services via constructor injection)

### Compliance

- Every tool must be annotated `@Component` and implement `Tool` (enforced by `DefaultToolRegistry` auto-discovery)
- Tool descriptions must be updated when tool behavior changes (enforced by `ToolRegistryTest` which validates all registered tools)
- The `ToolNames` constants file must be updated when a new tool is added (convention, not enforced)

### How to Present This to Stakeholders

**To architects:** Emphasize the hexagonal architecture / ports-and-adapters pattern: `ToolRegistry` is a port (interface), `DefaultToolRegistry` and each tool are adapters. The system is decoupled from any specific tool implementation. Show how the MCP and REST consumers both depend on the interface, not concrete implementations.

**To business stakeholders:** Frame as "we can add new capabilities to the AI agent by writing a single class -- no configuration changes, no deployment pipeline changes. Adding a stock price tool, a calendar tool, or a CRM lookup tool is a matter of writing one file and testing it."

**To engineers:** Walk through the flow: `DefaultToolRegistry` constructor receives `List<Tool>` from Spring -> stores in lowercase map -> `ReActAgent.run()` calls `buildSystemPrompt()` which calls `getToolDescriptions(enabledTools)` -> LLM sees only enabled tools -> when LLM outputs `Action: weather`, the agent calls `toolRegistry.getTool("weather")` -> tool executes. Show `CalculatorTool` as a clean example of the pattern.

---

## ADR-004: JWT Token Binding (IP + User-Agent)

**Status**: Accepted

### Context

The system uses JWT for stateless authentication with 15-minute access tokens and 7-day refresh tokens. JWTs are inherently vulnerable to theft: if an access token is intercepted (via XSS, compromised network, or browser extension), the attacker can use it from any location until it expires. Short expiration mitigates this partially -- a 15-minute window is still enough for significant damage, especially for automated attacks.

Standard token binding would reject requests from mismatched clients entirely, but this creates problems for legitimate users whose IP or user-agent changes (mobile users switching between WiFi and cellular, VPN users, browser updates).

### Decision

Embed the **client IP** and a **SHA-256 hash of the User-Agent** as JWT claims at token generation time, with a **warn-only (soft) enforcement** policy.

**Implementation details:**

- `JwtUtil.generateToken(username, clientIp, userAgent)` adds two claims:
  - `clientIp`: The raw IP address from `X-Forwarded-For` or `request.getRemoteAddr()`
  - `userAgentHash`: SHA-256 hex digest of the User-Agent string (empty if null/blank -- using SHA-256 instead of raw UA to limit token size)
- `AuthController` extracts `X-Forwarded-For` and `User-Agent` headers from the login/register request and passes them to `generateToken()`
- `JwtAuthFilter.doFilterInternal()`:
  1. Checks token expiration explicitly (logs warning if expired, continues chain as anonymous)
  2. Validates the HMAC signature (logs warning if invalid, continues as anonymous)
  3. **Soft IP binding check**: Compares the `clientIp` claim to the resolved request IP. On mismatch, logs at WARN level but does NOT reject the request
  4. The `userAgentHash` claim is stored for potential offline audit but not checked at filter time
- The filter never returns 401 -- it defers authorization to Spring Security's `FilterSecurityInterceptor`
- Refresh tokens are intentionally kept simple (no IP/UA binding) to reduce friction during token refresh

### Consequences

**Positive:**

- **Theft detection without false positives**: Legitimate IP changes (mobile, VPN) generate a log entry for investigation but don't block the user
- **Audit trail**: Security teams can scan logs for `Client IP mismatch` patterns to detect token sharing or theft
- **Minimal token size impact**: SHA-256 hash (64 hex chars) is far smaller than storing the raw User-Agent string
- **Backward compatible**: Tokens without `clientIp` or `userAgentHash` claims work normally (empty check at filter time)

**Negative:**

- **Not a true security boundary**: Warn-only enforcement means a stolen token is still usable -- the warning is for post-hoc detection, not prevention
- **IP resolution complexity**: Behind load balancers, `X-Forwarded-For` may contain multiple IPs or be spoofed; the filter takes the first IP, which may not be the actual client
- **No centralized alerting**: The WARN log entry is passive; production deployments need log aggregation and alerting rules to action on mismatches

### Compliance

- Every authenticated request passes through `JwtAuthFilter` (enforced by `SecurityConfig` filter chain ordering)
- Token generation must include `clientIp` and `userAgent` when available (enforced in `AuthController.register()` and `AuthController.login()`)
- The `SecurityConfig` marks all `/api/auth/` endpoints as `permitAll()` -- authentication is optional for auth endpoints themselves

### How to Present This to Stakeholders

**To security teams:** Emphasize the threat model and why soft binding was chosen. A hard-reject approach would lock out legitimate mobile/VPN users. The warn-only approach detects token theft without degrading UX -- pair it with log monitoring and alerting for effective detection. Explain the SHA-256 hashing of User-Agent: the raw UA string can be hundreds of characters (containing browser fingerprinting data), so we hash it to limit token size while still enabling mismatch detection.

**To architects:** Frame this as a defense-in-depth layer. JWT expiration (15 min) + refresh token rotation (7 days) + IP/UA binding (soft) = three layers of token security. The soft-binding approach is a conscious trade-off: security teams get audit data, users get uninterrupted service. If hard binding is needed later, the claim data is already in the token.

**To business stakeholders:** "We added an extra security layer that detects if someone steals a login token and uses it from a different device or location." Emphasize that this doesn't break the user experience (the problem with hard binding on mobile apps).

**To engineers:** Walk through the filter chain flow: `RateLimitingFilter` (rate limit auth endpoints) -> `JwtAuthFilter` (validate token, check IP) -> `UsernamePasswordAuthenticationFilter` (not used, stateless). Show the `resolveClientIp()` method that checks `X-Forwarded-For` first, then `getRemoteAddr()`. Show `hashUserAgent()` using `MessageDigest.getInstance("SHA-256")`.


---

## ADR-005: Graceful Degradation on External Dependencies

**Status**: Accepted

### Context

The system depends on four external services: Kafka (event streaming), Ollama (local LLM), NVIDIA API (cloud LLM), and PostgreSQL (persistence + vector store). Any of these could become unavailable due to network issues, service crashes, resource exhaustion, or maintenance windows. The original system had no fallback behavior: if Kafka was down, the application hung on `send()` indefinitely; if the primary LLM was down, the entire agent stopped working.

The requirement was clear: the system must remain operational (possibly with degraded functionality) when any single dependency is unavailable, without crashing, hanging, or leaking resources.

### Decision

Apply a **fail-fast and degrade-gracefully** pattern to each dependency, with timeouts, fallback implementations, and clear WARN-level logging throughout.

**Dependency-specific strategies:**

| Dependency | Fail-Fast Mechanism | Graceful Degradation |
|---|---|---|
| **Kafka** | `max.block.ms=2000`, `request.timeout.ms=2000`, `retries=3`, `@ConditionalOnProperty` | `@Primary NoOpKafkaEventPublisher` silently discards events with DEBUG log when Kafka is absent |
| **Ollama** | `Retry.backoff(3, 1s).maxBackoff(10s)` | Chain falls back to NVIDIA (see ADR-001); if all providers fail, returns error string |
| **NVIDIA API** | `Retry.backoff(3, 1s)` with blank key check | Chain falls back to Ollama; if API key missing, `isAvailable()` returns false immediately |
| **PostgreSQL** | Connection pool with 5s connect / 10s read timeouts | Application fails to start if database is down on initial connection (intentional -- no useful work without persistence) |
| **Rate Limiter** | `@Scheduled(fixedRate=60s)` cleanup evicts stale entries | Memory-safe -- `ConcurrentHashMap` can grow unbounded without cleanup |

**Implementation highlights:**

- `KafkaConfig` uses `@ConditionalOnProperty("spring.kafka.bootstrap-servers")` -- if no Kafka broker is configured, no Kafka beans are ever created
- `DefaultKafkaEventPublisher` (real publisher) has the same `@ConditionalOnProperty` -- it is never instantiated without a broker
- `NoOpKafkaEventPublisher` is `@Service @Primary` -- it is always present and takes precedence when the real publisher is absent
- `AppConfig` sets 5s connect / 10s read timeouts on `RestTemplate`, and 10s connect / 120s read on `WebClient` (120s for long LLM inference)
- `RateLimitingFilter` only tracks `/api/auth/` paths -- non-auth endpoints are not rate-limited, avoiding unnecessary overhead
- Both LLM services wrap their HTTP calls in `.timeout()` and `.onErrorResume()` to return error messages instead of throwing exceptions

### Consequences

**Positive:**

- **No single point of failure**: Any single dependency can fail without taking down the application
- **Development experience**: Running without Kafka is the default -- no infrastructure needed for local development
- **Clear operational signals**: `WARN` logs at every degradation point tell operators exactly what is degraded
- **Predictable failure behavior**: Every `catch` block produces a specific user-facing message or falls back to an alternative

**Negative:**

- **Silent data loss**: When Kafka is unavailable, events are silently discarded at DEBUG level -- operators may not notice missing events immediately
- **Degraded mode detection**: Clients have no API to discover which features are degraded (e.g., "Kafka is down, agent events are not being published")
- **Increased complexity**: The `@ConditionalOnProperty` + `@Primary` pattern for Kafka is elegant but requires understanding Spring's bean resolution priority
- **Non-degradable path**: If PostgreSQL is unavailable, the application cannot start -- all persistence goes through JDBC, and there is no in-memory fallback for the user repository

### Compliance

- All external service calls must have explicit timeouts (enforced via code review)
- All `catch` blocks must produce a user-facing fallback, not just a log message (enforced by unit tests checking error paths)
- The `/api/health` endpoint reports overall system health, including which LLM provider is active
- All 181 tests pass with mocked dependencies, proving graceful degradation is testable

### How to Present This to Stakeholders

**To architects:** Emphasize the fail-fast philosophy: "fail fast with timeouts, degrade gracefully, log clearly." Contrast this with fail-slow behavior (Kafka's default 60-second `max.block.ms` leading to thread pool exhaustion) or fail-noisy behavior (throwing exceptions up to the user). The `@ConditionalOnProperty` + `@Primary` pattern for Kafka is a particularly clean example of Spring's conditional bean loading.

**To business stakeholders:** Frame as "the system is resilient to failures of any one component. If the event logging system is down, the agent still works -- you just lose analytics until it comes back. If the cloud AI provider is down, we fall back to local AI. We designed for availability first."

**To engineers:** Walk through each dependency's failure scenario. Show the Kafka chain: `@ConditionalOnProperty` on `KafkaConfig` -> if no `spring.kafka.bootstrap-servers`, neither `KafkaConfig` nor `DefaultKafkaEventPublisher` are created -> `NoOpKafkaEventPublisher` (`@Primary`) handles all calls. Show `max.block.ms=2000` in `KafkaConfig` and explain why it prevents thread pool starvation. Demonstrate the `AiProviderChain` fallback with both providers unavailable.


---

## ADR-006: ReAct Agent Decomposition

**Status**: Accepted

### Context

The original `ReActAgent` class was a monolithic ~350-line file that handled prompt building, LLM interaction, state management, and tool execution in a single method. The agent state was tracked using three parallel `List<String>` fields (`thoughtHistory`, `actionsTaken`, `observations`) that had to be kept synchronized manually -- adding a step required adding to all three lists at the same index, which was fragile and error-prone.

The class had no separate `PromptBuilder` -- system prompts were hardcoded strings embedded in the agent logic. The system prompt template was not externalized, making prompt changes require code changes and recompilation.

Testing was difficult because the agent was tightly coupled to all its dependencies and prompt construction logic could not be verified independently.

### Decision

Decompose the monolithic agent into focused, single-responsibility classes:

1. **Extract `PromptBuilder`**: Responsible for all prompt construction -- `buildSystemPrompt()`, `buildContext()`, `buildIterationPrompt()`
2. **Introduce `record Step`**: Replace three parallel lists with `List<Step>` where each `Step(String thought, String action, String observation)` is a cohesive unit
3. **Externalize the system prompt**: Load from `classpath:prompts/system-prompt.txt` with `{tool_descriptions}` placeholder, with a hardcoded fallback if the file is unavailable
4. **Introduce `ToolNames` constants**: Eliminate magic strings by centralizing tool names in a constants class
5. **Add context window management**: `AgentState.estimatedTokenCount()` and `truncateStepsToFit()` to prevent token overflow

**Before (monolithic):**

```java
// ReActAgent.java had all of this inline:
List<String> thoughtHistory = new ArrayList<>();
List<String> actionsTaken = new ArrayList<>();
List<String> observations = new ArrayList<>();
// ... building prompt strings with string concatenation throughout ...
// ... tool name strings like "weather", "news", "rag_search" hardcoded ...
```

**After (decomposed):**

```java
// AgentState.java
public record Step(String thought, String action, String observation) {}
private List<Step> steps = new ArrayList<>(); // single list, single source of truth

// PromptBuilder.java
public String buildSystemPrompt(Set<String> enabledTools) { ... }
public String buildContext(String sessionId, String userMessage) { ... }
public String buildIterationPrompt(AgentState state, String context) { ... }

// ToolNames.java
public static final String WEATHER = "weather";
public static final String RAG_SEARCH = "rag_search";

// ReActAgent.java -- orchestrates, no longer builds prompts or manages parallel state
private final PromptBuilder promptBuilder = new PromptBuilder(toolRegistry, memoryService);
```

### Consequences

**Positive:**

- **Testable**: `PromptBuilder` can be unit-tested independently; `AgentState` has 9 dedicated tests covering state transitions, formatted history, and iteration counting
- **Maintainable**: Each class has a single responsibility -- `PromptBuilder` handles prompts, `AgentState` handles state, `ReActAgent` handles orchestration
- **Externalized prompts**: The system prompt can be edited without recompiling the application (resource file is reloaded on restart)
- **Immutable steps**: `Step` is a Java `record` -- once created, its fields cannot be mutated accidentally. `addAction()` and `addObservation()` create new `Step` instances rather than modifying existing ones
- **Backward compatible**: `AgentState` retains `getThoughtHistory()`, `getActionsTaken()`, `getObservations()` as `@JsonIgnore` derived accessors for any code still using the old parallel-list API

**Negative:**

- **Indirection overhead**: New developers must understand four files (`ReActAgent`, `PromptBuilder`, `AgentState`, `ToolNames`) instead of one
- **PromptBuilder is not a Spring bean**: It's manually instantiated in the `ReActAgent` constructor (because it depends on `ToolRegistry` and `AgentMemoryService` which are beans). This complicates testing slightly (requires mocking)

### Compliance

- All tool name references must use `ToolNames` constants (enforced by code review -- `grep` for bare string tool names)
- System prompt changes should be made in `prompts/system-prompt.txt`, not in Java code (fallback in `PromptBuilder` exists for development convenience)
- `AgentState` transitions must go through `addThought()`, `addAction()`, `addObservation()` rather than direct `List<Step>` manipulation

### How to Present This to Stakeholders

**To architects:** Emphasize the refactoring pattern: monolithic -> single-responsibility decomposition. The three parallel lists pattern is a known anti-pattern; replacing them with `List<Step>` where each step is a record demonstrates understanding of cohesive data structures. The externalized prompt template is a textbook example of separating configuration from code.

**To business stakeholders:** Frame as "we made the AI agent easier to improve by separating the prompt writing from the Java code. Now our prompt engineers can edit prompts directly without a Java developer, and each piece of the agent can be tested independently, reducing the risk of bugs."

**To engineers:** Walk through the before/after. Show how `addThought()` -> `addAction()` -> `addObservation()` creates immutable `Step` records. Show `buildIterationPrompt()` taking `AgentState` and building the prompt from `getFormattedHistory()`. Point out `truncateStepsToFit()` for context window management, which estimates token count as `totalChars / 4` and removes oldest steps when exceeding `maxContextTokens` (default 3000).

---

## ADR-007: Spring Boot 3.5.15 to 4.1.0 Migration

**Status**: Accepted

### Context

Spring AI 2.0.0 required Spring Boot 4.x as its baseline -- the project could not use the latest Spring AI features (PgVectorStore improvements, new embedding API, Retry.backoff builder) without upgrading from Spring Boot 3.5.15. Upgrading Spring Boot major versions is risky: breaking changes in configuration properties, API deprecations, removed classes, and transitive dependency conflicts can cascade across the entire codebase.

The project had a comprehensive test suite (181 tests) that needed to pass after migration. The migration also involved Spring AI 1.0.8 -> 2.0.0, which had its own breaking changes.

### Decision

Perform the **Spring Boot 3.5.15 -> 4.1.0 upgrade** alongside the **Spring AI 1.0.8 -> 2.0.0 upgrade** in a single coordinated change, with risk mitigation via full test suite validation.

**Changes made:**

| Area | Before | After |
|---|---|---|
| Parent POM | `spring-boot-starter-parent 3.5.15` | `spring-boot-starter-parent 4.1.0` |
| Spring AI BOM | `spring-ai.version 1.0.8` | `spring-ai.version 2.0.0` |
| Tomcat | Managed by Boot 3.5.15 | Managed by Boot 4.1.0 (CVE fixes included) |
| Jackson | CVE overrides in POM | Managed by Boot 4.1.0 (removed overrides) |
| OWASP plugin | `dependency-check` in build | Removed (CVE data issues) |

**Breaking changes handled:**

1. **`RestTemplateBuilder`**: Moved from `org.springframework.boot.web.client` to `org.springframework.boot.restclient`. Replaced with `ClientHttpRequestFactoryBuilder.simple()` using `HttpClientSettings` for connect/read timeouts.

2. **Removed test autoconfigure classes**: `@WebMvcTest`, `@MockBean`, `AutoConfigureMockMvc` were removed from Spring Boot 4.1.0's `spring-boot-test-autoconfigure`. Migrated controller tests to `MockMvcBuilders.standaloneSetup()` with manual mock injection.

3. **`TokenTextSplitter`**: Constructors deprecated in Spring AI 2.0.0. Changed to `TokenTextSplitter.builder()...build()` fluent API.

4. **Ollama embedding options**: Config properties changed from `spring.ai.ollama.embedding.options.temperature` to `spring.ai.ollama.embedding.temperature` (removed `.options` prefix).

5. **Jackson compatibility**: Spring Boot 4.x includes both Jackson 2.x and 3.x. Explicitly kept Jackson 2.x for backward compatibility with existing serialization.

### Consequences

**Positive:**

- **Spring AI 2.0.0 features**: Access to improved `PgVectorStore` builder, `Retry.backoff` with jitter, improved `TokenTextSplitter`
- **Java 21+ features**: Spring Boot 4.x is optimized for modern JDK releases (project uses Java 25)
- **CVE fixes**: Upgraded Tomcat, Jackson, Logback, and SnakeYAML to patched versions (managed by Boot 4.1.0)
- **Removed tech debt**: Deprecated CVE overrides and OWASP plugin removed from build

**Negative:**

- **Testing framework changes**: Controller tests required manual `MockMvcBuilders` setup instead of annotation-driven configuration -- all 9 controller test classes were affected
- **Configuration refactoring**: Every YAML property under `spring.ai.ollama.embedding.options.*` had to be updated to remove `.options`
- **Domino effect risk**: Spring AI 2.0.0 depends on Boot 4.x, which depends on the latest JDK -- dependency version conflicts required careful POM management

### Compliance

- All 181 tests must pass before merging (enforced by CI pipeline)
- No deprecated API usage from Spring Boot 3.x or Spring AI 1.x should remain (enforced by compile warnings)
- Every breaking change is documented in `AGENTS.md` (the project changelog)

### How to Present This to Stakeholders

**To architects:** Emphasize the risk mitigation strategy. A major-version framework upgrade is one of the highest-risk activities in a project. The key techniques used were: (1) full test suite as a safety net (181 tests), (2) one coordinated upgrade (Boot + Spring AI together) rather than sequential, (3) documentation of every breaking change in AGENTS.md. Point out the specific patterns that changed: `RestTemplateBuilder` API change, test autoconfigure removal, `TokenTextSplitter` builder pattern.

**To business stakeholders:** "We upgraded our framework to the latest version to get security patches and new features. We ran all 181 tests and fixed every issue before deploying. The upgrade was completed in a single phase to minimize disruption."

**To engineers:** Walk through each breaking change with before/after code. Show the `AppConfig.java` change from `RestTemplateBuilder` to `ClientHttpRequestFactoryBuilder.simple()`. Show a controller test migration from `@WebMvcTest` to `MockMvcBuilders.standaloneSetup()`. Explain how `TokenTextSplitter.builder()` replaces the deprecated constructor.


---

## ADR-008: RAG with Keyword Reranking

**Status**: Accepted

### Context

The RAG pipeline retrieved documents using pure cosine similarity against pgvector embeddings. While vector search captures semantic meaning well, it can miss documents that contain exact keyword matches but are semantically distant in the embedding space. For example, a query like "spring boot database connection pool" might rank documents about "HikariCP configuration" high (semantically similar) but rank a document literally titled "Spring Boot Database Connection Pool Setup" lower if its embedding vector is different from the query vector.

Users reported that document searches sometimes missed relevant results that contained the exact search terms. The pure vector approach was recall-limited: it found documents that "mean the same thing" but could miss documents that "contain the same words."

### Decision

Add a **keyword-based reranking step** after the initial vector search, using a hybrid scoring formula: `0.7 * cosine_similarity + 0.3 * keyword_overlap_ratio`.

**Implementation details:**

- `RagConfig` has a `rerankingEnabled` boolean field (default `true`) -- can be disabled via `app.rag.reranking-enabled=false` or `RAG_RERANKING_ENABLED=false` environment variable
- The reranking logic lives in `DocumentIngestionService.rerank()`:

```java
private List<Document> rerank(String query, List<Document> results) {
    List<String> queryTerms = Arrays.stream(query.toLowerCase().split("\\s+"))
            .filter(t -> t.length() > 2)
            .collect(Collectors.toList());
    // Skip if no meaningful query terms
    if (queryTerms.isEmpty()) return results;

    return results.stream()
            .sorted(Comparator.comparingDouble((Document doc) ->
                    computeCombinedScore(doc, queryTerms)).reversed())
            .collect(Collectors.toList());
}

private static double computeCombinedScore(Document doc, List<String> queryTerms) {
    double similarity = doc.getScore() != null ? doc.getScore().doubleValue() : 0.0;
    String text = doc.getText().toLowerCase();
    long matchCount = queryTerms.stream().filter(text::contains).count();
    double keywordOverlap = (double) matchCount / queryTerms.size();
    return 0.7 * similarity + 0.3 * keywordOverlap;
}
```

- The reranking only runs when `results.size() > 1` and `rerankingEnabled == true`
- The `0.7/0.3` ratio weights semantic similarity as primary and keyword overlap as a boost factor
- Query terms shorter than 3 characters are excluded (common noise words like "a", "an", "the", "in", "on")
- Documents are sorted by combined score in descending order

### Consequences

**Positive:**

- **Improved recall**: Documents with exact keyword matches are boosted above purely vector-similar ones, capturing results that pure vector search would miss
- **Configurable**: `rerankingEnabled` allows toggling the feature without code changes -- valuable for A/B testing or if performance is a concern
- **Minimal overhead**: Reranking runs on a small set of results (top-K, default 5) so the O(n log n) sort is negligible compared to the embedding query
- **Transparent formula**: The weighted scoring is intuitive and easy to tune -- the `0.7/0.3` ratio can be adjusted based on domain requirements

**Negative:**

- **Potential over-boosting**: Documents that happen to contain many query keywords but are semantically irrelevant could be ranked higher than semantically relevant documents (though the 0.7 weight on similarity mitigates this)
- **No stemming or lemmatization**: "running" and "run" are treated as different keywords -- keyword overlap is substring-based, not linguistically aware
- **English-only bias**: The current implementation assumes whitespace-delimited words, which works for English but may perform poorly for languages like Chinese, Japanese, or Korean

### Compliance

- Reranking behavior is tested in `DocumentIngestionServiceTest` with known queries and expected result orderings
- The `RagConfig` binding is validated by `VectorStoreConfigTest` which checks configuration property mapping

### How to Present This to Stakeholders

**To architects:** Emphasize the hybrid retrieval pattern -- combining dense (vector) and sparse (keyword) retrieval is a well-known approach in information retrieval research, often called "hybrid search." Explain that pure vector search optimizes for semantic similarity while keyword match optimizes for exact recall. The 0.7/0.3 ratio can be tuned per use case or even made configurable per-query in future iterations.

**To business stakeholders:** "Sometimes users search for specific terms that appear in documents but aren't semantically related enough for the AI to find them. We added a hybrid search that uses both meaning (vector similarity) and exact word matching to ensure users always find what they're looking for."

**To engineers:** Walk through the `search()` method flow: `vectorStore.similaritySearch()` returns top-K results with cosine similarity scores -> `rerank()` extracts query terms (filtering short words) -> `computeCombinedScore()` calculates hybrid score for each document -> results sorted by hybrid score. Show how `RagConfig.rerankingEnabled` controls the feature and how `RAG_RERANKING_ENABLED=false` disables it.

---

# Bonus: Guide to Writing ADRs in an Interview

When an interviewer asks you to "describe a key architectural decision you made," the ADR framework is your structure. Here is how to use it effectively.

## The 3-Minute ADR Framework

Most interviewers expect a concise, structured answer in 2-3 minutes. Structure your response as:

| Section | Time | What to Say |
|---|---|---|
| **Context** | 30s | What was the problem or constraint? What options were available? |
| **Decision** | 60s | What did you choose and why? Include the pattern name (Strategy, Chain-of-Responsibility, etc.) |
| **Consequences** | 45s | What improved? What trade-offs did you accept? Be honest about negatives. |
| **Impact** | 15s | How did this affect the team, the product, or the timeline? |

## Template Answer: ReAct Agent Decomposition

Here is a complete answer template that uses the ADR framework for ADR-006:

---

**Interviewer:** "Tell me about a key architectural decision you made on your last project."

**You:**

"One of the most impactful architectural decisions I made was how to structure our ReAct agent's code. Let me walk you through the decision using the ADR format.

**Context:** Our AI agent system followed the ReAct pattern -- Reasoning + Acting -- where an LLM iteratively decides whether to answer the user or call a tool. Originally, the agent was a single 350-line class that did everything: build prompts, track state, call the LLM, and execute tools. State was tracked through three parallel lists -- one for thoughts, one for actions, one for observations. You had to add to all three lists at the same index to keep them in sync, which was very fragile. The system prompt was also embedded as a hardcoded string inside the agent class, so any prompt change required modifying Java code and recompiling.

**Decision:** I decomposed the monolithic agent into three focused classes. First, I created a `PromptBuilder` class responsible for all prompt construction -- building the system prompt, the conversation context, and the iteration prompt. This allowed us to externalize the system prompt template to a text file on the classpath. Second, I introduced a `Step` record in Java that bundled a thought, action, and observation into a single data structure, replacing the three parallel lists with a single `List<Step>`. Third, I created a `ToolNames` constants file to eliminate magic strings -- tool names like 'weather' and 'rag_search' became `ToolNames.WEATHER` and `ToolNames.RAG_SEARCH`.

**Consequences:** The benefits were significant. The agent became testable -- we added 9 tests just for state management, and the prompt builder could be verified independently. The externalized prompt template meant our prompt engineers could iterate on prompts without involving Java developers. The `Step` record made the state model self-documenting: each iteration was a complete unit of thought-action-observation rather than indexes across three arrays. The trade-off was increased indirection -- new developers now had to understand four files instead of one -- but the improved testability and separation of concerns more than made up for it.

**Impact:** This decision reduced the time to add new features significantly. When we later added context window management with automatic step truncation, the change was isolated to `AgentState`'s `truncateStepsToFit()` method and did not touch the orchestration logic. The 181-test suite gave us confidence, and the decomposed architecture made the codebase approachable for new team members."

---

## Why This Answer Works

1. **It names the problem concretely**: "Three parallel lists" is a specific, relatable pain point. Every engineer has maintained parallel data structures and understands the fragility.

2. **It names the pattern**: "Single-responsibility decomposition" and "record as a cohesive data structure" show architectural vocabulary.

3. **It is honest about trade-offs**: "Increased indirection" shows you understand that not all refactoring benefits are free. This builds credibility.

4. **It ties back to measurable impact**: "Reduced time to add features," "approachable for new team members" -- connect code quality to team productivity.

5. **It follows the ADR structure**: Context -> Decision -> Consequences -> Impact. The interviewer hears a complete, logical argument.

## Adapting the Framework to Other ADRs

For each of the 8 ADRs in this document, you can adapt the same 3-minute structure. Here is a quick reference:

| ADR | Pattern to Emphasize | Key Trade-off to Mention |
|---|---|---|
| ADR-001: Multi-Provider | Chain-of-Responsibility | Latency from availability checks vs. reliability |
| ADR-002: PostgreSQL + pgvector | Consolidated persistence | Single-DB simplicity vs. specialized vector DB features |
| ADR-003: Interface-Driven Tools | Strategy + Registry | Plugin extensibility vs. no runtime tool loading |
| ADR-004: JWT Token Binding | Soft binding / defense-in-depth | Security detection vs. user convenience (warn vs. reject) |
| ADR-005: Graceful Degradation | Fail-fast + fallback | Operational resilience vs. silent data loss (Kafka events) |
| ADR-006: Agent Decomposition | Single Responsibility | Testability/ maintainability vs. indirection overhead |
| ADR-007: Spring Boot Upgrade | Coordinated migration | New features/security vs. test refactoring effort |
| ADR-008: RAG Reranking | Hybrid retrieval | Improved recall vs. potential over-boosting of keywords |

## Common Pitfalls to Avoid

**Don't just describe what you built.** "We used PostgreSQL with pgvector" is not an ADR. The question is *why did you choose it over alternatives?* Always mention Redis and ChromaDB as alternatives that were ruled out, and explain why.

**Don't avoid trade-offs.** Interviewers are skeptical of candidates who only mention positives. For every decision, state at least one negative consequence you accepted. For ADR-002, that is "pgvector is less specialized than Pinecone." For ADR-005, that is "events are silently discarded when Kafka is down."

**Don't use jargon without explaining it.** "Chain-of-responsibility pattern" is good vocabulary, but follow it with a one-sentence explanation: "where each provider checks if it's available before the next one tries."

**Do tie decisions to outcomes.** "We added RAG reranking and saw a 30% improvement in document retrieval recall in our test set" is more compelling than "we added RAG reranking."

---

*End of SECTION 5: Practice with ADRs*



---

*End of Interview Preparation Guide — Good luck! 🚀*

