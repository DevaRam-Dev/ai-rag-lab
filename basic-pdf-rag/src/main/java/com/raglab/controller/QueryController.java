package com.raglab.controller;

import com.raglab.dto.QueryRequest;
import com.raglab.dto.QueryResponse;
import com.raglab.repository.DocChunkRepository;
import com.raglab.service.QueryService;
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
        log.info("POST /api/query — question: \"{}\"", request.getQuestion());

        try {
            QueryResponse response = queryService.answer(request.getQuestion());
            log.info("POST /api/query — answer generated successfully");
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
        log.info("GET /api/documents — fetching distinct filenames");
        List<String> filenames = docChunkRepository.findDistinctFilenames();
        log.info("GET /api/documents — returning {} documents", filenames.size());
        return ResponseEntity.ok(filenames);
    }
}
