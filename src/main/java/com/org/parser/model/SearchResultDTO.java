package com.org.parser.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SearchResultDTO {
    private Long id;
    private String filename;
    private String status;
    private String classification;
    private String contentType;
    private Instant createdAt;

    // Highlight information
    private List<HighlightSnippet> highlights;
    private Integer totalMatches;

    @Data
    public static class HighlightSnippet {
        private String text;           // The snippet text
        private String highlightedText; // HTML with <mark> tags
        private Integer position;       // Position in document
        private Integer chunkIndex;     // Which chunk this is from
    }
}
