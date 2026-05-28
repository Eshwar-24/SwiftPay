# SwiftPay - Load Testing Guide

## Overview

This guide provides instructions for executing load tests on SwiftPay system, capturing performance metrics, and generating PCAP traces for analysis.

## Load Testing Tools

### K6 (Recommended)
- Modern load testing tool written in Go
- JavaScript-based test scripts
- Cloud integration available
- Real-time metrics and result summaries

### Installation

#### macOS
```bash
brew install k6
```

#### Windows (Chocolatey)
```powershell
choco install k6
```

#### Linux (Ubuntu/Debian)
```bash
apt-get install k6
```

#### Docker
```bash
docker run --rm -i grafana/k6:latest run - <test-script.js
```

### Verification
```bash
k6 version
# Output: v0.xx.x
```

## Load Test Scenarios

### Test Configuration

The `load-test.js` script includes:

1. **Ramp-up Phase**: 0 → 250 TPS over 2 minutes
2. **Sustained Phase**: 250 TPS for 30 minutes (~1 million transactions)
3. **Ramp-down Phase**: 250 → 0 TPS over 2 minutes

**Total Duration**: 34 minutes
**Total Transactions**: ~1.05 million
**Average TPS**: 250

### Test Stages
```javascript
export const options = {
  stages: [
    { duration: '2m', target: 250 },   // Ramp up
    { duration: '30m', target: 250 },  // Main load test
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};
```

## Running Load Tests

### Quick Start (5-minute test)
```bash
# Terminal 1: Start services
cd SwiftPay
docker-compose up -d
sleep 30  # Wait for services to start

# Terminal 2: Run short load test
k6 run load-test.js --vus 10 --duration 5m
```

### Full Load Test (250 TPS for 30 minutes)
```bash
curl -f http://localhost:8080/health > /dev/null || {
  echo "Application not ready"
  exit 1
}

# Clear any previous test data
docker-compose exec postgres psql -U postgres -d swiftpay -c "TRUNCATE payments, ledger;" 2>/dev/null || true

# Run load test
k6 run load-test.js

# Results will be displayed after completion
```

### Remote Execution (Cloud)
```bash
k6 cloud load-test.js
# Results stored in Grafana Cloud
```

## Understanding Metrics

### Response Time Metrics
```
http_req_duration:
  avg:      89.45ms  (Average time per request)
  min:      5.23ms   (Fastest request)
  max:      1245.67ms (Slowest request)
  p(50):    45.12ms  (Median - 50% under this)
  p(90):    245.34ms (90% under this)
  p(95):    455.67ms (95% under this - SLA threshold)
  p(99):    890.23ms (99% under this - SLA threshold)
```

### Request Metrics
```
http_reqs:
  1,000,000  (Total requests completed)
  291.67/s   (Average throughput)

http_req_failed:
  80         (Failed requests)
  0.008%     (Error rate)
```

### Virtual User Metrics
```
vus:
  min: 0
  max: 250    (Peak concurrent users)

vus_max:      250 (Maximum reached during test)
```

## PCAP Trace Capture

### Prerequisites
```bash
# macOS
brew install wireshark

# Ubuntu/Debian
sudo apt-get install wireshark tshark

# Windows
# Download from: https://www.wireshark.org/download/

# Verify installation
tcpdump --version
```

### Capture During Load Test

#### Option 1: Using tcpdump (Linux/macOS)

```bash
# Terminal 1: Start packet capture
sudo tcpdump -i any -w swiftpay-load-test.pcap \
  'tcp port 8080 or tcp port 9092 or tcp port 5432 or tcp port 6379'

# Terminal 2: Run load test
k6 run load-test.js

# Terminal 1: Stop capture (Ctrl+C)
# Result: swiftpay-load-test.pcap file created
```

#### Option 2: Using tcpdump with filter for accuracy

```bash
# Capture HTTP traffic
sudo tcpdump -i any -w swiftpay-http.pcap 'tcp port 8080'

# Capture Kafka traffic
sudo tcpdump -i any -w swiftpay-kafka.pcap 'tcp port 9092'

# Capture Database traffic
sudo tcpdump -i any -w swiftpay-db.pcap 'tcp port 5432'
```

#### Option 3: Docker Bridge Network

```bash
# Get network interface
docker network inspect swiftpay_default | grep '"br-[a-f0-9]'

# Capture on docker interface (e.g., br-abc123)
sudo tcpdump -i br-abc123 -w swiftpay-docker.pcap 'port 8080 or port 9092 or port 5432'
```

### Merging Multiple Captures
```bash
# Combine multiple pcap files
mergecap -w swiftpay-combined.pcap \
  swiftpay-http.pcap \
  swiftpay-kafka.pcap \
  swiftpay-db.pcap
```

## Analyzing PCAP Traces

### Using Wireshark (GUI)
```bash
# Open capture file
wireshark swiftpay-load-test.pcap

# Filter options:
# - TCP traffic only: tcp
# - HTTP only: http
# - Kafka: tcp.port==9092
# - PostgreSQL: tcp.port==5432
# - Response times: tcp.time_delta
# - Errors: tcp.flags.reset

# Statistics Menu:
# - Statistics → TCP Stream Graph
# - Statistics → HTTP
# - Statistics → Protocol Hierarchy
# - Statistics → Flow Graph
```

### Using tshark (CLI)
```bash
# List all captured frames
tshark -r swiftpay-load-test.pcap

# Filter for HTTP requests only
tshark -r swiftpay-load-test.pcap -Y 'http.request'

# Filter for errors (TCP retransmissions, resets)
tshark -r swiftpay-load-test.pcap -Y 'tcp.analysis.flags.retransmission or tcp.flags.reset'

# Extract response times
tshark -r swiftpay-load-test.pcap -Y 'http.response' \
  -T fields -e 'frame.time_delta_displayed'

# Protocol distribution
tshark -r swiftpay-load-test.pcap -q -z io,phs
```

### Extracting Statistics
```bash
# Count requests per second
tshark -r swiftpay-load-test.pcap -T fields -e 'frame.time' | \
  cut -d'.' -f1 | uniq -c | sort

# Find slowest requests
tshark -r swiftpay-load-test.pcap -Y 'http.response' \
  -T fields -e 'frame.time_delta_displayed' | \
  sort -rn | head -20

# Get connection count
tshark -r swiftpay-load-test.pcap -q -z endpoints,tcp | head -20
```

## Load Test Checklist

### Pre-Test
- [ ] Services running: `docker-compose ps`
- [ ] Health check passing: `curl http://localhost:8080/health`
- [ ] Database initialized with test users
- [ ] Redis cache empty (optional): `redis-cli FLUSHALL`
- [ ] Kafka topics created: `kafka-topics.sh`
- [ ] K6 installed and verified: `k6 version`
- [ ] Disk space available: `df -h`
- [ ] Monitor running: `watch -n 1 'docker stats'`

### During Test
- [ ] Monitor metrics in real-time: `kubectl top pod` or `docker stats`
- [ ] Check logs for errors: `docker-compose logs -f swiftpay`
- [ ] Monitor database: `psql -c "SELECT COUNT(*) FROM payments"`
- [ ] Watch consumer lag: `kafka-consumer-groups.sh`

### Post-Test
- [ ] Collect K6 results: `k6 run ... -o json=results.json`
- [ ] Stop PCAP capture
- [ ] Generate report
- [ ] Analyze metrics
- [ ] Document findings
- [ ] Clean up test data (optional)

## Expected Results (Baseline)

### Performance Metrics
```
Response Times:
  p50 (median):  50-100ms
  p95:           200-300ms
  p99:           500-800ms

Throughput:
  Actual TPS:    ~250 TPS
  Success Rate:  > 99%
  Error Rate:    < 1%

Resource Usage (per node):
  CPU:           40-60%
  Memory:        300-400MB
  Network:       50-100Mbps
```

### Capacity Planning

| Nodes | Peak TPS | P95 Response | CPU Usage | Memory |
|-------|----------|-------------|-----------|--------|
| 1     | 500      | 200ms       | 80%       | 400MB  |
| 3     | 1,500    | 150ms       | 60%       | 300MB  |
| 5     | 2,500    | 120ms       | 50%       | 300MB  |
| 10    | 5,000    | 100ms       | 40%       | 300MB  |

## Troubleshooting Load Test Issues

### High Error Rate
```bash
# Check application logs
docker-compose logs swiftpay | grep ERROR

# Check database connectivity
docker-compose exec postgres pg_isready

# Check Kafka broker
docker-compose exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Monitor database locks
docker-compose exec postgres psql -U postgres -d swiftpay -c "SELECT * FROM pg_locks"
```

### Connection Timeouts
```bash
# Check network connectivity
docker-compose exec swiftpay ping kafka
docker-compose exec swiftpay ping postgres

# Verify service ports
docker-compose port swiftpay 8080
docker-compose port postgres 5432

# Check resource limits
docker stats swiftpay
```

### Memory Issues
```bash
# Check JVM heap
docker-compose exec swiftpay jcmd <PID> GC.heap_dump /tmp/heap.hprof

# Analyze heap dump
# Use JProfiler or Eclipse MAT
```

### Database Lock Contention
```bash
# View active locks
docker-compose exec postgres psql -U postgres -d swiftpay -c \
  "SELECT * FROM pg_stat_activity WHERE state != 'idle';"

# Kill long-running queries if needed
docker-compose exec postgres psql -U postgres -d swiftpay -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE duration > interval '5 minutes';"
```

## Report Generation

### K6 HTML Report
```bash
# Install extension
npm install -g @jsdevtools/summaryreport

# Generate report
k6 run load-test.js -o json=results.json
```

### Custom Report Template
```bash
# Create report.md
cat > LOAD_TEST_REPORT.md << EOF
# SwiftPay Load Test Report

## Test Parameters
- Date: $(date)
- Duration: 34 minutes
- Peak TPS: 250
- Total Requests: ~1M

## Results
- Average Response Time: XXXms
- P95 Response Time: XXXms
- Error Rate: X%

## PCAP Analysis
- Total Packets Captured: XXX
- Unique Connections: XXX
- Retransmissions: X

## Recommendations
1. ...
2. ...
EOF
```

## Best Practices

1. **Baseline Testing**: Run test before code changes for comparison
2. **Incremental Load**: Start low, gradually increase to identify breaking points
3. **Multiple Runs**: Run multiple times to account for variability
4. **Isolation**: Ensure no background traffic during test
5. **Monitoring**: Record system metrics alongside load test
6. **Cleanup**: Truncate test data after each run
7. **Version Control**: Save load test scripts and results

## Advanced Scenarios

### Ramp-up Performance Degradation
```javascript
export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '10s', target: 100 },
    { duration: '10s', target: 200 },
    { duration: '10s', target: 400 },
    { duration: '10s', target: 250 },
  ],
};
```

### Spike Test (Sudden Load Increase)
```javascript
export const options = {
  stages: [
    { duration: '5m', target: 50 },    // Normal load
    { duration: '30s', target: 500 },  // Spike!
    { duration: '5m', target: 50 },    // Back to normal
  ],
};
```

### Stress Test (Maximum Capacity)
```javascript
export const options = {
  stages: [
    { duration: '5m', target: 100 },
    { duration: '5m', target: 500 },
    { duration: '10m', target: 1000 }, // Maximum
    { duration: '5m', target: 0 },
  ],
};
```

## Resources

- [K6 Documentation](https://k6.io/docs/)
- [Wireshark User Guide](https://www.wireshark.org/docs/)
- [tcpdump Manual](https://www.tcpdump.org/papers/sniffing-faq.html)
- [Performance Testing Best Practices](https://en.wikipedia.org/wiki/Software_performance_testing)


