package com.raglab.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for the first stage of the RAG ingestion pipeline: text extraction
 * and chunking.
 *
 * <p>Accepts a PDF {@link InputStream}, extracts the full text using Apache PDFBox, then
 * splits the text into overlapping chunks of a fixed character size so that nearby context
 * is not lost at chunk boundaries.  The resulting list of string chunks is handed to
 * {@link EmbeddingService} for vectorisation.</p>
 */
@Slf4j
@Service
public class PdfChunkingService {

    private static final int CHUNK_SIZE    = 500;
    private static final int CHUNK_OVERLAP = 50;

    /**
     * Lifecycle hook that logs a build marker so the startup log clearly shows
     * when this service becomes active.
     */
    @PostConstruct
    public void init() {
        log.info(box("PdfChunkingService initialized and ready")
            + lbl("Layer",   "SERVICE → PdfChunkingService")
            + lbl("Config",  "chunkSize=" + CHUNK_SIZE + ", overlap=" + CHUNK_OVERLAP)
            + lbl("ANALOGY", "Document shredder configured and standing by"));
    }

    /**
     * Extracts all text from the supplied PDF input stream using Apache PDFBox.
     *
     * <p>The stream is loaded into a {@link PDDocument} and {@link PDFTextStripper} is used
     * to retrieve the full document text.  The document is closed automatically via
     * try-with-resources to prevent resource leaks.</p>
     *
     * @param pdfStream raw bytes of the PDF file as an {@link InputStream}
     * @return the full text content of the PDF as a single string
     * @throws IOException if PDFBox cannot parse the supplied stream
     */
    public String extractTextFromPdf(InputStream pdfStream) throws IOException {
        log.info(box("extractTextFromPdf — loading PDF via PDFBox")
            + lbl("Layer",       "SERVICE → PdfChunkingService")
            + lbl("Description", "Extract full text from PDF input stream"));
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("[SERVICE → PdfChunkingService] extracted | chars={}", text.length());
            return text;
        }
    }

    /**
     * Splits a long text string into overlapping fixed-size chunks.
     *
     * <p>Each chunk is {@value #CHUNK_SIZE} characters long.  The sliding window advances by
     * ({@value #CHUNK_SIZE} - {@value #CHUNK_OVERLAP}) characters per step, so consecutive
     * chunks share a {@value #CHUNK_OVERLAP}-character overlap.  This preserves context at
     * boundaries and reduces the chance of splitting a sentence across two chunks without any
     * shared context.</p>
     *
     * @param text the full document text to be split
     * @return ordered list of text chunks; each chunk is at most {@value #CHUNK_SIZE} characters
     */
    public List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int step = CHUNK_SIZE - CHUNK_OVERLAP;
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += step;
        }

        log.info("[SERVICE → PdfChunkingService] chunkText | chunks={} | size={} | overlap={}",
                chunks.size(), CHUNK_SIZE, CHUNK_OVERLAP);
        return chunks;
    }

    /**
     * Convenience method that combines PDF text extraction and chunking into a single call.
     *
     * <p>Delegates to {@link #extractTextFromPdf(InputStream)} and then {@link #chunkText(String)}.</p>
     *
     * @param pdfStream raw bytes of the PDF file as an {@link InputStream}
     * @return ordered list of text chunks ready for embedding
     * @throws IOException if PDFBox cannot parse the supplied stream
     */
    public List<String> extractAndChunk(InputStream pdfStream, String filename) throws IOException {
        int available = pdfStream.available();
        log.info(box("extractAndChunk — extract text and split into chunks")
            + lbl("Layer",       "SERVICE → PdfChunkingService")
            + lbl("Input",       "filename=" + filename + ", streamSize=" + available + " bytes")
            + lbl("Description", "Load PDF → strip text → sliding-window chunk"));

        String fullText;
        int numPages;
        try (PDDocument document = PDDocument.load(pdfStream)) {
            numPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            fullText = stripper.getText(document);
        }

        List<String> chunks = chunkText(fullText);
        log.info("[SERVICE → PdfChunkingService] OUTPUT: filename={} | pages={} | chunks={} | chunkSize={} | overlap={}",
                filename, numPages, chunks.size(), CHUNK_SIZE, CHUNK_OVERLAP);
        return chunks;
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
