# Architecture — Basic PDF RAG

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Runtime | Java 21 | Application language |
| Framework | Spring Boot 3.2 | Web server, DI, JPA |
| Embedding model | Ollama `nomic-embed-text` | Dense vector generation |
| LLM | Ollama `qwen2.5-coder:1.5b` | Answer generation |
| Database | PostgreSQL + pgvector | Chunk storage + similarity search |
| ORM | Spring Data JPA / Hibernate | Entity persistence |
| PDF parsing | Apache PDFBox 2.0.29 | Text extraction |
| JSON | Jackson Databind | Ollama API serialisation |
| Frontend | Vanilla React 18 (CDN) | Single-page chat UI |
| Build | Maven 3 | Dependency management |

---

## Component Diagram (ASCII)

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser (index.html)                     │
│   ┌──────────────────┐          ┌──────────────────────────┐    │
│   │   Upload Panel   │          │       Chat Panel          │    │
│   │  POST /api/ingest│          │  POST /api/query          │    │
│   │  GET  /api/docs  │          │  GET  /api/documents      │    │
└───┼──────────────────┼──────────┼───────────────────────────┼───┘
    │                  │          │                           │
    ▼                  ▼          ▼                           ▼
┌────────────────────────────────────────────────────────────────┐
│                     Spring Boot Application                    │
│                                                                │
│  ┌──────────────────────────┐   ┌───────────────────────────┐ │
│  │   IngestionController    │   │     QueryController       │ │
│  │   POST /api/ingest       │   │   POST /api/query         │ │
│  │                          │   │   GET  /api/documents     │ │
│  └──────────┬───────────────┘   └──────────┬────────────────┘ │
│             │                              │                   │
│             ▼                              ▼                   │
│  ┌──────────────────────┐    ┌─────────────────────────────┐  │
│  │   IngestionService   │    │        QueryService         │  │
│  │  orchestrates:       │    │  orchestrates:              │  │
│  │  1. chunking         │    │  1. embed question          │  │
│  │  2. embedding        │    │  2. similarity search       │  │
│  │  3. persistence      │    │  3. prompt assembly         │  │
│  └──┬───────────┬───────┘    │  4. LLM call + parse        │  │
│     │           │            └──┬──────────────────────────┘  │
│     │           │               │                             │
│     ▼           ▼               ▼                             │
│  ┌──────────┐ ┌───────────┐ ┌──────────────────┐             │
│  │PdfChunking│ │Embedding  │ │  EmbeddingService│             │
│  │Service   │ │Service    │ │  (shared)        │             │
│  │(PDFBox)  │ │(Ollama    │ └────────┬─────────┘             │
│  └──────────┘ │/api/      │          │                        │
│               │embeddings)│          │                        │
│               └───────────┘          │                        │
│                                      ▼                        │
│                            ┌──────────────────────┐           │
│                            │  DocChunkRepository  │           │
│                            │  (Spring Data JPA)   │           │
│                            └──────────┬───────────┘           │
└───────────────────────────────────────┼───────────────────────┘
                                        │
                    ┌───────────────────┼────────────────────┐
                    │                   ▼                    │
                    │       PostgreSQL + pgvector            │
                    │       Table: doc_chunks                │
                    │       (id, filename, chunk_index,      │
                    │        content TEXT, embedding TEXT)   │
                    └───────────────────────────────────────-┘
                                        │
                    ┌───────────────────┼─────────────────────┐
                    │        Ollama (localhost:11434)          │
                    │  POST /api/embeddings  (nomic-embed-text)│
                    │  POST /api/generate    (qwen2.5-coder)   │
                    └──────────────────────────────────────────┘
```

---

## Ingestion Flow

```
User uploads PDF
      │
      ▼
IngestionController.ingest(MultipartFile)
      │
      ▼
IngestionService.ingestPdf(file)
      │
      ├─1─► PdfChunkingService.extractAndChunk(InputStream)
      │         │
      │         ├─ PDFBox: PDDocument.load() + PDFTextStripper.getText()
      │         └─ chunkText(): sliding window 500 chars / 50 overlap
      │
      ├─2─► for each chunk:
      │         EmbeddingService.generateEmbeddingAsString(chunkText)
      │             └─ POST localhost:11434/api/embeddings → float[]
      │                 └─ embeddingToString() → "0.1,0.2,..."
      │
      └─3─► DocChunkRepository.save(DocChunk entity)
                └─ PostgreSQL INSERT into doc_chunks
```

---

## Query Flow

```
User submits question
      │
      ▼
QueryController.query(QueryRequest)
      │
      ▼
QueryService.answer(question)
      │
      ├─1─► EmbeddingService.generateEmbeddingAsString(question)
      │         └─ POST localhost:11434/api/embeddings → comma-separated floats
      │
      ├─2─► DocChunkRepository.findTopChunksByEmbedding(queryEmbedding, 3)
      │         └─ native SQL: ORDER BY ('[' || embedding || ']')::vector
      │                            <=> ('[' || :queryEmbedding || ']')::vector
      │                        LIMIT 3
      │
      ├─3─► buildPrompt(chunks, question)
      │         └─ "Answer based on context:\n<chunk1>...\nQuestion: ..."
      │
      ├─4─► POST localhost:11434/api/generate (stream=true)
      │         └─ qwen2.5-coder:1.5b
      │
      └─5─► parseStreamedResponse(body)
                └─ read NDJSON lines, concatenate "response" fields until done=true
```

---

## REST Endpoints

| Method | Path | Body / Params | Response |
|---|---|---|---|
| `POST` | `/api/ingest` | Multipart `file` (PDF) | `{"message":"Ingested N chunks from filename.pdf"}` |
| `POST` | `/api/query` | `{"question":"..."}` | `{"answer":"...","sourceChunks":["..."]}` |
| `GET` | `/api/documents` | — | `["file1.pdf","file2.pdf"]` |
