package com.raglab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity representing a single text chunk extracted from an ingested PDF document.
 *
 * <p>Each row stores one chunk of text along with its vector embedding serialised
 * as a comma-separated string of floats.  The embedding column is kept as TEXT so
 * that it can be cast to the pgvector {@code vector} type at query time via a native
 * SQL expression such as {@code ('[' || embedding || ']')::vector}.</p>
 */
@Entity
@Table(name = "doc_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocChunk {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original PDF filename from which this chunk was extracted. */
    @Column(name = "filename")
    private String filename;

    /** Zero-based position of this chunk within the source document. */
    @Column(name = "chunk_index")
    private int chunkIndex;

    /** Raw text content of this chunk (up to ~500 characters). */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Comma-separated float values representing the embedding vector produced by Ollama.
     * Example: {@code "0.123,0.456,-0.789,..."}
     */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;
}
