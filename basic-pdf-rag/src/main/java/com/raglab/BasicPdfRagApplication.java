package com.raglab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Basic PDF RAG application.
 *
 * <p>This Spring Boot application implements a Retrieval-Augmented Generation (RAG) pipeline
 * that ingests PDF documents, generates embeddings via Ollama, stores them in PostgreSQL
 * with pgvector support, and answers natural-language questions using an Ollama LLM.</p>
 */
@SpringBootApplication
public class BasicPdfRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicPdfRagApplication.class, args);
    }
}
