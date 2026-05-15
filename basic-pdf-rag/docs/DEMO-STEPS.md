# Demo Steps — RAG basic-pdf-rag

Complete walkthrough for running and demonstrating the basic-pdf-rag application from scratch.

---

## Prerequisites

| Requirement | Version | Verify |
|-------------|---------|--------|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 14+ | `psql --version` |
| pgvector extension | 0.8.2+ | `SELECT extversion FROM pg_extension WHERE extname='vector';` |
| Ollama | latest | `ollama --version` |
| nomic-embed-text model | — | `ollama list` |
| qwen2.5-coder:1.5b model | — | `ollama list` |
| Browser | Modern | Chrome / Firefox / Edge |

Pull Ollama models if not already present:
```bash
ollama pull nomic-embed-text
ollama pull qwen2.5-coder:1.5b
ollama list   # verify both appear
```

---

## Step 1 — Start PostgreSQL

```bash
sudo systemctl start postgresql

# Verify it is running
sudo systemctl status postgresql
```

Create the database and enable pgvector (first-time setup only):
```sql
psql -U postgres -c "CREATE DATABASE ragdb;"
psql -U postgres -d ragdb -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

The `doc_chunks` table is auto-created by Spring Boot on first startup (`spring.jpa.hibernate.ddl-auto=update`).

---

## Step 2 — Start Ollama

```bash
# Start in background
ollama serve &

# Verify API is responding
curl http://localhost:11434/api/tags
# Should return JSON with "models" array
```

> Ollama must be running BEFORE starting Spring Boot, as the application
> verifies connectivity at embed call time (not at startup).

---

## Step 3 — Start Spring Boot

```bash
cd ~/Projects/CursorAI_Projects/RAG/ai-rag-lab/basic-pdf-rag
mvn spring-boot:run
```

Expected startup log output:
```
=== PdfChunkingService initialized === 2026-05-09T10:00:00
=== EmbeddingService initialized    === 2026-05-09T10:00:00
=== IngestionService initialized    === 2026-05-09T10:00:00
=== QueryService initialized        === 2026-05-09T10:00:00
Started BasicPdfRagApplication in 3.241 seconds (JVM running for 3.812)
```

Application runs on: **http://localhost:8080**

---

## Step 4 — Open the Browser

Navigate to:
```
http://localhost:8080
```

You will see two panels:
- **Left — Upload Panel**: drag-and-drop PDF upload + list of ingested documents
- **Right — Chat Panel**: question input + answer display with source chunk viewer

> Do NOT open `index.html` as a `file://` URL — it must be served by Spring Boot to avoid CORS errors.

---

## Step 5 — Upload a PDF

1. In the **Upload Panel**, click **"Click or drag a PDF here"**
2. Select a PDF file (e.g. `Go_Tyme_Bank.pdf`, max 50 MB)
3. Click **"Upload & Ingest"**
4. Wait for the success banner: `"Ingested 47 chunks from Go_Tyme_Bank.pdf"`
5. Click **Refresh List** — the filename appears in the **Uploaded Documents** list

Or via curl:
```bash
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@/path/to/Go_Tyme_Bank.pdf"
```

Expected response:
```json
{"message": "Ingested 47 chunks from Go_Tyme_Bank.pdf"}
```

---

## Step 6 — Ask Questions in Chat Panel

Type a question and click **Ask** (or press `Ctrl+Enter`):

### Sample Questions

| Question | What to expect |
|----------|---------------|
| `What is the main topic of this document?` | High-level summary from top chunks |
| `What is attendance monitoring?` | Specific feature description |
| `How does GoTyme Bank handle customer onboarding?` | Process explanation from doc |
| `What are the key features of the product?` | Feature list from PDF content |
| `Summarise the document in 3 bullet points.` | Concise LLM summary |

Click **"Show N source chunk(s)"** below the answer to see the raw text chunks the LLM used as context.

Or via curl:
```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What is attendance monitoring?"}'
```

Expected response structure:
```json
{
  "answer": "Attendance monitoring is...",
  "sourceChunks": [
    "chunk text 1...",
    "chunk text 2...",
    "chunk text 3..."
  ]
}
```

---

## Step 7 — Verify Database Contents

```bash
# Connect to ragdb
psql -U postgres -d ragdb

# Count chunks stored
SELECT COUNT(*) FROM doc_chunks;

# See filenames ingested
SELECT DISTINCT filename FROM doc_chunks;

# Preview first chunk of a document
SELECT filename, chunk_index, LEFT(content, 100)
FROM doc_chunks
WHERE filename = 'Go_Tyme_Bank.pdf'
ORDER BY chunk_index
LIMIT 5;
```

---

## Troubleshooting Quick Reference

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Port 8080 already in use | Stale Spring Boot process | `sudo lsof -ti :8080 \| xargs kill -9` |
| `Connection refused` to Ollama | Ollama not started | `ollama serve &` |
| `Connection refused` to PostgreSQL | PostgreSQL not running | `sudo systemctl start postgresql` |
| Empty answer from LLM | No documents ingested | Upload a PDF first |
| CORS error in browser | Opened HTML via `file://` | Use `http://localhost:8080` |
| `relation "doc_chunks" does not exist` | pgvector extension missing or schema not created | Run `schema.sql` manually |
| `operator does not exist: text <=> text` | pgvector extension not enabled in ragdb | `CREATE EXTENSION vector;` in ragdb |
