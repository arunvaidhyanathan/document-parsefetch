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
