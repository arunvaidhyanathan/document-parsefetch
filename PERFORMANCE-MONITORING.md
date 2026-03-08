# Performance Monitoring & Analysis Guide

## Overview

The Document Parsing Service now includes comprehensive performance tracking at **4 different levels**:

1. **Custom Performance Metrics API** - Detailed operation-level tracking
2. **Spring Boot Actuator** - Built-in application metrics
3. **Micrometer Integration** - Time-series metrics with histograms
4. **Database Performance Table** - Historical performance data

---

## 1. Custom Performance Metrics API

### Available Endpoints

#### Get Recent Performance Statistics
```bash
curl "http://localhost:8080/api/v1/performance/stats/recent?minutes=60" | jq .
```

**Response includes:**
- Total operations in time period
- Average times by operation type
- Operation counts
- Last 10 operations with full details

#### Get Overall Performance Statistics
```bash
curl "http://localhost:8080/api/v1/performance/stats/overall" | jq .
```

**Response includes:**
- Average, min, max times per operation type
- Total operations count
- Success/failure ratios

### Tracked Metrics

#### For Document Parsing (PARSE):
- `totalTimeMs` - End-to-end processing time
- `dbTimeMs` - Time spent in database operations
- `parsingTimeMs` - Time spent parsing the document
- `tikaParseTimeMs` - Apache Tika parsing time
- `fileSizeBytes` - Document file size
- `chunksCreated` - Number of text chunks created
- `metadataKeysExtracted` - Number of metadata fields

#### For Search Operations (SEARCH_FTS, SEARCH_METADATA):
- `totalTimeMs` - End-to-end search time
- `dbTimeMs` - Database query execution time
- `resultsCount` - Number of results returned
- `searchQuery` - The search query executed

---

## 2. Spring Boot Actuator Metrics

### Available Metrics Endpoints

#### List All Available Metrics
```bash
curl http://localhost:8080/actuator/metrics | jq '.names'
```

#### Application Health
```bash
curl http://localhost:8080/actuator/health | jq .
```

**Includes:**
- Database connectivity status
- Disk space status
- Application status

#### Custom Metrics

##### Document Parsing Time
```bash
curl http://localhost:8080/actuator/metrics/document.parsing.time | jq .
```

**Provides:**
- COUNT - Total number of parsing operations
- TOTAL_TIME - Sum of all parsing times (seconds)
- MAX - Maximum parsing time recorded

##### Document Parsing Count
```bash
curl http://localhost:8080/actuator/metrics/document.parsing.count | jq .
```

**Breakdown by status:** SUCCESS, FAILED

##### Document Search Time
```bash
curl http://localhost:8080/actuator/metrics/document.search.time | jq .
```

**Breakdown by:**
- Operation type (SEARCH_FTS, SEARCH_METADATA, SEARCH_PHRASE)
- Status (SUCCESS, FAILED)

##### Document Search Count
```bash
curl http://localhost:8080/actuator/metrics/document.search.count | jq .
```

#### JVM & System Metrics

```bash
# JVM Memory Usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq .

# CPU Usage
curl http://localhost:8080/actuator/metrics/system.cpu.usage | jq .

# Thread Count
curl http://localhost:8080/actuator/metrics/jvm.threads.live | jq .

# HTTP Request Metrics
curl http://localhost:8080/actuator/metrics/http.server.requests | jq .
```

---

## 3. Prometheus Metrics Export

### Access Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

**Output format:** Prometheus-compatible metrics for Grafana dashboards

### Sample Prometheus Metrics

```
# Document Parsing Time (seconds)
document_parsing_time_seconds_count{application="document-parsing-service",status="SUCCESS",} 1.0
document_parsing_time_seconds_sum{application="document-parsing-service",status="SUCCESS",} 0.131
document_parsing_time_seconds_max{application="document-parsing-service",status="SUCCESS",} 0.131

# Document Search Time (seconds)
document_search_time_seconds_count{application="document-parsing-service",status="SUCCESS",type="SEARCH_FTS",} 2.0
document_search_time_seconds_sum{application="document-parsing-service",status="SUCCESS",type="SEARCH_FTS",} 0.241
```

---

## 4. Database Performance Table

### Direct Database Queries

```sql
-- Get all performance metrics for parsing operations
SELECT
    operation_type,
    AVG(total_time_ms) as avg_total_ms,
    AVG(db_time_ms) as avg_db_ms,
    AVG(parsing_time_ms) as avg_parsing_ms,
    AVG(tika_parse_time_ms) as avg_tika_ms,
    COUNT(*) as operation_count
FROM document_management.performance_metrics
WHERE operation_type = 'PARSE' AND status = 'SUCCESS'
GROUP BY operation_type;

-- Get search performance breakdown
SELECT
    operation_type,
    AVG(total_time_ms) as avg_time_ms,
    AVG(db_time_ms) as avg_db_ms,
    AVG(results_count) as avg_results,
    COUNT(*) as operation_count
FROM document_management.performance_metrics
WHERE operation_type LIKE 'SEARCH%' AND status = 'SUCCESS'
GROUP BY operation_type;

-- Get slowest operations
SELECT
    operation_type,
    document_id,
    total_time_ms,
    db_time_ms,
    parsing_time_ms,
    file_size_bytes,
    created_at
FROM document_management.performance_metrics
WHERE status = 'SUCCESS'
ORDER BY total_time_ms DESC
LIMIT 10;

-- Performance over time (hourly aggregates)
SELECT
    DATE_TRUNC('hour', created_at) as hour,
    operation_type,
    AVG(total_time_ms) as avg_time_ms,
    COUNT(*) as operations_per_hour
FROM document_management.performance_metrics
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY DATE_TRUNC('hour', created_at), operation_type
ORDER BY hour DESC;
```

---

## Load Testing Tools Comparison

### 1. **Apache JMeter** (Recommended for GUI-based testing)

**Pros:**
- ✅ Comprehensive GUI for test configuration
- ✅ Built-in reporting and graphs
- ✅ Support for file uploads (multipart/form-data)
- ✅ Assertion capabilities
- ✅ Easy parameterization

**Cons:**
- ❌ Heavy resource consumption
- ❌ Steeper learning curve

**Use Case:** Ideal for comprehensive load testing with detailed reports

#### JMeter Test Plan Setup

1. **Install JMeter:**
```bash
brew install jmeter  # macOS
# or download from https://jmeter.apache.org/
```

2. **Create Test Plan:**

```xml
<!-- Sample JMeter Test Plan -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup>
        <stringProp name="ThreadGroup.num_threads">10</stringProp>
        <stringProp name="ThreadGroup.ramp_time">5</stringProp>
        <stringProp name="ThreadGroup.loops">100</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.path">/api/v1/search/content</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="query" elementType="HTTPArgument">
                <stringProp name="Argument.value">revenue</stringProp>
              </elementProp>
              <elementProp name="exact" elementType="HTTPArgument">
                <stringProp name="Argument.value">false</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

3. **Run JMeter Test:**
```bash
jmeter -n -t search-test-plan.jmx -l results.jtl -e -o reports/
```

---

### 2. **Apache Bench (ab)** (Recommended for quick testing)

**Pros:**
- ✅ Extremely lightweight
- ✅ Simple command-line interface
- ✅ Fast execution
- ✅ Pre-installed on macOS/Linux

**Cons:**
- ❌ Limited to simple HTTP requests
- ❌ No file upload support
- ❌ Basic reporting

**Use Case:** Quick performance checks and baseline testing

#### Example Commands

```bash
# Test search endpoint - 1000 requests with 10 concurrent
ab -n 1000 -c 10 "http://localhost:8080/api/v1/search/content?query=revenue&exact=false"

# With custom headers
ab -n 1000 -c 10 -H "Accept: application/json" \
   "http://localhost:8080/api/v1/search/content?query=revenue&exact=false"

# Output analysis:
# - Requests per second
# - Time per request (mean)
# - Transfer rate
# - Percentile distribution
```

---

### 3. **Gatling** (Recommended for code-based testing)

**Pros:**
- ✅ Scala/Kotlin based, version-controllable
- ✅ Beautiful HTML reports
- ✅ Low resource consumption
- ✅ Excellent for CI/CD integration

**Cons:**
- ❌ Requires Scala/Kotlin knowledge
- ❌ Steeper initial setup

**Use Case:** Automated performance testing in CI/CD pipelines

#### Sample Gatling Script

```scala
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class DocumentParsingSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")

  val searchScenario = scenario("Search Documents")
    .exec(http("Search Revenue")
      .get("/api/v1/search/content")
      .queryParam("query", "revenue")
      .queryParam("exact", "false")
      .check(status.is(200)))
    .pause(1.second)

  setUp(
    searchScenario.inject(
      rampUsers(100) during (30.seconds)
    ).protocols(httpProtocol)
  )
}
```

---

### 4. **k6** (Recommended for modern load testing)

**Pros:**
- ✅ JavaScript-based, easy to learn
- ✅ Excellent for CI/CD
- ✅ Cloud integration available
- ✅ Good performance

**Cons:**
- ❌ Relatively new ecosystem

**Use Case:** Modern cloud-native load testing

#### Sample k6 Script

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/v1/search/content?query=revenue&exact=false');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}
```

**Run k6:**
```bash
k6 run load-test.js
```

---

### 5. **Custom Python Script** (Recommended for flexible testing)

**Pros:**
- ✅ Full control over test scenarios
- ✅ Easy to integrate with custom logic
- ✅ Can test file uploads
- ✅ Lightweight

#### Sample Python Load Test

```python
import requests
import time
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed

def search_test(query):
    start = time.time()
    response = requests.get(
        'http://localhost:8080/api/v1/search/content',
        params={'query': query, 'exact': 'false'}
    )
    elapsed = time.time() - start
    return {
        'status': response.status_code,
        'time_ms': elapsed * 1000,
        'results': len(response.json()) if response.ok else 0
    }

def upload_test(file_path):
    start = time.time()
    with open(file_path, 'rb') as f:
        response = requests.post(
            'http://localhost:8080/api/v1/docs/upload',
            files={'file': f}
        )
    elapsed = time.time() - start
    return {
        'status': response.status_code,
        'time_ms': elapsed * 1000,
        'doc_id': response.text if response.ok else None
    }

# Load test with 100 concurrent searches
queries = ['revenue', 'confidential', 'EBITDA'] * 34
times = []

with ThreadPoolExecutor(max_workers=10) as executor:
    futures = [executor.submit(search_test, q) for q in queries]
    for future in as_completed(futures):
        result = future.result()
        times.append(result['time_ms'])
        print(f"Status: {result['status']}, Time: {result['time_ms']:.2f}ms, Results: {result['results']}")

print(f"\nPerformance Summary:")
print(f"Total Requests: {len(times)}")
print(f"Mean: {statistics.mean(times):.2f}ms")
print(f"Median: {statistics.median(times):.2f}ms")
print(f"Min: {min(times):.2f}ms")
print(f"Max: {max(times):.2f}ms")
print(f"95th Percentile: {statistics.quantiles(times, n=20)[18]:.2f}ms")
```

**Run:**
```bash
python3 load_test.py
```

---

## Recommendations

### For Your Use Case:

1. **Start with Apache Bench** for quick baseline testing
2. **Use Custom Python Script** for file upload testing
3. **Implement JMeter** for comprehensive load testing with reports
4. **Add k6** for CI/CD integration

### Performance Targets

Based on your current metrics:

- **Parsing (small files <2KB):** Target < 200ms
- **Parsing (large files >100KB):** Target < 2s
- **Search (FTS):** Target < 150ms
- **Search (Metadata):** Target < 100ms
- **Database queries:** Target < 50ms

### Monitoring Stack Recommendation

```
Application → Micrometer → Prometheus → Grafana Dashboard
              ↓
         Custom API
              ↓
    Performance Metrics DB
```

---

## Quick Start Performance Testing

```bash
# 1. Run 100 search requests
ab -n 100 -c 10 "http://localhost:8080/api/v1/search/content?query=revenue&exact=false"

# 2. Check current performance stats
curl http://localhost:8080/api/v1/performance/stats/recent?minutes=5 | jq '.averageTimesByOperation'

# 3. Monitor Micrometer metrics
curl http://localhost:8080/actuator/metrics/document.search.time | jq .

# 4. Query database for detailed analysis
psql -h your-db-host -U username -d workflow \
  -c "SELECT operation_type, AVG(total_time_ms) FROM document_management.performance_metrics GROUP BY operation_type;"
```

---

## Performance Optimization Tips

1. **Database Connection Pool:** Already optimized (max 20 connections)
2. **Thread Pool:** Configured for 4-10 concurrent parsing operations
3. **GIN Indexes:** Already in place for JSONB queries
4. **Monitoring:** All 4 levels now active

## Next Steps

1. Set up Grafana dashboard with Prometheus integration
2. Configure alerting for slow operations (>1s)
3. Implement automated performance regression tests in CI/CD
4. Create baseline performance reports for different document types
