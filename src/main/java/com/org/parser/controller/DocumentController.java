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

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
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
