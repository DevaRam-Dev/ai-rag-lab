package com.raglab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outbound DTO returned by the {@code POST /api/query} endpoint.
 *
 * <p>Contains the LLM-generated answer along with the source text chunks that were
 * retrieved from the vector store and used as context for that answer.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    /** The answer produced by the Ollama LLM based on the retrieved context. */
    private String answer;

    /** The raw text chunks that were retrieved and injected into the LLM prompt. */
    private List<String> sourceChunks;
}
