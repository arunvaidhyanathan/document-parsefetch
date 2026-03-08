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
                   "WHERE to_tsvector('english', COALESCE(CAST(parsed_content AS text), '')) @@ to_tsquery('english', :tsQuery)",
           nativeQuery = true)
    List<DocumentRecord> searchByKeyword(@Param("tsQuery") String tsQuery);

    /**
     * Find documents by status
     */
    List<DocumentRecord> findByStatus(String status);
}
