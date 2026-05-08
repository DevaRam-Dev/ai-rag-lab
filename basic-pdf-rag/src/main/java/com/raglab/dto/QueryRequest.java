package com.raglab.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO for the {@code POST /api/query} endpoint.
 *
 * <p>Carries the natural-language question submitted by the user through the chat panel.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    /** The natural-language question to be answered by the RAG pipeline. */
    private String question;
}
