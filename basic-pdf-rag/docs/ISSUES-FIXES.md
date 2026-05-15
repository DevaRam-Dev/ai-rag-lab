# Issues & Fixes Log — RAG basic-pdf-rag

Running log of all issues encountered during development, testing, and demo runs.
Add new entries at the bottom with the next sequential number.

---

## Log Table

| # | Date | Issue | Root Cause | Fix | Status |
|---|------|-------|------------|-----|--------|
| 1 | 2026-05-09 | Generated files landed in repo root instead of `basic-pdf-rag/` | AI scaffold defaulted to CWD (repo root) instead of module dir | Manually moved files with `mv` commands | ✅ Fixed |
| 2 | 2026-05-09 | pgvector `::vector` cast syntax error in native query | Hibernate pre-parses SQL and rejects PostgreSQL-specific `::` cast shorthand | Changed to `CAST(... AS vector)` SQL-standard syntax | ✅ Fixed |
| 3 | 2026-05-09 | Port 8080 already in use on startup | Orphaned JVM process from a previous killed session | `sudo lsof -ti :8080 \| xargs kill -9` | ✅ Fixed |
| 4 | 2026-05-09 | Ollama connection refused — embed API call failed | Ollama service not started before launching Spring Boot | `ollama serve &` then restarted ingestion | ✅ Fixed |
| 5 | 2026-05-09 | Frontend CORS error when opened as `file://` | Browser treats `file://` as `null` origin; Spring Boot CORS didn't cover it | Moved `index.html` to `src/main/resources/static/` — served by Spring Boot on same origin | ✅ Fixed |

---

## Detailed Entries

### Issue 1 — Files Generated in Wrong Directory

- **Date:** 2026-05-09
- **Issue:** Claude Code scaffold generated `pom.xml`, `src/`, `application.properties` in repo root `/ai-rag-lab/` instead of `/ai-rag-lab/basic-pdf-rag/`
- **Root Cause:** AI file generation did not scope the output to the intended module subdirectory
- **Fix:** `mv pom.xml basic-pdf-rag/ && mv src/ basic-pdf-rag/`
- **Status:** ✅ Fixed

---

### Issue 2 — pgvector `::vector` Cast Syntax Error

- **Date:** 2026-05-09
- **Issue:** `org.hibernate.exception.SQLGrammarException: could not prepare statement` — syntax error at `::`
- **Root Cause:** Hibernate's SQL pre-parser does not support PostgreSQL-specific `::` cast shorthand in native queries
- **Fix:** Changed `('[' || embedding || ']')::vector` → `CAST('[' || embedding || ']' AS vector)` in `DocChunkRepository`
- **Status:** ✅ Fixed

---

### Issue 3 — Port 8080 Already in Use

- **Date:** 2026-05-09
- **Issue:** `Web server failed to start. Port 8080 was already in use.`
- **Root Cause:** Previous Spring Boot JVM process was force-killed but not fully terminated; port remained bound
- **Fix:** `sudo lsof -ti :8080 | xargs kill -9` then restarted with `mvn spring-boot:run`
- **Status:** ✅ Fixed

---

### Issue 4 — Ollama Not Running

- **Date:** 2026-05-09
- **Issue:** `java.net.ConnectException: Connection refused` when attempting embed API call to `localhost:11434`
- **Root Cause:** Ollama service was not started before launching the Spring Boot application
- **Fix:** `ollama serve &` to start Ollama in background; verified with `curl localhost:11434/api/tags`
- **Status:** ✅ Fixed

---

### Issue 5 — CORS Error with `file://` Frontend

- **Date:** 2026-05-09
- **Issue:** All `fetch()` calls blocked with `Access-Control-Allow-Origin` CORS error when `index.html` opened directly from filesystem
- **Root Cause:** Browser treats `file://` as `null` origin; `@CrossOrigin(origins = "*")` does not reliably cover `null`
- **Fix:** Moved `index.html` to `src/main/resources/static/` so it is served by Spring Boot at `http://localhost:8080` (same origin as API)
- **Status:** ✅ Fixed

---

## Template for New Issues

```
### Issue N — Short Title

- **Date:** YYYY-MM-DD
- **Issue:** Brief description of the problem observed
- **Root Cause:** Why did it happen?
- **Fix:** Exact command or code change that resolved it
- **Status:** ✅ Fixed / 🔄 In Progress / ❌ Open
```
