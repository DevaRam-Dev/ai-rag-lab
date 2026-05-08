package com.raglab.service;

import com.raglab.entity.DocChunk;
import com.raglab.repository.DocChunkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
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
        log.info("=== IngestionService initialized === {}", LocalDateTime.now());
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
        log.info("Ingestion started — file: {}, size: {} bytes", filename, file.getSize());

        List<String> chunks = pdfChunkingService.extractAndChunk(file.getInputStream());
        log.info("Chunking complete — {} total chunks produced from '{}'", chunks.size(), filename);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText      = chunks.get(i);
            String embeddingStr   = embeddingService.generateEmbeddingAsString(chunkText);

            DocChunk entity = DocChunk.builder()
                    .filename(filename)
                    .chunkIndex(i)
                    .content(chunkText)
                    .embedding(embeddingStr)
                    .build();

            docChunkRepository.save(entity);
            log.info("Saved chunk {}/{} from '{}' — preview: \"{}...\"",
                    i + 1, chunks.size(), filename,
                    chunkText.length() > 50 ? chunkText.substring(0, 50) : chunkText);
        }

        log.info("Ingestion complete — {} chunks saved for '{}'", chunks.size(), filename);
        return chunks.size();
    }
}
