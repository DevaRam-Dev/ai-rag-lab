package com.raglab.controller;

import com.raglab.dto.QueryRequest;
import com.raglab.dto.QueryResponse;
import com.raglab.repository.DocChunkRepository;
import com.raglab.service.QueryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the RAG query and document-listing endpoints.
 *
 * <p>Handles two routes:
 * <ul>
 *   <li>{@code POST /api/query} — accepts a JSON question, runs the RAG pipeline, and
 *       returns the LLM answer together with the source chunks that were used as context.</li>
 *   <li>{@code GET /api/documents} — returns the list of distinct filenames currently
 *       stored in the database, allowing the UI to show which PDFs have been ingested.</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QueryController {

    private final QueryService        queryService;
    private final DocChunkRepository  docChunkRepository;

    @PostConstruct
    public void init() {
        log.info(box("QueryController initialized and ready")
            + lbl("Layer",   "API → QueryController")
            + lbl("ANALOGY", "Question-answering desk opened for RAG queries"));
    }

    /**
     * Runs the full RAG pipeline for the submitted question.
     *
     * <p>Parses the JSON body into a {@link QueryRequest}, delegates to
     * {@link QueryService#answer(String)}, and returns a {@link QueryResponse}
     * containing both the generated answer and the retrieved source chunk texts.</p>
     *
     * @param request JSON body containing the {@code question} field
     * @return HTTP 200 with a {@link QueryResponse} body, or HTTP 500 on failure
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        long start = System.currentTimeMillis();
        log.info(heavyBox("REQUEST START : POST /api/query")
            + lbl("Layer",       "API → QueryController")
            + lbl("Input",       "question=\"" + request.getQuestion() + "\"")
            + lbl("Description", "Run RAG pipeline: embed → retrieve → generate answer"));

        try {
            QueryResponse response = queryService.answer(request.getQuestion());
            log.info(heavyBox("REQUEST END : POST /api/query — 200 OK")
                + lbl("Output",   "answerLength=" + response.getAnswer().length() + " chars, sourceChunks=" + response.getSourceChunks().size())
                + lbl("Duration", (System.currentTimeMillis() - start) + "ms"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("POST /api/query — query failed: {}", e.getMessage(), e);
            QueryResponse errorResponse = QueryResponse.builder()
                    .answer("Query failed: " + e.getMessage())
                    .sourceChunks(List.of())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Returns the list of distinct PDF filenames that have been ingested into the database.
     *
     * <p>Used by the frontend Upload Panel to populate the "Uploaded Documents" list so the
     * user can see which PDFs are available for querying without inspecting the database.</p>
     *
     * @return HTTP 200 with a JSON array of filename strings
     */
    @GetMapping("/documents")
    public ResponseEntity<List<String>> listDocuments() {
        long start = System.currentTimeMillis();
        log.info(heavyBox("REQUEST START : GET /api/documents")
            + lbl("Layer",       "API → QueryController")
            + lbl("Input",       "none")
            + lbl("Description", "Return list of distinct ingested PDF filenames"));

        List<String> filenames = docChunkRepository.findDistinctFilenames();

        log.info(heavyBox("REQUEST END : GET /api/documents — 200 OK")
            + lbl("Output",   filenames.size() + " document(s) returned")
            + lbl("Duration", (System.currentTimeMillis() - start) + "ms"));
        return ResponseEntity.ok(filenames);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Log formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static final String HEAVY_BAR = "█".repeat(78);

    private static String heavyBox(String title) {
        return "\n" + HEAVY_BAR + "\n█  " + String.format("%-74s", title) + "█\n" + HEAVY_BAR;
    }

    private static final String BOX_H = "═".repeat(76);

    private static String box(String title) {
        return "\n╔" + BOX_H + "╗\n║  " + String.format("%-74s", title) + "║\n╚" + BOX_H + "╝";
    }

    private static String lbl(String label, Object value) {
        return "\n   " + String.format("%-11s", label) + " : " + value;
    }
}
