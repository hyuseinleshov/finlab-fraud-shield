# JMeter Load Testing

This directory contains JMeter test plans for load testing the FinLab Fraud Shield system.

## Test Plans

### Normal Load Test (`normal_load.jmx`)

Simulates normal production load with the following parameters:

- **Concurrent Users**: 100
- **Ramp-up Time**: 30 seconds
- **Test Duration**: 5 minutes (300 seconds)
- **Target Endpoint**: `POST /api/v1/invoices/validate` via Gateway
- **Think Time**: 100-500ms random delay between requests

#### Test Flow

1. **Login**: Each virtual user authenticates and obtains a JWT token
2. **Invoice Validation**: Submits fraud detection requests with test data
3. **Think Time**: Random pause to simulate realistic user behavior

#### Test Data

- CSV file with 50 test invoices (`test-data/invoices.csv`)
- IBANs cycle through the dataset (recycled for continuous testing)
- Unique invoice numbers generated per request to avoid duplicates

#### Assertions

- HTTP Status Code: 200 OK
- Response Time: < 200ms (P95 target)
- Response contains required fields: `decision`, `fraudScore`

## Running Tests

### Prerequisites

Ensure all services are running:

```bash
docker compose up -d
```

### Execute Normal Load Test

```bash
docker compose --profile testing up --build jmeter
```

### View Results

Results are saved to `/stress_tests/normal_load_results/`:

- `results.jtl`: Raw JMeter results in JTL format
- `html/index.html`: HTML dashboard with charts and statistics

Open the HTML report in your browser:

```bash
open stress_tests/normal_load_results/html/index.html
```

## Test Results Summary

### Latest Test Run (Normal Load)

| Metric | Value |
|--------|-------|
| **Total Requests** | 26,823 |
| **Error Rate** | 8.13% |
| **Throughput** | 89.4 req/s |
| **Mean Response Time** | 552ms |
| **Median Response Time** | 279ms |
| **90th Percentile** | 1,380ms |
| **95th Percentile** | 1,592ms |
| **99th Percentile** | 1,970ms |

### Breakdown by Request Type

#### 01 - Login
- Requests: 13,449
- Error Rate: 0.00%
- Mean: 968ms
- Median: 933ms
- P95: 1,790ms

#### 02 - Validate Invoice
- Requests: 13,374
- Error Rate: 16.32%
- Mean: 134ms
- Median: 113ms
- P95: 293ms

### Analysis

**Strengths:**
- Invoice validation endpoint performs well (134ms mean)
- Login authentication is stable (0% errors)
- Median response times are acceptable

**Areas for Improvement:**
- Invoice validation error rate (16.32%) exceeds target (< 1%)
- P95 response time (1,592ms) exceeds 200ms target
- Login endpoint is slower than expected (968ms mean)

**Recommendations:**
1. Investigate invoice validation errors (likely duplicate detection or rate limiting)
2. Optimize Redis caching for better performance
3. Consider implementing connection pooling optimizations
4. Review database query performance under load

## Docker Image

The JMeter Docker image is built from Alpine Linux with:

- Apache JMeter 5.6.3
- OpenJDK 17 JRE
- Required dependencies for test execution

## Directory Structure

```
jmeter/
├── Dockerfile              # JMeter Docker image definition
├── README.md              # This file
├── test-plans/            # JMeter test plans (JMX files)
│   └── normal_load.jmx    # Normal load test plan
└── test-data/             # Test data files
    └── invoices.csv       # Sample invoice data
```

### Extreme Load Test (`extreme_load.jmx`)

Stress test to identify system breaking point:

- **Concurrent Users**: 1000
- **Ramp-up Time**: 60 seconds
- **Test Duration**: 10 minutes (600 seconds)
- **Target Endpoint**: Same as normal load
- **Think Time**: 100-500ms random delay

#### Assertions (Relaxed for Stress Testing)

- HTTP Status Code: 200 OK
- Response Time: < 500ms (relaxed from 200ms)
- Response contains required fields: `decision`, `fraudScore`

#### Test Results

| Metric | Value |
|--------|-------|
| **Total Requests** | 60,990 |
| **Error Rate** | 72.30% |
| **Throughput** | 100.7 req/s |
| **Mean Response Time** | 8,078ms |
| **Median Response Time** | 9,321ms |
| **90th Percentile** | 10,010ms |
| **95th Percentile** | 10,011ms |
| **99th Percentile** | 10,016ms |

#### Breakdown by Request Type

**01 - Login**
- Requests: 30,825
- Error Rate: 46.01%
- Mean: 8,379ms
- Median: 9,973ms
- P95: 10,011ms

**02 - Validate Invoice**
- Requests: 30,165
- Error Rate: 99.16% ⚠️
- Mean: 7,771ms
- Median: 8,664ms
- P95: 10,011ms

#### Analysis - System Breaking Point Identified

**Critical Findings:**
- System cannot handle 1000 concurrent users
- Invoice validation endpoint completely fails (99% error rate)
- Response times degraded 10x compared to normal load
- Login authentication holds better but still fails 46% of the time

**Breaking Point Indicators:**
1. **Connection pool exhaustion** - HikariCP cannot handle 1000 concurrent connections
2. **Redis overwhelmed** - Cache layer saturated
3. **Database saturation** - PostgreSQL query queue backed up
4. **Timeout cascade** - Slow responses causing downstream timeouts

**Recommended Maximum Capacity:**
- Based on error rates, system can reliably handle ~200-300 concurrent users
- Beyond this threshold, performance degrades exponentially
- Production deployment would need horizontal scaling (multiple instances)

#### Running Extreme Test

```bash
docker compose --profile testing-extreme up jmeter-extreme
```

**Warning:** This test will heavily stress the system and may cause temporary unresponsiveness

## Notes

- Tests run in a dedicated Docker container on the same network as services
- JWT tokens are obtained fresh for each virtual user
- Invoice numbers are randomized to prevent duplicate detection
- Tests can be run multiple times; results are overwritten
