# Demo Steps — Basic PDF RAG

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 14+ | Must have the `vector` extension available |
| Ollama | Latest | [ollama.ai](https://ollama.ai) — must be running on port 11434 |
| pgvector | 0.5+ | `CREATE EXTENSION IF NOT EXISTS vector;` |
| Browser | Modern | Chrome / Firefox / Edge |

---

## Step 1 — Set up PostgreSQL database

```sql
-- Connect as superuser (e.g. psql -U postgres)
CREATE DATABASE ragdb;
\c ragdb
CREATE EXTENSION IF NOT EXISTS vector;
```

The JPA DDL-auto is set to `update`, so the `doc_chunks` table will be created automatically
on first startup. You can also run `schema.sql` manually:

```bash
psql -U postgres -d ragdb -f src/main/resources/schema.sql
```

---

## Step 2 — Pull Ollama models

```bash
# Embedding model
ollama pull nomic-embed-text

# LLM for answer generation
ollama pull qwen2.5-coder:1.5b

# Verify models are available
ollama list
```

---

## Step 3 — Start the Spring Boot application

```bash
# From the project root (ai-rag-lab/)
mvn spring-boot:run
```

Expected startup output:
```
=== PdfChunkingService initialized === 2026-...
=== EmbeddingService initialized    === 2026-...
=== IngestionService initialized    === 2026-...
=== QueryService initialized        === 2026-...
Started BasicPdfRagApplication in X.XXX seconds
```

Application runs on: **http://localhost:8080**

---

## Step 4 — Open the frontend

Open a new terminal and navigate to the frontend folder:

```bash
# On Linux / Mac
xdg-open ../../frontend/index.html
# Or just open the file directly in your browser:
# file:///home/<user>/Projects/CursorAI_Projects/frontend/index.html
```

The UI shows two panels: **Upload PDF** (left) and **Chat** (right).

---

## Step 5 — Ingest a PDF

1. Click **"Click or drag a PDF here"** in the Upload Panel.
2. Select any PDF file (max 50 MB).
3. Click **"Upload & Ingest"**.
4. Wait for the success banner: `"Ingested N chunks from filename.pdf"`.
5. Click **Refresh List** — the PDF filename appears in the Uploaded Documents list.

You can also use curl:

```bash
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@/path/to/your/document.pdf"
```

---

## Step 6 — Ask a question

1. Type your question in the Chat Panel textarea (e.g. *"What is the main topic of this document?"*).
2. Press **Ask** or hit **Ctrl+Enter**.
3. The assistant streams back an answer based on the retrieved chunks.
4. Click **"Show N source chunk(s)"** to see the raw text the LLM used as context.

You can also use curl:

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the main topic of this document?"}'
```

---

## Step 7 — List ingested documents

```bash
curl http://localhost:8080/api/documents
# Returns: ["filename.pdf", ...]
```

---

## Step 8 — Package as a JAR (optional)

```bash
mvn clean package -DskipTests
java -jar target/basic-pdf-rag-1.0.0.jar
```

---

## Troubleshooting quick-reference

| Symptom | Likely cause | Fix |
|---|---|---|
| `Connection refused` on startup | PostgreSQL not running | `sudo systemctl start postgresql` |
| `relation "doc_chunks" does not exist` | Table not created | Run `schema.sql` manually |
| `operator does not exist: text <=> text` | pgvector extension missing | `CREATE EXTENSION vector;` in ragdb |
| Ollama `connection refused` | Ollama not running | `ollama serve` in a separate terminal |
| Empty answer from LLM | No documents ingested | Upload at least one PDF first |
| CORS error in browser | Opening HTML via `file://` with strict browser | Serve the HTML via a local HTTP server |
