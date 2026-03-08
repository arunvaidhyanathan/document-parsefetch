package com.org.parser.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "performance_metrics", schema = "document_management")
@Data
public class PerformanceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "perf_seq")
    @SequenceGenerator(
        name = "perf_seq",
        sequenceName = "performance_metrics_seq",
        schema = "document_management",
        allocationSize = 1
    )
    private Long id;

    @Column(name = "operation_type", nullable = false)
    private String operationType; // PARSE, SEARCH_CONTENT, SEARCH_METADATA

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "total_time_ms", nullable = false)
    private Long totalTimeMs;

    @Column(name = "db_time_ms")
    private Long dbTimeMs;

    @Column(name = "parsing_time_ms")
    private Long parsingTimeMs;

    @Column(name = "tika_parse_time_ms")
    private Long tikaParseTimeMs;

    @Column(name = "chunks_created")
    private Integer chunksCreated;

    @Column(name = "metadata_keys_extracted")
    private Integer metadataKeysExtracted;

    @Column(name = "search_query")
    private String searchQuery;

    @Column(name = "results_count")
    private Integer resultsCount;

    @Column(name = "status")
    private String status; // SUCCESS, FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
