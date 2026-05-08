package com.raglab.repository;

import com.raglab.entity.DocChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DocChunk} entities.
 *
 * <p>Extends the standard CRUD operations with a native pgvector cosine-similarity
 * search query.  The {@code embedding} column is stored as TEXT (comma-separated floats)
 * and is cast to the pgvector {@code vector} type at query time so that the {@code <=>}
 * cosine-distance operator can be applied.</p>
 *
 * <p><strong>Prerequisite:</strong> the {@code vector} extension must be installed in
 * PostgreSQL ({@code CREATE EXTENSION IF NOT EXISTS vector;}) before this query can run.</p>
 */
@Repository
public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    /**
     * Finds the {@code limit} most semantically similar chunks to the supplied query embedding
     * using pgvector's cosine-distance operator ({@code <=>}).
     *
     * <p>The TEXT embeddings are wrapped in square brackets and cast to {@code vector} so that
     * pgvector can interpret them.  Both the stored embedding and the query embedding must use
     * the same number of dimensions (768 for {@code nomic-embed-text}).</p>
     *
     * @param queryEmbedding comma-separated float string representing the query vector,
     *                       e.g. {@code "0.12,0.45,-0.78,..."}
     * @param limit          maximum number of chunks to return
     * @return ordered list of the closest {@link DocChunk} records (ascending distance)
     */
    @Query(
        value = "SELECT * FROM doc_chunks " +
                "ORDER BY ('[' || embedding || ']')::vector <=> ('[' || :queryEmbedding || ']')::vector " +
                "LIMIT :limit",
        nativeQuery = true
    )
    List<DocChunk> findTopChunksByEmbedding(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    /**
     * Returns a deduplicated list of all filenames that have been ingested into the database.
     * Used by the {@code GET /api/documents} endpoint to populate the uploaded-docs list in the UI.
     *
     * @return distinct filename strings
     */
    @Query("SELECT DISTINCT d.filename FROM DocChunk d")
    List<String> findDistinctFilenames();
}
