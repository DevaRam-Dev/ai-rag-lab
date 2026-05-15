# Architecture — RAG basic-pdf-rag

## Project Overview

A Retrieval-Augmented Generation (RAG) system built with Spring Boot and pgvector.
Users upload PDF documents; the system extracts, chunks, and embeds the text into a
PostgreSQL vector database. At query time, the most semantically relevant chunks are
retrieved and fed as context to a local LLM to generate a grounded, hallucination-reduced answer.

---

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2 |
| Build Tool | Maven | 3.x |
| Database | PostgreSQL | 14 |
| Vector Extension | pgvector | 0.8.2 |
| AI Runtime | Ollama | latest |
| Embed Model | nomic-embed-text | 274 MB |
| LLM Model | qwen2.5-coder:1.5b | 986 MB |
| PDF Parsing | Apache PDFBox | 2.0.29 |
| Frontend | React (vanilla CDN) | 18 |

---

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                     React UI (index.html)                        │
│        Upload Panel             Chat Panel                       │
│      POST /api/ingest        POST /api/query                     │
│      GET  /api/documents     GET  /api/documents                 │
└──────────────┬───────────────────────┬───────────────────────────┘
               │        HTTP REST      │
    ┌──────────▼──────────┐   ┌────────▼─────────────┐
    │  IngestionController│   │   QueryController    │
    └──────────┬──────────┘   └────────┬─────────────┘
               │                       │
    ┌──────────▼──────────┐   ┌────────▼─────────────┐
    │  IngestionService   │   │   QueryService        │
    │  (orchestrates)     │   │   (orchestrates)      │
    └──┬───────────┬──────┘   └────────┬─────────────┘
       │           │                   │
       ▼           ▼                   ▼
  ┌─────────┐ ┌──────────┐     ┌──────────────┐
  │PdfChunking│ │Embedding │     │EmbeddingService│
  │Service  │ │Service   │◄────┤(shared)      │
  │(PDFBox) │ │(Ollama)  │     └──────────────┘
  └─────────┘ └──────────┘
       │           │                   │
       └─────┬─────┘                   │
             ▼                         ▼
     ┌───────────────┐       ┌─────────────────┐
     │DocChunkRepo   │       │DocChunkRepo     │
     │(Spring Data   │       │findTopChunks    │
     │ JPA / save)   │       │ByEmbedding      │
     └───────┬───────┘       └────────┬────────┘
             │                        │
             ▼                        ▼
     ┌──────────────────────────────────────────┐
     │        PostgreSQL 14 + pgvector           │
     │           Table: doc_chunks               │
     └──────────────────────────────────────────┘
             │                        │
             ▼                        ▼
     ┌──────────────────────────────────────────┐
     │        Ollama (localhost:11434)           │
     │  POST /api/embeddings  nomic-embed-text   │
     │  POST /api/generate    qwen2.5-coder:1.5b │
     └──────────────────────────────────────────┘
```

---

## Ingestion Flow

1. User uploads PDF via `POST /api/ingest` (multipart form field: `file`)
2. `IngestionController` receives the `MultipartFile` and delegates to `IngestionService`
3. `PdfChunkingService` calls Apache PDFBox (`PDDocument.load` + `PDFTextStripper`) to extract full text
4. Text is split into chunks of **500 characters** with **50-character overlap** (sliding window)
5. For each chunk, `EmbeddingService` calls Ollama `nomic-embed-text` → returns 768-dimensional float vector
6. Each chunk + embedding is persisted as a `DocChunk` entity in the `doc_chunks` table

---

## Query Flow

1. User submits a question via `POST /api/query` (JSON: `{"question":"..."}`)
2. `QueryController` receives `QueryRequest` and delegates to `QueryService`
3. `EmbeddingService` embeds the question using `nomic-embed-text` → 768-dim vector
4. `DocChunkRepository.findTopChunksByEmbedding` runs a pgvector cosine similarity search (`<=>`)
5. **Top 3** most relevant chunks are retrieved from the database
6. `QueryService.buildPrompt` assembles: retrieved chunks + question into a context-rich prompt
7. Prompt is sent to `qwen2.5-coder:1.5b` via Ollama `POST /api/generate` (stream=true)
8. Streamed NDJSON response tokens are concatenated into the final answer string
9. `QueryResponse` (answer + source chunks list) is returned to the UI

---

## REST API Endpoints

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| `POST` | `/api/ingest` | `multipart/form-data` with `file` field (PDF) | `{"message": "Ingested N chunks from filename.pdf"}` |
| `POST` | `/api/query` | `{"question": "..."}` | `{"answer": "...", "sourceChunks": ["...", "..."]}` |
| `GET` | `/api/documents` | — | `["file1.pdf", "file2.pdf"]` |

---

## Database Schema

Table: `doc_chunks`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL PRIMARY KEY` | Auto-increment surrogate key |
| `filename` | `TEXT NOT NULL` | Original PDF filename (e.g. `Go_Tyme_Bank.pdf`) |
| `chunk_index` | `INT NOT NULL` | Zero-based position of chunk within the document |
| `content` | `TEXT NOT NULL` | Raw text of this chunk (up to 500 characters) |
| `embedding` | `TEXT NOT NULL` | Comma-separated float vector — 768 values (nomic-embed-text output) |

> **Why TEXT for embedding?** pgvector's `vector` type requires a fixed declared dimension at column definition time.
> Storing as TEXT with `CAST('[' || embedding || ']' AS vector)` at query time avoids schema coupling and
> allows switching embed models without an ALTER TABLE.
