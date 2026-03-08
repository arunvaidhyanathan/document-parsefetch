package com.org.parser.service;

import com.org.parser.entity.PerformanceMetrics;
import com.org.parser.repository.PerformanceMetricsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceTrackingService {

    private final PerformanceMetricsRepository metricsRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void recordParsingMetrics(Long documentId, Long fileSizeBytes, long totalTimeMs,
                                     long dbTimeMs, long parsingTimeMs, long tikaParseTimeMs,
                                     int chunksCreated, int metadataKeysExtracted,
                                     String status, String errorMessage) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setOperationType("PARSE");
        metrics.setDocumentId(documentId);
        metrics.setFileSizeBytes(fileSizeBytes);
        metrics.setTotalTimeMs(totalTimeMs);
        metrics.setDbTimeMs(dbTimeMs);
        metrics.setParsingTimeMs(parsingTimeMs);
        metrics.setTikaParseTimeMs(tikaParseTimeMs);
        metrics.setChunksCreated(chunksCreated);
        metrics.setMetadataKeysExtracted(metadataKeysExtracted);
        metrics.setStatus(status);
        metrics.setErrorMessage(errorMessage);

        metricsRepository.save(metrics);

        // Record to Micrometer
        meterRegistry.timer("document.parsing.time", "status", status)
            .record(totalTimeMs, TimeUnit.MILLISECONDS);
        meterRegistry.counter("document.parsing.count", "status", status).increment();
        meterRegistry.gauge("document.parsing.file.size", fileSizeBytes);

        log.info("Performance Metrics - Parse: total={}ms, db={}ms, parsing={}ms, tika={}ms, chunks={}, size={}bytes",
                totalTimeMs, dbTimeMs, parsingTimeMs, tikaParseTimeMs, chunksCreated, fileSizeBytes);
    }

    @Transactional
    public void recordSearchMetrics(String searchQuery, String operationType,
                                    long totalTimeMs, long dbTimeMs, int resultsCount,
                                    String status, String errorMessage) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setOperationType(operationType);
        metrics.setSearchQuery(searchQuery);
        metrics.setTotalTimeMs(totalTimeMs);
        metrics.setDbTimeMs(dbTimeMs);
        metrics.setResultsCount(resultsCount);
        metrics.setStatus(status);
        metrics.setErrorMessage(errorMessage);

        metricsRepository.save(metrics);

        // Record to Micrometer
        meterRegistry.timer("document.search.time", "type", operationType, "status", status)
            .record(totalTimeMs, TimeUnit.MILLISECONDS);
        meterRegistry.counter("document.search.count", "type", operationType, "status", status).increment();

        log.info("Performance Metrics - Search: type={}, query={}, total={}ms, db={}ms, results={}",
                operationType, searchQuery, totalTimeMs, dbTimeMs, resultsCount);
    }

    public Map<String, Object> getRecentPerformanceStats(int minutesBack) {
        Instant since = Instant.now().minus(Duration.ofMinutes(minutesBack));
        List<PerformanceMetrics> recentMetrics = metricsRepository.findRecentMetrics(since);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOperations", recentMetrics.size());
        stats.put("timePeriod", minutesBack + " minutes");

        // Calculate averages by operation type
        Map<String, Double> avgTimes = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();

        for (PerformanceMetrics metric : recentMetrics) {
            String opType = metric.getOperationType();
            avgTimes.put(opType, avgTimes.getOrDefault(opType, 0.0) + metric.getTotalTimeMs());
            counts.put(opType, counts.getOrDefault(opType, 0L) + 1);
        }

        Map<String, Object> averages = new HashMap<>();
        for (String opType : avgTimes.keySet()) {
            double avg = avgTimes.get(opType) / counts.get(opType);
            averages.put(opType, String.format("%.2fms", avg));
        }

        stats.put("averageTimesByOperation", averages);
        stats.put("operationCounts", counts);
        stats.put("recentMetrics", recentMetrics.stream().limit(10).toList());

        return stats;
    }

    public Map<String, Object> getOverallStatistics() {
        List<Object[]> opStats = metricsRepository.getOperationStatistics();

        Map<String, Map<String, Object>> statistics = new HashMap<>();

        for (Object[] stat : opStats) {
            String operationType = (String) stat[0];
            Double avgTime = (Double) stat[1];
            Long minTime = (Long) stat[2];
            Long maxTime = (Long) stat[3];
            Long count = (Long) stat[4];

            Map<String, Object> opStat = new HashMap<>();
            opStat.put("averageTimeMs", String.format("%.2f", avgTime));
            opStat.put("minTimeMs", minTime);
            opStat.put("maxTimeMs", maxTime);
            opStat.put("totalOperations", count);

            statistics.put(operationType, opStat);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("operationStatistics", statistics);
        result.put("totalRecords", metricsRepository.count());

        return result;
    }
}
