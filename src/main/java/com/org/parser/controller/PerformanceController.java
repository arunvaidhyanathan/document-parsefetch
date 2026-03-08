package com.org.parser.controller;

import com.org.parser.service.PerformanceTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance")
@Tag(name = "Performance Metrics", description = "Performance monitoring and statistics")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceTrackingService performanceTracking;

    @GetMapping("/stats/recent")
    @Operation(summary = "Get recent performance statistics")
    public ResponseEntity<Map<String, Object>> getRecentStats(
            @Parameter(description = "Number of minutes to look back")
            @RequestParam(defaultValue = "60") int minutes) {

        Map<String, Object> stats = performanceTracking.getRecentPerformanceStats(minutes);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/overall")
    @Operation(summary = "Get overall performance statistics")
    public ResponseEntity<Map<String, Object>> getOverallStats() {
        Map<String, Object> stats = performanceTracking.getOverallStatistics();
        return ResponseEntity.ok(stats);
    }
}
