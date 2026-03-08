package com.org.parser.service;

import com.org.parser.entity.DocumentRecord;
import com.org.parser.exception.DocumentNotFoundException;
import com.org.parser.exception.ParsingException;
import com.org.parser.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingService {

    private static final int CHUNK_SIZE = 2000;
    private final AutoDetectParser autoParser;
    private final DocumentRepository repository;
    private final PerformanceTrackingService performanceTracking;

    @Async("parsingTaskExecutor")
    @Transactional
    public void processDocument(Long docId, byte[] fileData) {
        long startTime = System.currentTimeMillis();
        long dbTime = 0;
        long parsingTime = 0;
        long tikaParseTime = 0;
        int chunksCreated = 0;
        int metadataKeysExtracted = 0;

        log.info("Starting parsing for document ID: {}, file size: {} bytes", docId, fileData.length);

        long dbStart = System.currentTimeMillis();
        updateStatus(docId, "PROCESSING", null);
        dbTime += (System.currentTimeMillis() - dbStart);

        try {
            // Setup Recursive Parsing for nested files/attachments
            long parseStart = System.currentTimeMillis();
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(
                    BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                    -1  // No limit on content length
                )
            );

            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(autoParser);
            Metadata containerMetadata = new Metadata();
            ParseContext context = new ParseContext();

            // Parse the document
            try (InputStream stream = new ByteArrayInputStream(fileData)) {
                long tikaStart = System.currentTimeMillis();
                wrapper.parse(stream, handler, containerMetadata, context);
                tikaParseTime = System.currentTimeMillis() - tikaStart;

                List<Metadata> metadataList = handler.getMetadataList();

                // Extract and flatten text content and metadata
                List<String> chunks = new ArrayList<>();
                Map<String, String> mergedMetadata = new LinkedHashMap<>();

                for (int i = 0; i < metadataList.size(); i++) {
                    Metadata m = metadataList.get(i);

                    // Extract text content
                    String content = m.get("X-TIKA:content");
                    if (content != null && !content.trim().isEmpty()) {
                        chunks.addAll(splitIntoChunks(content.trim(), CHUNK_SIZE));
                    }

                    // Collect metadata from all embedded resources
                    for (String name : m.names()) {
                        String value = m.get(name);
                        if (value != null && !value.trim().isEmpty()) {
                            // Prefix embedded resource metadata
                            String key = (i > 0) ? "embedded_" + i + "_" + name : name;
                            mergedMetadata.putIfAbsent(key, value);
                        }
                    }
                }

                chunksCreated = chunks.size();
                metadataKeysExtracted = mergedMetadata.size();
                parsingTime = System.currentTimeMillis() - parseStart;

                // Auto-detect classification from metadata
                String classification = detectClassification(mergedMetadata);

                // Save results
                dbStart = System.currentTimeMillis();
                saveResults(docId, mergedMetadata, chunks, classification);
                dbTime += (System.currentTimeMillis() - dbStart);

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("Successfully parsed document ID: {} with {} chunks in {}ms", docId, chunks.size(), totalTime);

                // Record performance metrics
                performanceTracking.recordParsingMetrics(
                    docId, (long) fileData.length, totalTime, dbTime, parsingTime,
                    tikaParseTime, chunksCreated, metadataKeysExtracted,
                    "SUCCESS", null
                );
            }
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("Failed to parse document ID: {} after {}ms", docId, totalTime, e);

            dbStart = System.currentTimeMillis();
            updateStatus(docId, "FAILED", e.getMessage());
            dbTime += (System.currentTimeMillis() - dbStart);

            // Record failure metrics
            performanceTracking.recordParsingMetrics(
                docId, (long) fileData.length, totalTime, dbTime, parsingTime,
                tikaParseTime, chunksCreated, metadataKeysExtracted,
                "FAILED", e.getMessage()
            );

            throw new ParsingException("Parsing failed for document ID: " + docId, e);
        }
    }

    /**
     * Splits text into manageable chunks for JSONB storage
     * Chunks at 2000 characters to balance granularity and storage efficiency
     */
    private List<String> splitIntoChunks(String text, int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            result.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return result;
    }

    /**
     * Auto-detects document classification from metadata headers
     */
    private String detectClassification(Map<String, String> metadata) {
        // Check common classification headers
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().toLowerCase();

            if (key.contains("classification") || key.contains("sensitivity")) {
                if (value.contains("confidential")) return "Confidential";
                if (value.contains("restricted")) return "Restricted";
                if (value.contains("internal")) return "Internal";
            }
        }
        return "Unclassified";
    }

    /**
     * Updates document status
     */
    @Transactional
    protected void updateStatus(Long docId, String status, String error) {
        DocumentRecord doc = repository.findById(docId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + docId));

        doc.setStatus(status);
        doc.setErrorLog(error);
        doc.setUpdatedAt(Instant.now());
        repository.save(doc);
    }

    /**
     * Saves parsing results to the database
     */
    @Transactional
    protected void saveResults(Long docId, Map<String, String> metadata,
                              List<String> chunks, String classification) {
        DocumentRecord doc = repository.findById(docId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + docId));

        doc.setMetadata(metadata);
        doc.setParsedContent(chunks);
        doc.setClassification(classification);
        doc.setStatus("COMPLETED");
        doc.setUpdatedAt(Instant.now());
        repository.save(doc);
    }
}
