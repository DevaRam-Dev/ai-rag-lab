package com.raglab.controller;

import com.raglab.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller that exposes the PDF ingestion endpoint.
 *
 * <p>Receives multipart PDF uploads via {@code POST /api/ingest}, delegates processing
 * to {@link IngestionService}, and returns a JSON summary of the ingestion result
 * (filename and total chunk count).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * Accepts a multipart PDF upload, runs the full ingestion pipeline, and returns
     * a confirmation message with the number of chunks that were saved.
     *
     * <p>The {@code file} form field must contain a valid PDF binary. Errors during
     * ingestion (e.g. malformed PDF or Ollama unavailable) are returned as HTTP 500
     * with an error message so the frontend can display them.</p>
     *
     * @param file the uploaded PDF file (multipart form field name: {@code file})
     * @return HTTP 200 with JSON body {@code {"message":"Ingested N chunks from filename.pdf"}}
     *         or HTTP 500 with an error message if ingestion fails
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingest(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        log.info("POST /api/ingest — received file: '{}', size: {} bytes", filename, file.getSize());

        try {
            int chunkCount = ingestionService.ingestPdf(file);
            String message = "Ingested " + chunkCount + " chunks from " + filename;
            log.info("POST /api/ingest — completed: {}", message);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            log.error("POST /api/ingest — ingestion failed for '{}': {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Ingestion failed: " + e.getMessage()));
        }
    }
}
