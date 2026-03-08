# Document Parsing Microservice Implementation Guide

This guide provides a comprehensive blueprint for building a high-performance, asynchronous document parsing microservice using Spring Boot 3.2.x, Apache Tika, and PostgreSQL.

## Table of Contents
1. [Overview & Architecture](#1-overview--architecture)
2. [Prerequisites & Dependencies](#2-prerequisites--dependencies)
3. [Database Setup](#3-database-setup)
4. [Core Implementation](#4-core-implementation)
5. [REST API Layer](#5-rest-api-layer)
6. [Search Implementation](#6-search-implementation)
7. [Error Handling & Resilience](#7-error-handling--resilience)
8. [API Documentation (Swagger)](#8-api-documentation-swagger)
9. [Testing & Verification](#9-testing--verification)
10. [Observability & Monitoring](#10-observability--monitoring)
11. [Security Considerations](#11-security-considerations)
12. [Deployment Checklist](#12-deployment-checklist)

---

## 1. Overview & Architecture

This service follows a clean separation of concerns, ensuring the heavy lifting of document extraction does not block the API or the database. It supports:

- **Asynchronous Processing**: Non-blocking document parsing with status tracking
- **Recursive Parsing**: Extracts content from nested files (ZIP, attachments, embedded documents)
- **OCR Support**: Optional Tesseract integration for image-based documents
- **High-Performance Search**: PostgreSQL JSONB with GIN indexing and Full-Text Search

### Project Structure

```text
document-parsing-service/
├── src/main/java/com/org/parser/
│   ├── config/             # Tika, Async, and OCR Configuration
│   ├── controller/         # REST Endpoints (Upload, Status, Search)
│   ├── entity/             # JPA Entities (DocumentRecord)
│   ├── repository/         # PostgreSQL JSONB & FTS Queries
│   ├── service/            # Business Logic & Parsing Engine
│   ├── model/              # DTOs and Enums
│   └── exception/          # Custom Exceptions & Error Handling
├── src/main/resources/
│   ├── db/changelog/       # Liquibase SQL Scripts
│   └── application.yml     # Configuration
└── pom.xml                 # Dependencies
```

---

## 2. Prerequisites & Dependencies

### Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17+ | LTS recommended |
| Spring Boot | 3.2.x | Latest stable |
| Apache Tika | 2.9.x | Core + Parsers |
| PostgreSQL | 14+ | JSONB & GIN support required |
| Tesseract OCR | 5.x | Optional, for image parsing |

### Maven Dependencies (pom.xml)

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
    <tika.version>2.9.1</tika.version>
</properties>

<dependencies>
    <!-- Spring Boot Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Liquibase for Schema Migration -->
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>

    <!-- Apache Tika -->
    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-core</artifactId>
        <version>${tika.version}</version>
    </dependency>

    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-parsers-standard-package</artifactId>
        <version>${tika.version}</version>
    </dependency>

    <!-- OpenAPI / Swagger -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.3.0</version>
    </dependency>

    <!-- Lombok (Optional, for cleaner code) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 3. Database Setup

### Connection Configuration

Add the following to your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-host:5432/workflow?sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Let Liquibase handle schema
    properties:
      hibernate:
        default_schema: document_management
        jdbc:
          lob:
            non_contextual_creation: true

  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    default-schema: document_management
```

### Schema Initialization

Create the schema manually before running Liquibase:

```sql
CREATE SCHEMA IF NOT EXISTS document_management;
```

### Liquibase Changelog Master

**src/main/resources/db/changelog/db.changelog-master.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <include file="db/changelog/001-initial-schema.sql"/>
</databaseChangeLog>
```

### Database Schema

**src/main/resources/db/changelog/001-initial-schema.sql**

```sql
-- Document ID Sequence
CREATE SEQUENCE IF NOT EXISTS document_management.doc_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Main Document Records Table
CREATE TABLE document_management.document_records (
    id BIGINT PRIMARY KEY DEFAULT nextval('document_management.doc_id_seq'),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    status VARCHAR(20) NOT NULL,           -- PENDING, PROCESSING, COMPLETED, FAILED
    classification VARCHAR(50),            -- Internal, Restricted, Confidential
    metadata JSONB,                        -- Tika extracted properties
    parsed_content JSONB,                  -- Array of text chunks
    error_log TEXT,                        -- Detailed failure reason
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- GIN Indexes for JSONB Performance
-- Using jsonb_path_ops for optimal containment queries (@>)
CREATE INDEX idx_doc_metadata_gin
    ON document_management.document_records
    USING GIN (metadata jsonb_path_ops);

CREATE INDEX idx_doc_content_gin
    ON document_management.document_records
    USING GIN (parsed_content jsonb_path_ops);

-- Full-Text Search Index for advanced keyword matching
CREATE INDEX idx_doc_fts_content
    ON document_management.document_records
    USING GIN (to_tsvector('english', parsed_content::text));

-- B-Tree Indexes for Status Filtering
CREATE INDEX idx_doc_status
    ON document_management.document_records(status);

CREATE INDEX idx_doc_classification
    ON document_management.document_records(classification);

CREATE INDEX idx_doc_created_at
    ON document_management.document_records(created_at DESC);
```

**Key Design Decisions:**

- **Chunking Strategy**: Documents are split into 2000-character segments stored in `parsed_content` JSONB array to prevent single-record bloat and enable granular indexing
- **jsonb_path_ops**: Smaller and faster than standard GIN, optimized specifically for the `@>` containment operator
- **Separate FTS Index**: Enables word stemming and language-aware keyword search

---

## 4. Core Implementation

### A. JPA Entity

**DocumentRecord.java**

```java
package com.org.parser.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "document_records", schema = "document_management")
@Data
public class DocumentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "doc_seq")
    @SequenceGenerator(
        name = "doc_seq",
        sequenceName = "doc_id_seq",
        schema = "document_management",
        allocationSize = 1
    )
    private Long id;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 50)
    private String classification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_content", columnDefinition = "jsonb")
    private List<String> parsedContent;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### B. Repository Layer

**DocumentRepository.java**

```java
package com.org.parser.repository;

import com.org.parser.entity.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentRecord, Long> {

    /**
     * Metadata Containment Search
     * Matches exact key-value pairs in the metadata JSONB.
     * Example: '{"Classification": "Confidential"}'
     */
    @Query(value = "SELECT * FROM document_management.document_records " +
                   "WHERE metadata @> CAST(:jsonQuery AS jsonb)",
           nativeQuery = true)
    List<DocumentRecord> searchByMetadata(@Param("jsonQuery") String jsonQuery);

    /**
     * Content Phrase Search
     * Checks if a specific phrase exists within the parsed_content JSONB array.
     * Example: '["Confidential Information"]'
     */
    @Query(value = "SELECT * FROM document_management.document_records " +
                   "WHERE parsed_content @> CAST(:jsonPhrase AS jsonb)",
           nativeQuery = true)
    List<DocumentRecord> searchByContentPhrase(@Param("jsonPhrase") String jsonPhrase);

    /**
     * Full-Text Keyword Search
     * Uses PostgreSQL to_tsquery for word-based searching with stemming.
     * Example: 'report & draft'
     */
    @Query(value = "SELECT * FROM document_management.document_records " +
                   "WHERE to_tsvector('english', parsed_content::text) @@ to_tsquery('english', :tsQuery)",
           nativeQuery = true)
    List<DocumentRecord> searchByKeyword(@Param("tsQuery") String tsQuery);

    /**
     * Find documents by status
     */
    List<DocumentRecord> findByStatus(String status);
}
```

### C. Configuration Classes

**TikaConfig.java**

```java
package com.org.parser.config;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaConfig {

    @Bean
    public AutoDetectParser autoDetectParser() {
        return new AutoDetectParser();
    }

    @Bean
    public TesseractOCRConfig tesseractOCRConfig() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        // Tika will check for Tesseract binary automatically.
        // If not found, OCR is skipped gracefully.
        config.setLanguage("eng");
        config.setEnableImageProcessing(1);
        return config;
    }
}
```

**AsyncConfig.java**

```java
package com.org.parser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "parsingTaskExecutor")
    public Executor parsingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("parsing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

### D. Parsing Service

**ParsingService.java**

```java
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

    @Async("parsingTaskExecutor")
    @Transactional
    public void processDocument(Long docId, byte[] fileData) {
        log.info("Starting parsing for document ID: {}", docId);
        updateStatus(docId, "PROCESSING", null);

        try {
            // Setup Recursive Parsing for nested files/attachments
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
                wrapper.parse(stream, handler, containerMetadata, context);
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

                // Auto-detect classification from metadata
                String classification = detectClassification(mergedMetadata);

                // Save results
                saveResults(docId, mergedMetadata, chunks, classification);
                log.info("Successfully parsed document ID: {} with {} chunks", docId, chunks.size());

            }
        } catch (Exception e) {
            log.error("Failed to parse document ID: {}", docId, e);
            updateStatus(docId, "FAILED", e.getMessage());
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
```

---

## 5. REST API Layer

### Document Controller

**DocumentController.java**

```java
package com.org.parser.controller;

import com.org.parser.entity.DocumentRecord;
import com.org.parser.repository.DocumentRepository;
import com.org.parser.service.ParsingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/docs")
@Tag(name = "Document Ingestion", description = "Endpoints for uploading and tracking document parsing")
@RequiredArgsConstructor
public class DocumentController {

    private final ParsingService parsingService;
    private final DocumentRepository repository;

    @PostMapping("/upload")
    @Operation(summary = "Upload a document for asynchronous parsing")
    public ResponseEntity<Long> upload(
            @Parameter(description = "Document file (PDF, DOCX, PNG, etc.)")
            @RequestParam("file") MultipartFile file) throws IOException {

        DocumentRecord record = new DocumentRecord();
        record.setFilename(file.getOriginalFilename());
        record.setContentType(file.getContentType());
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        record = repository.save(record);

        // Process asynchronously
        parsingService.processDocument(record.getId(), file.getBytes());

        return ResponseEntity.accepted().body(record.getId());
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get document parsing status and results")
    public ResponseEntity<DocumentRecord> getStatus(
            @Parameter(description = "Document ID")
            @PathVariable Long id) {

        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full document record")
    public ResponseEntity<DocumentRecord> getDocument(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

---

## 6. Search Implementation

### Search Service

**DocumentSearchService.java**

```java
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

    /**
     * Searches for exact phrases within the parsed JSONB content array.
     * Uses GIN index with jsonb_path_ops for optimal performance.
     */
    public List<DocumentRecord> searchByPhrase(String phrase) {
        // Wrap phrase in JSON array format for @> operator
        String jsonPhrase = "[\"" + escapeJson(phrase) + "\"]";
        log.debug("Executing phrase search with: {}", jsonPhrase);
        return repository.searchByContentPhrase(jsonPhrase);
    }

    /**
     * Performs keyword search using PostgreSQL Full-Text Search.
     * Supports word stemming and language-aware matching.
     */
    public List<DocumentRecord> searchByKeywords(String keywords) {
        // Convert "word1 word2" to "word1 & word2" for tsquery
        String tsQuery = keywords.trim().replaceAll("\\s+", " & ");
        log.debug("Executing FTS search with: {}", tsQuery);
        return repository.searchByKeyword(tsQuery);
    }

    /**
     * Searches metadata for specific key-value pairs.
     */
    public List<DocumentRecord> searchMetadata(String key, String value) {
        // Construct JSON object: {"key": "value"}
        String jsonMeta = String.format("{\"%s\": \"%s\"}",
                                        escapeJson(key),
                                        escapeJson(value));
        log.debug("Executing metadata search with: {}", jsonMeta);
        return repository.searchByMetadata(jsonMeta);
    }

    /**
     * Escapes special JSON characters
     */
    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                    .replace("\\", "\\\\");
    }
}
```

### Search Controller

**SearchController.java**

```java
package com.org.parser.controller;

import com.org.parser.entity.DocumentRecord;
import com.org.parser.service.DocumentSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "JSONB and Full-Text Search endpoints")
@RequiredArgsConstructor
public class SearchController {

    private final DocumentSearchService searchService;

    @GetMapping("/content")
    @Operation(
        summary = "Search document content",
        description = "Search using exact phrase matching (GIN index) or keyword-based Full-Text Search"
    )
    public ResponseEntity<List<DocumentRecord>> searchContent(
            @Parameter(description = "The text or keywords to search for")
            @RequestParam String query,
            @Parameter(description = "True for exact phrase matching, False for FTS keyword search")
            @RequestParam(defaultValue = "false") boolean exact) {

        List<DocumentRecord> results = exact
            ? searchService.searchByPhrase(query)
            : searchService.searchByKeywords(query);

        return ResponseEntity.ok(results);
    }

    @GetMapping("/metadata")
    @Operation(summary = "Search by metadata key-value pairs")
    public ResponseEntity<List<DocumentRecord>> searchMetadata(
            @Parameter(description = "Metadata field name (e.g., Author, Classification)")
            @RequestParam String key,
            @Parameter(description = "Value to match")
            @RequestParam String value) {

        return ResponseEntity.ok(searchService.searchMetadata(key, value));
    }
}
```

### Search Strategy Summary

| Search Type | Postgres Operator | Requirement | Best For |
|-------------|------------------|-------------|----------|
| Metadata | `@>` | Exact Key/Value | Classifications, Authors, Dates |
| Phrase | `@>` | Exact String in Array | Specific sentences, IDs, compliance terms |
| Keyword | `@@` | Word Stems (FTS) | General search functionality, natural language queries |

---

## 7. Error Handling & Resilience

### Custom Exceptions

**DocumentNotFoundException.java**

```java
package com.org.parser.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}
```

**ParsingException.java**

```java
package com.org.parser.exception;

public class ParsingException extends RuntimeException {
    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Global Exception Handler

**GlobalExceptionHandler.java**

```java
package com.org.parser.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ParsingException.class)
    public ResponseEntity<Map<String, Object>> handleParsingException(ParsingException ex) {
        log.error("Parsing error occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Document parsing failed");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds maximum limit");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        return ResponseEntity.status(status).body(error);
    }
}
```

---

## 8. API Documentation (Swagger)

### Swagger Configuration

Add to `application.yml`:

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: alpha
    tags-sorter: alpha
  api-docs:
    path: /v3/api-docs
  packages-to-scan: com.org.parser.controller
```

### OpenAPI Specification

```yaml
openapi: 3.0.3
info:
  title: Document Parsing & Search API
  description: High-performance asynchronous document parsing using Apache Tika with PostgreSQL JSONB search
  version: 1.0.0
  contact:
    name: API Support
    email: support@example.com

servers:
  - url: http://localhost:8080/api/v1
    description: Local Development Server
  - url: https://api.example.com/api/v1
    description: Production Server

paths:
  /docs/upload:
    post:
      summary: Upload a document for parsing
      tags: [Document Ingestion]
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                file:
                  type: string
                  format: binary
      responses:
        '202':
          description: File accepted for processing
          content:
            application/json:
              schema:
                type: integer
                format: int64
                example: 101

  /docs/{id}/status:
    get:
      summary: Get parsing status and results
      tags: [Document Ingestion]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Document record found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DocumentRecord'
        '404':
          description: Document not found

  /search/content:
    get:
      summary: Search document content
      tags: [Search]
      parameters:
        - name: query
          in: query
          required: true
          schema:
            type: string
        - name: exact
          in: query
          schema:
            type: boolean
            default: false
      responses:
        '200':
          description: Matching documents
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/DocumentRecord'

  /search/metadata:
    get:
      summary: Search by metadata
      tags: [Search]
      parameters:
        - name: key
          in: query
          required: true
          schema:
            type: string
        - name: value
          in: query
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Matching documents
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/DocumentRecord'

components:
  schemas:
    DocumentRecord:
      type: object
      properties:
        id:
          type: integer
          format: int64
        filename:
          type: string
        contentType:
          type: string
        status:
          type: string
          enum: [PENDING, PROCESSING, COMPLETED, FAILED]
        classification:
          type: string
          enum: [Unclassified, Internal, Restricted, Confidential]
        metadata:
          type: object
          additionalProperties: true
        parsedContent:
          type: array
          items:
            type: string
        errorLog:
          type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time
```

Access Swagger UI at: `http://localhost:8080/swagger-ui.html`

---

## 9. Testing & Verification

### Manual Testing Guide

#### 1. Upload a Document

```bash
# Upload a PDF
curl -X POST http://localhost:8080/api/v1/docs/upload \
  -F "file=@sample.pdf" \
  -v

# Expected Response (HTTP 202):
# 101
```

#### 2. Check Parsing Status

```bash
# Poll status endpoint
curl http://localhost:8080/api/v1/docs/101

# Expected Response (PROCESSING):
{
  "id": 101,
  "filename": "sample.pdf",
  "status": "PROCESSING",
  "createdAt": "2024-01-15T10:30:00Z"
}

# Wait 5-10 seconds, then retry
# Expected Response (COMPLETED):
{
  "id": 101,
  "status": "COMPLETED",
  "classification": "Confidential",
  "parsedContent": ["chunk1...", "chunk2..."],
  "metadata": {
    "Content-Type": "application/pdf",
    "Author": "John Doe"
  }
}
```

#### 3. Test Recursive Parsing (Nested Files)

```bash
# Upload a ZIP containing multiple documents
curl -X POST http://localhost:8080/api/v1/docs/upload \
  -F "file=@archive.zip"

# Verify parsed_content contains text from all embedded files
# Check for metadata key: "embedded_1_X-TIKA:content"
```

#### 4. Exact Phrase Search

```bash
# Search for a specific phrase using GIN index
curl "http://localhost:8080/api/v1/search/content?query=Confidential%20Information&exact=true"

# Expected: Documents containing the exact phrase "Confidential Information"
```

#### 5. Keyword Search (Full-Text)

```bash
# Search using FTS with word stemming
curl "http://localhost:8080/api/v1/search/content?query=revenue%20report&exact=false"

# Expected: Documents containing "revenue" AND "report" (any word form)
```

#### 6. Metadata Search

```bash
# Find all Confidential documents
curl "http://localhost:8080/api/v1/search/metadata?key=Classification&value=Confidential"
```

### Performance Testing

#### Large File Handling

```bash
# Upload a 20MB+ PDF
curl -X POST http://localhost:8080/api/v1/docs/upload \
  -F "file=@large-report.pdf"

# Monitor async thread pool in logs
# Verify status transitions: PENDING → PROCESSING → COMPLETED
```

#### OCR Verification (If Tesseract Installed)

```bash
# Upload a scanned image or PDF
curl -X POST http://localhost:8080/api/v1/docs/upload \
  -F "file=@scanned-document.png"

# Check parsed_content for extracted text from image
```

### Automated Tests

**Example JUnit Test**

```java
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldUploadAndProcessDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Test content".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/docs/upload").file(file))
            .andExpect(status().isAccepted())
            .andReturn();

        Long docId = Long.parseLong(result.getResponse().getContentAsString());

        // Wait for async processing
        Thread.sleep(2000);

        mockMvc.perform(get("/api/v1/docs/" + docId + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
```

---

## 10. Observability & Monitoring

### Structured Logging

Add to `application.yml`:

```yaml
logging:
  level:
    com.org.parser: DEBUG
    org.apache.tika: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

### Metrics Configuration

**Application Properties**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Health Checks

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Expected Response:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

### Key Metrics to Monitor

- **Parsing Duration**: Average time to parse documents
- **Success Rate**: Ratio of COMPLETED vs FAILED documents
- **Queue Depth**: Number of PENDING/PROCESSING documents
- **Thread Pool Utilization**: Active threads in `parsingTaskExecutor`
- **Database Connection Pool**: Active/idle connections

---

## 11. Security Considerations

### File Upload Restrictions

Add to `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

### Allowed File Types

```java
private static final Set<String> ALLOWED_TYPES = Set.of(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "image/png",
    "image/jpeg",
    "text/plain"
);

// Validate in controller
if (!ALLOWED_TYPES.contains(file.getContentType())) {
    throw new IllegalArgumentException("Unsupported file type");
}
```

### Swagger Security (Production)

```yaml
springdoc:
  swagger-ui:
    enabled: false  # Disable in production
```

Or use Spring Security:

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

### Database Credentials

**Never hardcode credentials.** Use environment variables:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:workflow}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Set environment variables:

```bash
export DB_HOST=your-db-host.aws.neon.tech
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export DB_NAME=workflow
```

---

## 12. Deployment Checklist

### Pre-Deployment

- [ ] PostgreSQL 14+ database provisioned
- [ ] `document_management` schema created
- [ ] Environment variables configured
- [ ] Liquibase migration scripts tested
- [ ] Tesseract OCR installed (optional, for image-based documents)
- [ ] File size limits configured
- [ ] Swagger UI disabled in production
- [ ] Logging configured for production (JSON format recommended)

### Database Setup

```sql
-- Create schema
CREATE SCHEMA IF NOT EXISTS document_management;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA document_management TO your_username;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA document_management TO your_username;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA document_management TO your_username;
```

### Application Startup

```bash
# Build the application
mvn clean package -DskipTests

# Run with production profile
java -jar target/document-parser-service.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:postgresql://your-host:5432/workflow \
  --spring.datasource.username=${DB_USERNAME} \
  --spring.datasource.password=${DB_PASSWORD}
```

### Post-Deployment Verification

```bash
# 1. Health check
curl http://your-app-url/actuator/health

# 2. Test upload
curl -X POST http://your-app-url/api/v1/docs/upload \
  -F "file=@test.pdf"

# 3. Verify database
psql -h your-host -U your-username -d workflow \
  -c "SELECT COUNT(*) FROM document_management.document_records;"
```

### Performance Tuning

**Thread Pool Sizing**

For CPU-intensive parsing:
- Core Pool Size: `Runtime.getRuntime().availableProcessors()`
- Max Pool Size: `2 × CPU cores`

**Database Connection Pool**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

**JVM Options**

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar document-parser-service.jar
```

---

## Summary

This implementation provides:

✅ **Asynchronous Processing**: Non-blocking document parsing with status tracking
✅ **Recursive Parsing**: Extracts content from nested files and attachments
✅ **High-Performance Search**: PostgreSQL GIN indexes + Full-Text Search without extensions
✅ **OCR Support**: Graceful Tesseract integration for image-based content
✅ **Production-Ready**: Error handling, monitoring, security best practices
✅ **Auto-Documentation**: Swagger UI for easy API testing

The service handles documents up to 50MB, breaks content into 2000-character chunks for optimal JSONB indexing, and provides three search modes: exact phrase matching, keyword search with stemming, and metadata filtering.

For questions or issues, refer to the Swagger UI at `/swagger-ui.html` or check application logs.
