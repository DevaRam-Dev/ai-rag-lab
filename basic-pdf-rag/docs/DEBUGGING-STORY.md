# Debugging Story — RAG basic-pdf-rag

A chronological account of real issues encountered and resolved while building this project.
Each entry follows the same format: Problem → Root Cause → Fix → Lesson Learned.

---

## Issue 1 — Generated Files Landed in Repo Root Instead of `basic-pdf-rag/`

**Problem:**
Claude Code scaffold generated all Java source files (`pom.xml`, `src/`, `application.properties`, etc.)
in the repository root `/ai-rag-lab/` instead of the intended `/ai-rag-lab/basic-pdf-rag/` subfolder.
The wrong working directory was assumed during file generation.

**Root Cause:**
The AI file generation did not have an explicit target directory scoped to the module. It defaulted
to the current working directory which was the repo root at the time of execution.

**Fix:**
```bash
# Manually relocated all misplaced files
mv pom.xml basic-pdf-rag/
mv src/ basic-pdf-rag/
mv application.properties basic-pdf-rag/
```

**Lesson Learned:**
Always specify the exact target directory when scaffolding submodules inside a monorepo.
Verify file placement immediately after generation with `ls -R <target_dir>` before proceeding.

---

## Issue 2 — pgvector `::vector` Cast Syntax Error in Native Query

**Problem:**
The repository's `findTopChunksByEmbedding` native SQL query used PostgreSQL cast shorthand:
```sql
ORDER BY ('[' || embedding || ']')::vector <=> ('[' || :queryEmbedding || ']')::vector
```
Spring Data JPA / Hibernate rejected this with:
```
org.hibernate.exception.SQLGrammarException: could not prepare statement
ERROR: syntax error at or near "::"
```

**Root Cause:**
The `::` cast operator is a PostgreSQL-specific shorthand. When the query is parsed by Hibernate's
JPQL/HQL parser before being sent to PostgreSQL, the `::` is not recognized as a valid token,
causing a grammar exception at the driver level.

**Fix:**
Changed all `::vector` casts to the SQL-standard `CAST(... AS vector)` syntax:
```sql
ORDER BY CAST('[' || embedding || ']' AS vector) <=> CAST('[' || :queryEmbedding || ']' AS vector)
```

**Lesson Learned:**
When writing native queries in Spring Data JPA, avoid PostgreSQL-specific syntax shortcuts (`::` casting,
`$1` parameters, etc.) that are not part of the SQL standard. Use ANSI SQL equivalents to avoid
Hibernate pre-parsing issues. Always test native queries directly in `psql` first.

---

## Issue 3 — Port 8080 Already in Use on Startup

**Problem:**
Running `mvn spring-boot:run` failed immediately with:
```
Web server failed to start. Port 8080 was already in use.
Action: Identify and stop the process that's listening on port 8080 or
        configure this application to listen on another port.
```
A stale previous Spring Boot process or another service was holding the port.

**Root Cause:**
A previous `mvn spring-boot:run` session was killed mid-startup but the JVM process was not
fully terminated. The orphaned process retained its bind on port 8080.

**Fix:**
```bash
# Find and kill the process holding port 8080
sudo lsof -ti :8080 | xargs kill -9

# Verify the port is free
sudo lsof -i :8080
# (should return no output)

# Then restart
mvn spring-boot:run
```

**Lesson Learned:**
After force-stopping a Spring Boot process, always verify the port is released before restarting.
Alternatively, configure a different port in `application.properties` (`server.port=8081`) for
development to avoid conflicts with system services running on 8080.

---

## Issue 4 — Ollama Not Running — Connection Refused on Embed API Call

**Problem:**
After starting the Spring Boot application and uploading a PDF, the ingestion pipeline failed with:
```
java.net.ConnectException: Connection refused
  at EmbeddingService.generateEmbedding(EmbeddingService.java:74)
```
The application could not reach `http://localhost:11434/api/embeddings`.

**Root Cause:**
The Ollama service was not started before launching the Spring Boot application.
Ollama must be running as a background process for the embedding and LLM API calls to succeed.

**Fix:**
```bash
# Start Ollama in the background
ollama serve &

# Verify it is listening
curl http://localhost:11434/api/tags
# Should return JSON listing available models

# Then restart ingestion
```

**Lesson Learned:**
Treat Ollama like a required infrastructure dependency — the same as PostgreSQL. Add it to the
prerequisites checklist in DEMO-STEPS.md and verify connectivity with `curl localhost:11434/api/tags`
before uploading any PDF. Document the startup sequence: PostgreSQL → Ollama → Spring Boot.

---

## Issue 5 — Frontend Served as `file://` — CORS and Fetch Blocked

**Problem:**
Opening `index.html` by double-clicking it in the file manager (served via `file://` protocol) caused
all `fetch()` calls to `http://localhost:8080/api/*` to fail silently or with a CORS error:
```
Access to fetch at 'http://localhost:8080/api/ingest' from origin 'null'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present.
```
The browser treats `file://` as origin `null`, which many CORS configurations reject.

**Root Cause:**
Modern browsers enforce CORS restrictions even on localhost. When the HTML page is served via
`file://` (origin = `null`), Spring Boot's `@CrossOrigin(origins = "*")` does not cover `null` origin
in all browser implementations.

**Fix:**
Moved `index.html` from the project root into Spring Boot's static resources directory:
```bash
mv index.html src/main/resources/static/index.html
```
Now the page is served by Spring Boot itself at `http://localhost:8080/index.html` (same origin
as the API), making CORS irrelevant — all fetch calls are same-origin.

**Lesson Learned:**
For Spring Boot applications with a bundled frontend, always place HTML/CSS/JS in
`src/main/resources/static/`. Never open HTML files directly from the filesystem during development;
always access them through the Spring Boot server URL (`http://localhost:8080`).
