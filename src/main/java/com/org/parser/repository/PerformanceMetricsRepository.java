package com.org.parser.repository;

import com.org.parser.entity.PerformanceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PerformanceMetricsRepository extends JpaRepository<PerformanceMetrics, Long> {

    List<PerformanceMetrics> findByOperationType(String operationType);

    @Query("SELECT pm FROM PerformanceMetrics pm WHERE pm.createdAt >= :since ORDER BY pm.createdAt DESC")
    List<PerformanceMetrics> findRecentMetrics(Instant since);

    @Query("SELECT pm.operationType, AVG(pm.totalTimeMs), MIN(pm.totalTimeMs), MAX(pm.totalTimeMs), COUNT(pm) " +
           "FROM PerformanceMetrics pm " +
           "WHERE pm.status = 'SUCCESS' " +
           "GROUP BY pm.operationType")
    List<Object[]> getOperationStatistics();

    @Query("SELECT AVG(pm.totalTimeMs) FROM PerformanceMetrics pm WHERE pm.operationType = :operationType AND pm.status = 'SUCCESS'")
    Double getAverageTimeByOperation(String operationType);
}
