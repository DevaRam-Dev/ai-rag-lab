package com.raglab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.dto.QueryResponse;
import com.raglab.entity.DocChunk;
import com.raglab.repository.DocChunkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for the RAG query pipeline: embed question → retrieve chunks → generate answer.
 *
 * <p>Accepts a natural-language question, converts it to a vector embedding using
 * {@link EmbeddingService}, performs a cosine-similarity search against the stored
 * {@link DocChunk} embeddings via {@link DocChunkRepository}, assembles a context-rich
 * prompt, and calls the Ollama {@code /api/generate} endpoint to produce the final answer.
 * The Ollama response stream is read line by line and all {@code response} fields are
 * concatenated into a single answer string.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private static final int TOP_K = 3;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.llm-model}")
    private String llmModel;

    private final EmbeddingService    embeddingService;
    private final DocChunkRepository  docChunkRepository;
    private final ObjectMapper        objectMapper = new ObjectMapper();
    private final HttpClient          httpClient   = HttpClient.newHttpClient();

    /**
     * Lifecycle hook that logs a build marker at service startup.
     */
    @PostConstruct
    public void init() {
        log.info("=== QueryService initialized === {}", LocalDateTime.now());
    }

    /**
     * Executes the full RAG query pipeline for the supplied question.
     *
     * <p>Steps:
     * <ol>
     *   <li>Embeds the question string using {@link EmbeddingService}.</li>
     *   <li>Runs a pgvector cosine-similarity search via the repository to fetch the top
     *       {@value #TOP_K} most relevant {@link DocChunk} records.</li>
     *   <li>Builds a context-augmented prompt that prepends the retrieved chunks before the
     *       question so the LLM has grounding information.</li>
     *   <li>Calls the Ollama {@code /api/generate} endpoint and streams the response.</li>
     *   <li>Concatenates all streamed {@code response} tokens into the final answer string.</li>
     * </ol>
     * </p>
     *
     * @param question the natural-language question from the user
     * @return a {@link QueryResponse} containing the generated answer and the source chunk texts
     * @throws IOException          if any HTTP call or JSON parsing fails
     * @throws InterruptedException if an HTTP call is interrupted
     */
    public QueryResponse answer(String question) throws IOException, InterruptedException {
        log.info("Query received: \"{}\"", question);

        String queryEmbedding = embeddingService.generateEmbeddingAsString(question);
        List<DocChunk> topChunks = docChunkRepository.findTopChunksByEmbedding(queryEmbedding, TOP_K);
        log.info("Retrieved {} chunks for similarity search", topChunks.size());

        List<String> sourceTexts = topChunks.stream()
                .map(DocChunk::getContent)
                .collect(Collectors.toList());

        String prompt = buildPrompt(sourceTexts, question);
        String answer = callOllamaGenerate(prompt);

        log.info("Answer generated — length: {} characters", answer.length());
        return QueryResponse.builder()
                .answer(answer)
                .sourceChunks(sourceTexts)
                .build();
    }

    /**
     * Assembles the context-augmented prompt string that is sent to the LLM.
     *
     * <p>Each retrieved chunk is included on its own numbered line under a "Context:" header.
     * The user's question follows at the end so the model receives grounding context first.</p>
     *
     * @param contextChunks ordered list of retrieved text chunks
     * @param question      the original user question
     * @return fully assembled prompt string
     */
    private String buildPrompt(List<String> contextChunks, String question) {
        StringBuilder sb = new StringBuilder("Answer based on context:\n\n");
        for (int i = 0; i < contextChunks.size(); i++) {
            sb.append("--- Chunk ").append(i + 1).append(" ---\n");
            sb.append(contextChunks.get(i)).append("\n\n");
        }
        sb.append("Question: ").append(question);
        return sb.toString();
    }

    /**
     * Sends the assembled prompt to the Ollama {@code /api/generate} endpoint and collects
     * the streamed response.
     *
     * <p>Ollama streams the answer as a series of newline-delimited JSON objects.  Each object
     * has a {@code response} field containing the next token and a {@code done} boolean flag.
     * This method reads all lines, parses each JSON object, and concatenates the {@code response}
     * fields until {@code done} is {@code true}.</p>
     *
     * @param prompt the full context-augmented prompt
     * @return the complete LLM answer as a single string
     * @throws IOException          if the HTTP call or JSON parsing fails
     * @throws InterruptedException if the HTTP call is interrupted
     */
    private String callOllamaGenerate(String prompt) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(
                Map.of("model", llmModel, "prompt", prompt, "stream", true)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return parseStreamedResponse(response.body());
    }

    /**
     * Parses the newline-delimited JSON stream returned by {@code /api/generate} and concatenates
     * all token fragments into a complete answer string.
     *
     * <p>Each line of the Ollama stream is a JSON object like:
     * {@code {"model":"...","response":"token","done":false}}.
     * The last line has {@code "done":true} and an empty {@code response}.  This method
     * accumulates every {@code response} value until a line with {@code "done":true} is seen.</p>
     *
     * @param rawBody the raw multi-line response body from Ollama
     * @return concatenated answer string
     * @throws IOException if any individual line cannot be parsed as JSON
     */
    private String parseStreamedResponse(String rawBody) throws IOException {
        StringBuilder answer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(rawBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                if (node.has("response")) {
                    answer.append(node.get("response").asText());
                }
                if (node.has("done") && node.get("done").asBoolean()) {
                    break;
                }
            }
        }

        return answer.toString();
    }
}
