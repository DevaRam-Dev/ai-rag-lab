package com.raglab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Service responsible for generating dense vector embeddings from text using the Ollama API.
 *
 * <p>Calls the Ollama {@code /api/embeddings} endpoint with the {@code nomic-embed-text} model
 * to produce a float array for any given text string.  The float array can then be serialised
 * to a comma-separated string suitable for storage in the {@code doc_chunks.embedding} TEXT
 * column, or compared with stored embeddings at query time using pgvector's {@code <=>}
 * cosine-distance operator.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.embed-model}")
    private String embedModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Lifecycle hook that logs a build marker at service startup.
     */
    @PostConstruct
    public void init() {
        log.info(box("EmbeddingService initialized and ready")
            + lbl("Layer",   "SERVICE → EmbeddingService")
            + lbl("ANALOGY", "Embedding model interface armed and ready"));
    }

    /**
     * Calls the Ollama {@code /api/embeddings} endpoint and returns the embedding as a float array.
     *
     * <p>Builds a JSON request body with {@code model} and {@code prompt} fields, sends a
     * synchronous POST via Java's built-in {@link HttpClient}, then parses the JSON response
     * to extract the {@code embedding} array field.</p>
     *
     * @param text the input text to embed
     * @return float array representing the dense vector embedding
     * @throws IOException          if the HTTP request or JSON parsing fails
     * @throws InterruptedException if the HTTP thread is interrupted
     */
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("model", embedModel, "prompt", text)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.info(box("[OLLAMA] PROMPT SENT")
            + lbl("Layer",    "SERVICE → EmbeddingService")
            + lbl("Model",    embedModel)
            + lbl("Endpoint", "/api/embeddings")
            + lbl("Input",    text.length() + " chars"));

        long embedStart = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long embedDurationMs = System.currentTimeMillis() - embedStart;

        log.info(box("[OLLAMA] RESPONSE RECEIVED")
            + lbl("Layer",    "SERVICE → EmbeddingService")
            + lbl("Status",   response.statusCode())
            + lbl("Body",     response.body().length() + " chars")
            + lbl("Duration", embedDurationMs + "ms"));

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embeddingNode = root.get("embedding");

        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }

        StringBuilder preview = new StringBuilder();
        for (int p = 0; p < Math.min(5, embedding.length); p++) {
            if (p > 0) preview.append(", ");
            preview.append(String.format("%.4f", embedding[p]));
        }
        log.info("[SERVICE → EmbeddingService] OUTPUT: vectorDimensions={} | preview=[{}]...",
                embedding.length, preview);
        return embedding;
    }

    /**
     * Serialises a float array to a comma-separated string for TEXT column storage.
     *
     * <p>Example output: {@code "0.123,0.456,-0.789,..."}
     * The stored value is later wrapped in square brackets and cast to {@code vector} type
     * inside the native pgvector similarity query.</p>
     *
     * @param embedding float array produced by {@link #generateEmbedding(String)}
     * @return comma-separated string representation of the embedding values
     */
    public String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    /**
     * Convenience method that generates an embedding and immediately converts it to
     * the comma-separated string format used for database storage.
     *
     * @param text the input text to embed
     * @return comma-separated embedding string
     * @throws IOException          if the HTTP request or JSON parsing fails
     * @throws InterruptedException if the HTTP thread is interrupted
     */
    public String generateEmbeddingAsString(String text) throws IOException, InterruptedException {
        float[] embedding = generateEmbedding(text);
        return embeddingToString(embedding);
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
