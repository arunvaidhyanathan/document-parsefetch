package com.org.parser.service;

import com.org.parser.entity.DocumentRecord;
import com.org.parser.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSearchService {

    private final DocumentRepository repository;
    private final PerformanceTrackingService performanceTracking;

    /**
     * Searches for exact phrases within the parsed JSONB content array.
     * Uses GIN index with jsonb_path_ops for optimal performance.
     */
    public List<DocumentRecord> searchByPhrase(String phrase) {
        long startTime = System.currentTimeMillis();
        List<DocumentRecord> results = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            // Wrap phrase in JSON array format for @> operator
            String jsonPhrase = "[\"" + escapeJson(phrase) + "\"]";
            log.debug("Executing phrase search with: {}", jsonPhrase);

            long dbStart = System.currentTimeMillis();
            results = repository.searchByContentPhrase(jsonPhrase);
            long dbTime = System.currentTimeMillis() - dbStart;
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                phrase, "SEARCH_PHRASE", totalTime, dbTime,
                results.size(), status, null
            );

            return results;
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                phrase, "SEARCH_PHRASE", totalTime, 0,
                0, status, errorMessage
            );
            throw e;
        }
    }

    /**
     * Performs keyword search using PostgreSQL Full-Text Search.
     * Supports word stemming and language-aware matching.
     */
    public List<DocumentRecord> searchByKeywords(String keywords) {
        long startTime = System.currentTimeMillis();
        List<DocumentRecord> results = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            // Convert "word1 word2" to "word1 & word2" for tsquery
            String tsQuery = keywords.trim().replaceAll("\\s+", " & ");
            log.debug("Executing FTS search with: {}", tsQuery);

            long dbStart = System.currentTimeMillis();
            results = repository.searchByKeyword(tsQuery);
            long dbTime = System.currentTimeMillis() - dbStart;
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                keywords, "SEARCH_FTS", totalTime, dbTime,
                results.size(), status, null
            );

            return results;
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                keywords, "SEARCH_FTS", totalTime, 0,
                0, status, errorMessage
            );
            throw e;
        }
    }

    /**
     * Searches metadata for specific key-value pairs.
     */
    public List<DocumentRecord> searchMetadata(String key, String value) {
        long startTime = System.currentTimeMillis();
        List<DocumentRecord> results = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            // Construct JSON object: {"key": "value"}
            String jsonMeta = String.format("{\"%s\": \"%s\"}",
                                            escapeJson(key),
                                            escapeJson(value));
            log.debug("Executing metadata search with: {}", jsonMeta);

            long dbStart = System.currentTimeMillis();
            results = repository.searchByMetadata(jsonMeta);
            long dbTime = System.currentTimeMillis() - dbStart;
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                key + ":" + value, "SEARCH_METADATA", totalTime, dbTime,
                results.size(), status, null
            );

            return results;
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            long totalTime = System.currentTimeMillis() - startTime;

            performanceTracking.recordSearchMetrics(
                key + ":" + value, "SEARCH_METADATA", totalTime, 0,
                0, status, errorMessage
            );
            throw e;
        }
    }

    /**
     * Escapes special JSON characters
     */
    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                    .replace("\\", "\\\\");
    }
}
