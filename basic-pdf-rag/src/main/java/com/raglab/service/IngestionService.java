package com.raglab.service;

import com.raglab.entity.DocChunk;
import com.raglab.repository.DocChunkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Orchestrates the full PDF ingestion pipeline: extract → chunk → embed → persist.
 *
 * <p>Accepts a Spring {@link MultipartFile} representing an uploaded PDF, delegates text
 * extraction and chunking to {@link PdfChunkingService}, generates vector embeddings for
 * each chunk via {@link EmbeddingService}, and finally saves each {@link DocChunk} entity
 * to PostgreSQL through {@link DocChunkRepository}.  Returns the total number of chunks
 * saved so the caller can include it in the API response.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final PdfChunkingService pdfChunkingService;
    private final EmbeddingService   embeddingService;
    private final DocChunkRepository docChunkRepository;

    /**
     * Lifecycle hook that logs a build marker at service startup.
     */
    @PostConstruct
    public void init() {
        log.info(box("IngestionService initialized and ready")
            + lbl("Layer",   "SERVICE → IngestionService")
            + lbl("ANALOGY", "PDF processing pipeline armed and ready"));
    }

    /**
     * Processes an uploaded PDF file through the full ingestion pipeline.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Opens the {@link MultipartFile} input stream and passes it to
     *       {@link PdfChunkingService#extractAndChunk(java.io.InputStream)} to obtain text chunks.</li>
     *   <li>Iterates over each chunk, calls {@link EmbeddingService#generateEmbeddingAsString(String)}
     *       to produce the vector representation, and builds a {@link DocChunk} entity.</li>
     *   <li>Saves each entity individually via the repository; logs the chunk index and a snippet
     *       of its content so progress can be monitored in the application logs.</li>
     * </ol>
     * </p>
     *
     * @param file the uploaded PDF as a Spring {@link MultipartFile}
     * @return total number of chunks successfully ingested
     * @throws IOException          if PDFBox cannot read the PDF or the HTTP call to Ollama fails
     * @throws InterruptedException if the embedding HTTP call is interrupted
     */
    public int ingestPdf(MultipartFile file) throws IOException, InterruptedException {
        String filename = file.getOriginalFilename();

        // ── STEP 1: Extract text and chunk ───────────────────────────────────────
        long step1Start = System.currentTimeMillis();
        log.info(box("STEP 1 : Extract Text and Chunk PDF")
            + lbl("Layer",       "SERVICE → IngestionService")
            + lbl("Input",       "filename=" + filename + ", size=" + file.getSize() + " bytes")
            + lbl("Description", "PDFBox extracts full text; sliding-window chunker splits into chunks"));

        List<String> chunks = pdfChunkingService.extractAndChunk(file.getInputStream(), filename);
        long step1Ms = System.currentTimeMillis() - step1Start;

        log.info("[DOC] CHUNKS CREATED | filename={} | chunkCount={} | strategy=sliding-window | duration={}ms",
                filename, chunks.size(), step1Ms);

        // ── STEP 2: Embed each chunk and persist ─────────────────────────────────
        long step2Start = System.currentTimeMillis();
        log.info(box("STEP 2 : Embed Chunks and Persist to PostgreSQL")
            + lbl("Layer",       "SERVICE → IngestionService")
            + lbl("Input",       chunks.size() + " chunks from '" + filename + "'")
            + lbl("Description", "nomic-embed-text generates vector per chunk; each saved to doc_chunks table"));

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText    = chunks.get(i);
            String embeddingStr = embeddingService.generateEmbeddingAsString(chunkText);

            DocChunk entity = DocChunk.builder()
                    .filename(filename)
                    .chunkIndex(i)
                    .content(chunkText)
                    .embedding(embeddingStr)
                    .build();

            docChunkRepository.save(entity);
            log.info("[DB] INSERT | table=doc_chunks | chunk={}/{} | file={} | preview=\"{}...\"",
                    i + 1, chunks.size(), filename,
                    chunkText.length() > 50 ? chunkText.substring(0, 50) : chunkText);
        }

        long step2Ms = System.currentTimeMillis() - step2Start;

        // ── STEP 3: Complete ──────────────────────────────────────────────────────
        log.info(box("STEP 3 : Ingestion Complete")
            + lbl("Layer",       "SERVICE → IngestionService")
            + lbl("Output",      chunks.size() + " chunks saved for '" + filename + "'")
            + lbl("Duration",    step2Ms + "ms (embedding + persist loop)"));
        return chunks.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Log formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static final String BOX_H = "═".repeat(76);

    private static String box(String title) {
        return "\n╔" + BOX_H + "╗\n║  " + String.format("%-74s", title) + "║\n╚" + BOX_H + "╝";
    }

    private static String lbl(String label, Object value) {
        return "\n   " + String.format("%-11s", label) + " : " + value;
    }
}
