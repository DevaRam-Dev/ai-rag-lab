# Logging Strategy — RAG basic-pdf-rag

Every layer prefixes its log messages with `[ClassName]` so you can `grep [IngestionService]`
to isolate a single layer's trace without modifying log configuration.

---

## Layer-by-Layer Logging Table

| Layer | Class | INPUT logged | OUTPUT logged |
|-------|-------|-------------|---------------|
| HTTP receive | `IngestionController` | filename, file size (bytes) | Response message string |
| HTTP receive | `QueryController` | question text | Answer length, source chunk count |
| Orchestration | `IngestionService` | filename | Total chunks saved |
| Orchestration | `QueryService` | question text | Answer length |
| PDF extraction | `PdfChunkingService` | filename, stream size (bytes) | Total pages, total chunks, chunk size, overlap |
| Embedding | `EmbeddingService` | model name, input text length | Vector dimensions, first-5-value preview |

---

## Per-Layer Detail

### 1. IngestionController

**Receives:** `MultipartFile` from HTTP multipart upload
**Logs:** filename and file size on entry; response message on exit
**Produces:** delegates to `IngestionService`, returns JSON response

```
[IngestionController] INPUT: filename=Go_Tyme_Bank.pdf, fileSize=204800bytes
[IngestionController] OUTPUT: Ingested 47 chunks from Go_Tyme_Bank.pdf
```

---

### 2. IngestionService

**Receives:** `MultipartFile` from controller
**Logs:** filename at entry, per-chunk save progress, total at exit
**Produces:** integer count of chunks saved

```
[IngestionService] INPUT: filename=Go_Tyme_Bank.pdf
[IngestionService] SAVED: chunk 1/47 for file=Go_Tyme_Bank.pdf
[IngestionService] SAVED: chunk 2/47 for file=Go_Tyme_Bank.pdf
...
[IngestionService] SAVED: chunk 47/47 for file=Go_Tyme_Bank.pdf
[IngestionService] OUTPUT: totalChunksSaved=47, file=Go_Tyme_Bank.pdf
```

---

### 3. PdfChunkingService

**Receives:** `InputStream` (PDF bytes) and filename
**Logs:** stream size on entry; page count, chunk count, chunk size, overlap on exit
**Produces:** `List<String>` of text chunks

```
[PdfChunkingService] INPUT: filename=Go_Tyme_Bank.pdf, inputStreamSize=204800bytes
PDF text extraction complete — 23500 characters extracted
[PdfChunkingService] OUTPUT: totalPages=12, totalChunks=47, chunkSize=500, overlap=50
```

---

### 4. EmbeddingService

**Receives:** raw text string and model name (from `@Value`)
**Logs:** model name and text length on entry; vector dimensions and first-5 value preview on exit
**Produces:** `float[]` (768-dim vector)

```
[EmbeddingService] INPUT: model=nomic-embed-text, textLength=487 chars
Generated embedding of dimension 768 for text snippet: "GoTyme Bank is a digital bank..."
[EmbeddingService] OUTPUT: vectorDimensions=768, embeddingPreview=[0.0312, -0.1456, 0.0891, 0.2103, -0.0774]...
```

---

### 5. QueryController

**Receives:** `QueryRequest` JSON body with `question` field
**Logs:** question on entry; answer length and source chunk count on exit
**Produces:** `QueryResponse` JSON

```
[QueryController] INPUT: question="What is attendance monitoring?"
[QueryController] OUTPUT: answerLength=842, sourceChunks=3
```

---

### 6. QueryService

**Receives:** question string from controller
**Logs:** question, embedding dimensions, retrieved chunk count, prompt length, answer length
**Produces:** `QueryResponse` (answer + source chunks)

```
[QueryService] INPUT: question="What is attendance monitoring?"
[QueryService] EMBEDDING: questionEmbeddingDimensions=768
[QueryService] RETRIEVED: 3 chunks from pgvector
[QueryService] PROMPT: length=1674 chars
[QueryService] OUTPUT: answerLength=842 chars
```

---

## Sample Full Ingestion Log (single file upload)

```
INFO  POST /api/ingest — received file: 'Go_Tyme_Bank.pdf', size: 204800 bytes
INFO  [IngestionController] INPUT: filename=Go_Tyme_Bank.pdf, fileSize=204800bytes
INFO  [IngestionService] INPUT: filename=Go_Tyme_Bank.pdf
INFO  Ingestion started — file: Go_Tyme_Bank.pdf, size: 204800 bytes
INFO  [PdfChunkingService] INPUT: filename=Go_Tyme_Bank.pdf, inputStreamSize=204800bytes
INFO  PDF text extraction complete — 23500 characters extracted
INFO  [PdfChunkingService] OUTPUT: totalPages=12, totalChunks=47, chunkSize=500, overlap=50
INFO  Text split into 47 chunks (size=500, overlap=50)
INFO  Chunking complete — 47 total chunks produced from 'Go_Tyme_Bank.pdf'
INFO  [EmbeddingService] INPUT: model=nomic-embed-text, textLength=487 chars
INFO  Generated embedding of dimension 768 for text snippet: "GoTyme Bank is a digital bank..."
INFO  [EmbeddingService] OUTPUT: vectorDimensions=768, embeddingPreview=[0.0312, -0.1456, 0.0891, 0.2103, -0.0774]...
INFO  [IngestionService] SAVED: chunk 1/47 for file=Go_Tyme_Bank.pdf
INFO  Saved chunk 1/47 from 'Go_Tyme_Bank.pdf' — preview: "GoTyme Bank is a digital bank fo..."
... (repeated for each chunk)
INFO  [IngestionService] OUTPUT: totalChunksSaved=47, file=Go_Tyme_Bank.pdf
INFO  Ingestion complete — 47 chunks saved for 'Go_Tyme_Bank.pdf'
INFO  POST /api/ingest — completed: Ingested 47 chunks from Go_Tyme_Bank.pdf
INFO  [IngestionController] OUTPUT: Ingested 47 chunks from Go_Tyme_Bank.pdf
```

---

## Sample Full Query Log (single question)

```
INFO  POST /api/query — question: "What is attendance monitoring?"
INFO  [QueryController] INPUT: question="What is attendance monitoring?"
INFO  [QueryService] INPUT: question="What is attendance monitoring?"
INFO  Query received: "What is attendance monitoring?"
INFO  [EmbeddingService] INPUT: model=nomic-embed-text, textLength=30 chars
INFO  Generated embedding of dimension 768 for text snippet: "What is attendance monitoring?..."
INFO  [EmbeddingService] OUTPUT: vectorDimensions=768, embeddingPreview=[0.1203, -0.0892, 0.3041, 0.0117, -0.2234]...
INFO  [QueryService] EMBEDDING: questionEmbeddingDimensions=768
INFO  Retrieved 3 chunks for similarity search
INFO  [QueryService] RETRIEVED: 3 chunks from pgvector
INFO  [QueryService] PROMPT: length=1674 chars
INFO  Answer generated — length: 842 characters
INFO  [QueryService] OUTPUT: answerLength=842 chars
INFO  POST /api/query — answer generated successfully
INFO  [QueryController] OUTPUT: answerLength=842, sourceChunks=3
```

---

## Grep Cheat Sheet

```bash
# Trace only IngestionController
grep "\[IngestionController\]" application.log

# Trace only EmbeddingService calls
grep "\[EmbeddingService\]" application.log

# See all INPUT lines across all layers
grep "INPUT:" application.log

# See all OUTPUT lines across all layers
grep "OUTPUT:" application.log

# Watch logs live while running
tail -f logs/spring.log | grep "\[QueryService\]"
```
