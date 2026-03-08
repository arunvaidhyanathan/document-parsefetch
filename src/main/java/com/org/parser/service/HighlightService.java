package com.org.parser.service;

import com.org.parser.entity.DocumentRecord;
import com.org.parser.model.SearchResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HighlightService {

    private static final int SNIPPET_CONTEXT_LENGTH = 100; // Characters before/after match
    private static final int MAX_SNIPPETS = 5; // Maximum snippets to return per document

    /**
     * Creates search result DTOs with highlighted snippets
     */
    public List<SearchResultDTO> createHighlightedResults(List<DocumentRecord> documents, String query, boolean exactMatch) {
        List<SearchResultDTO> results = new ArrayList<>();

        for (DocumentRecord doc : documents) {
            SearchResultDTO dto = new SearchResultDTO();
            dto.setId(doc.getId());
            dto.setFilename(doc.getFilename());
            dto.setStatus(doc.getStatus());
            dto.setClassification(doc.getClassification());
            dto.setContentType(doc.getContentType());
            dto.setCreatedAt(doc.getCreatedAt());

            // Extract highlights
            List<SearchResultDTO.HighlightSnippet> highlights = extractHighlights(doc, query, exactMatch);
            dto.setHighlights(highlights);
            dto.setTotalMatches(highlights.size());

            results.add(dto);
        }

        return results;
    }

    /**
     * Extracts highlighted snippets from document content
     */
    private List<SearchResultDTO.HighlightSnippet> extractHighlights(DocumentRecord doc, String query, boolean exactMatch) {
        List<SearchResultDTO.HighlightSnippet> snippets = new ArrayList<>();

        if (doc.getParsedContent() == null || doc.getParsedContent().isEmpty()) {
            return snippets;
        }

        int chunkIndex = 0;
        for (String chunk : doc.getParsedContent()) {
            if (chunk == null || chunk.trim().isEmpty()) {
                chunkIndex++;
                continue;
            }

            List<SearchResultDTO.HighlightSnippet> chunkSnippets = extractSnippetsFromChunk(
                chunk, query, chunkIndex, exactMatch
            );
            snippets.addAll(chunkSnippets);

            if (snippets.size() >= MAX_SNIPPETS) {
                break;
            }
            chunkIndex++;
        }

        return snippets.subList(0, Math.min(snippets.size(), MAX_SNIPPETS));
    }

    /**
     * Extracts snippets from a single chunk
     */
    private List<SearchResultDTO.HighlightSnippet> extractSnippetsFromChunk(
            String chunk, String query, int chunkIndex, boolean exactMatch) {

        List<SearchResultDTO.HighlightSnippet> snippets = new ArrayList<>();

        if (exactMatch) {
            // For exact match, find the exact phrase
            snippets.addAll(findExactMatches(chunk, query, chunkIndex));
        } else {
            // For keyword search, find each word
            String[] keywords = query.split("\\s+");
            for (String keyword : keywords) {
                snippets.addAll(findKeywordMatches(chunk, keyword, chunkIndex));
                if (snippets.size() >= MAX_SNIPPETS) {
                    break;
                }
            }
        }

        return snippets;
    }

    /**
     * Finds exact phrase matches
     */
    private List<SearchResultDTO.HighlightSnippet> findExactMatches(String text, String phrase, int chunkIndex) {
        List<SearchResultDTO.HighlightSnippet> snippets = new ArrayList<>();

        // Case-insensitive search
        Pattern pattern = Pattern.compile(Pattern.quote(phrase), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find() && snippets.size() < MAX_SNIPPETS) {
            int start = matcher.start();
            int end = matcher.end();

            SearchResultDTO.HighlightSnippet snippet = createSnippet(text, start, end, chunkIndex, phrase);
            snippets.add(snippet);
        }

        return snippets;
    }

    /**
     * Finds keyword matches (case-insensitive, word boundaries)
     */
    private List<SearchResultDTO.HighlightSnippet> findKeywordMatches(String text, String keyword, int chunkIndex) {
        List<SearchResultDTO.HighlightSnippet> snippets = new ArrayList<>();

        // Match whole words, case-insensitive
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find() && snippets.size() < MAX_SNIPPETS) {
            int start = matcher.start();
            int end = matcher.end();

            SearchResultDTO.HighlightSnippet snippet = createSnippet(text, start, end, chunkIndex, keyword);
            snippets.add(snippet);
        }

        return snippets;
    }

    /**
     * Creates a snippet with context around the match
     */
    private SearchResultDTO.HighlightSnippet createSnippet(
            String text, int matchStart, int matchEnd, int chunkIndex, String matchedTerm) {

        SearchResultDTO.HighlightSnippet snippet = new SearchResultDTO.HighlightSnippet();

        // Calculate snippet boundaries with context
        int snippetStart = Math.max(0, matchStart - SNIPPET_CONTEXT_LENGTH);
        int snippetEnd = Math.min(text.length(), matchEnd + SNIPPET_CONTEXT_LENGTH);

        // Adjust to word boundaries for cleaner snippets
        while (snippetStart > 0 && !Character.isWhitespace(text.charAt(snippetStart))) {
            snippetStart--;
        }
        while (snippetEnd < text.length() && !Character.isWhitespace(text.charAt(snippetEnd))) {
            snippetEnd++;
        }

        // Extract the plain text snippet
        String snippetText = text.substring(snippetStart, snippetEnd).trim();

        // Add ellipsis if snippet doesn't start/end at boundaries
        if (snippetStart > 0) {
            snippetText = "..." + snippetText;
        }
        if (snippetEnd < text.length()) {
            snippetText = snippetText + "...";
        }

        // Create highlighted version
        String highlightedText = highlightMatches(snippetText, matchedTerm);

        snippet.setText(snippetText);
        snippet.setHighlightedText(highlightedText);
        snippet.setPosition(matchStart);
        snippet.setChunkIndex(chunkIndex);

        return snippet;
    }

    /**
     * Highlights all occurrences of the search term in the text
     */
    private String highlightMatches(String text, String searchTerm) {
        // Use case-insensitive pattern with word boundaries
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(searchTerm) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            // Wrap matched text in <mark> tags
            matcher.appendReplacement(result, "<mark class='highlight'>" + matcher.group() + "</mark>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Highlights multiple keywords in text
     */
    public String highlightMultipleKeywords(String text, List<String> keywords) {
        String result = text;
        for (String keyword : keywords) {
            result = highlightMatches(result, keyword);
        }
        return result;
    }
}
