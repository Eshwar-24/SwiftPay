# PCAP (Packet Capture) Analysis - SwiftPay Load Testing

## 🎯 What is PCAP and Why Do We Need It?

### Definition of PCAP
**PCAP (Packet CAPture)** is a file format that contains the raw network traffic captured at the packet level. It includes:
- All network packets sent and received by the system
- Complete TCP/UDP streams
- Protocol headers and payload data
- Timestamps for each packet
- Source and destination IP addresses/ports

### Why PCAP is Critical for Load Testing

#### 1. **Proof of Execution**
- Provides **cryptographic evidence** that the load test actually happened
- Shows real network traffic, not just logs or reports
- Timestamps prove the test duration and intensity
- Cannot be faked or tampered without detection

#### 2. **Network-Level Verification**
At the packet level, PCAP captures:
- **HTTP Requests/Responses**: Actual API calls and responses
- **Kafka Message Traffic**: Event-driven communication between services
- **PostgreSQL Connections**: Database transaction activity
- **Redis Interactions**: Cache hits/misses and commands
- **TCP Handshakes & Closures**: Connection lifecycle

#### 3. **Performance Diagnostics**
PCAP helps identify:
- **Network Latency**: Delay between request and response
- **Packet Loss**: Dropped packets indicating network issues
- **Retransmissions**: TCP retransmits show congestion
- **Window Size Issues**: TCP window scaling problems
- **DNS Resolution Time**: Name server query delays

#### 4. **Bottleneck Analysis**
With PCAP, reviewers can:
- Analyze inter-service communication patterns
- Identify which component is the bottleneck (API → Kafka, Kafka → DB, etc.)
- Measure actual bytes transferred across each channel
- Monitor connection states (TIME_WAIT, ESTABLISHED, etc.)

#### 5. **Compliance & Audit**
- **Regulatory Requirements**: Some fintech regulations require traffic evidence
- **Fraud Prevention**: Network signatures can detect fraudulent patterns
- **Audit Trail**: Immutable record of system behavior
- **Incident Investigation**: Replay traffic to debug production issues

#### 6. **System Stability Validation**
PCAP proves:
- ✅ No network stalls or deadlocks during 1M transactions
- ✅ Connection pooling working correctly (no exhaustion)
- ✅ No TCP Reset floods or connection failures
- ✅ Ordered message delivery (for Kafka)
- ✅ No persistent connection leaks

### SwiftPay E2E Data Flow in PCAP

```
Client (K6 Load Generator)
    │
    ├─────HTTP/3──────► [8080] API Gateway (SwiftPay Service)
    │                            │
    │                            ├─────JDBC/TCP──────► [5432] PostgreSQL
    │                            │    (Write Transactions)
    │                            │
    │                            ├─────PLAINTEXT/TCP──► [29092] Kafka Broker
    │                            │    (Emit Events)
    │                            │
    │                            └─────REDIS/TCP──────► [6379] Redis
    │                                 (Idempotency Check)
    │
    └◄───────HTTP/201──────────────────────────────────────
         Response: [Transaction ID, Status]
```

In the PCAP file, you would see:
- **Traffic on port 8080**: HTTP POST requests with transaction payloads
- **Traffic on port 5432**: PostgreSQL wire protocol messages (BEGIN, INSERT, COMMIT)
- **Traffic on port 29092**: Kafka protocol messages (PRODUCE requests)
- **Traffic on port 6379**: Redis protocol commands (GET/SET)

## 📊 PCAP Metrics SwiftPay Reviewers Will Validate

### 1. Transaction Throughput
```
Expected: ~250 TPS (4 million packets in 30 minutes)
PCAP shows: Exact packet count, timing of each HTTP request
```

### 2. End-to-End Latency
```
HTTP Request → API → DB → Kafka → Response
Time between SYN and FIN packets = true E2E latency
```

### 3. Concurrent Connections
```
Number of unique TCP sessions visible in PCAP
Should show ~250 concurrent connections at peak
```

### 4. Protocol Compliance
- HTTP/1.1 or 2.0 used correctly
- TCP three-way handshake completed
- TLS/SSL certificates valid (if using HTTPS)
- Kafka protocol version matches configured broker

### 5. Error Detection
- TCP RST (reset) flags → connection failures
- TCP DUP ACK → packet loss/retransmissions
- ICMP Unreachable → network routing issues
- HTTP 4xx/5xx responses visible in payload

## 🔍 How to Use PCAP Files for Analysis

### Using Wireshark (GUI)
```
1. Open the PCAP file
2. Go to Statistics → Summary
   - Shows total packets, bytes, duration
3. Go to Statistics → HTTP
   - Shows HTTP request/response counts
4. Go to Statistics → TCP Stream Graphs
   - Visualizes traffic patterns over time
5. Filter: "tcp.port==5432" for database traffic only
6. Right-click → Follow TCP Stream → see actual SQL
```

### Using tshark (CLI)
```bash
# Count HTTP requests
tshark -r swiftpay-load-test.pcap -Y 'http.request' | wc -l

# Get HTTP response codes distribution
tshark -r swiftpay-load-test.pcap -Y 'http.response' \
  -T fields -e 'http.response.code' | sort | uniq -c

# Calculate average response time
tshark -r swiftpay-load-test.pcap -Y 'http.response' \
  -T fields -e 'frame.time_delta_displayed' | awk '{sum+=$1; count++} END {print "Avg:", sum/count "ms"}'

# Identify retransmissions
tshark -r swiftpay-load-test.pcap -Y 'tcp.analysis.retransmission' | wc -l

# Check for connection resets
tshark -r swiftpay-load-test.pcap -Y 'tcp.flags.reset' | wc -l
```

## 📈 Real-World Example: What a PCAP Reveals

### Scenario: Payment API at 250 TPS

**PCAP will show:**

```
Frame 1234 | 0.001s | Client→API(8080) | HTTP POST /v1/payments
          | Payload: {txnId: ABC123, sender: user1, receiver: user2, amount: 100}

Frame 1235 | 0.002s | API(8080)→DB(5432) | BEGIN TRANSACTION
Frame 1236 | 0.005s | DB(5432)→API(8080) | ACK
Frame 1237 | 0.006s | API(8080)→DB(5432) | INSERT INTO payments VALUES(...)
Frame 1238 | 0.010s | DB(5432)→API(8080) | INSERT OK

Frame 1239 | 0.011s | API(8080)→Kafka(29092) | ProduceRequest topic:payment-events
Frame 1240 | 0.012s | Kafka(29092)→API(8080) | ProduceResponse OK

Frame 1241 | 0.013s | API(8080)→Client | HTTP 201 Created
          | Payload: {status: PENDING, txnId: ABC123, createdAt: ...}

[Similar pattern repeats 1,000,000 times over 30 minutes]
```

**What reviewers observe:**
- ✅ Each transaction touches: Client → API → DB → Kafka → Response
- ✅ No broken connections (no RST frames)
- ✅ No stalled packets (all responses received)
- ✅ Distributed load across time (not bunched up)
- ✅ Success rate > 99% (minimal retransmissions)

## 🔐 Why Logs Alone Are Not Sufficient

| Artifact | Proof Level | Tamperability | Coverage |
|----------|------------|---|---------|
| **Application Logs** | Medium | Easy to fake | Limited to application events |
| **Metrics/Dashboards** | Medium | Easy to modify | Aggregated, not granular |
| **Screenshots** | Low | Very easy to fabricate | Single moment in time |
| **PCAP Files** | High | Cryptographically verified | Every packet, complete picture |
| **Database Snapshots** | Medium | Possible if not signed | Final state, not transaction flow |

PCAP is the **immutable source of truth** for network-level behavior.

## 📋 PCAP Capture Strategy for SwiftPay Load Test

### Prerequisites
```bash
# Windows
choco install wireshark
# or manually download from https://www.wireshark.org/

# Linux
sudo apt-get install tcpdump wireshark-cli

# macOS
brew install tcpdump wireshark
```

### Capture Process
```bash
# Terminal 1: Start PCAP capture
# Option A: Capture all traffic
sudo tcpdump -i any -w swiftpay-load-test.pcap \
  'tcp port 8080 or tcp port 9092 or tcp port 5432 or tcp port 6379'

# Option B: Capture to interface file for Docker
sudo tcpdump -i docker0 -w swiftpay-docker.pcap \
  'tcp port 8080 or tcp port 9092 or tcp port 5432'

# Terminal 2: Run the load test
k6 run load-test.js

# Terminal 1: Stop capture (Ctrl+C when load test completes)
```

### Post-Capture Analysis
```bash
# Get statistics
tcpdump -r swiftpay-load-test.pcap -nn -q | wc -l
# Result: Total packet count

# List all conversations
tcpdump -r swiftpay-load-test.pcap -nn | \
  cut -d' ' -f1,2,3 | uniq | wc -l
# Result: Unique connections

# Check for errors
tcpdump -r swiftpay-load-test.pcap -nn 'tcp[13] & 4!=0'
# Result: Packets with RST flag (connection resets)
```

## 🚀 PCAP Delivery: Pushing to GitHub

### Challenge: Large File Sizes
A 30-minute load test at 250 TPS generates:
- **~4M packets**
- **~2-5 GB PCAP file** (depending on payload size)
- **GitHub has 100MB file limit**

### Solution: Git LFS (Large File Storage)

```bash
# 1. Install Git LFS
# Windows: git lfs install (comes with Git for Windows)
# macOS: brew install git-lfs && git lfs install
# Linux: curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | sudo bash

# 2. Track PCAP files with Git LFS
cd SwiftPay
git lfs install
git lfs track "*.pcap"
git add .gitattributes
git commit -m "Track PCAP files with Git LFS"

# 3. Add the PCAP file
git add swiftpay-load-test.pcap
git commit -m "Add PCAP trace: 250 TPS × 1M transactions load test"
git push origin main

# 4. Verify LFS tracking
git lfs ls-files
```

### Alternative: Compressed PCAP

```bash
# Compress PCAP to reduce size
gzip -9 swiftpay-load-test.pcap
# Reduces 5GB → 500MB

# Or create summary PCAP (sample every Nth packet)
tcpdump -r swiftpay-load-test.pcap -w swiftpay-summary.pcap 'packet number % 10 == 0'
```

## 📝 PCAP Report Template

```markdown
## Load Test Execution Summary

**Date:** May 28, 2026
**Test Type:** 250 TPS Sustained Load
**Duration:** 34 minutes (2m ramp-up + 30m sustained + 2m ramp-down)
**Total Transactions:** 1,050,000

### PCAP Artifacts
- **File:** swiftpay-load-test.pcap (4.2 GB)
- **Packets Captured:** 4,200,000
- **Time Range:** 12:00:00 → 12:34:00 UTC
- **Compression:** None (use Git LFS)

### Traffic Distribution
- **HTTP (Port 8080):** 1.05M packets (API requests/responses)
- **Kafka (Port 9092):** 2.1M packets (Event distribution)
- **PostgreSQL (Port 5432):** 840K packets (Transactions)
- **Redis (Port 6379):** 210K packets (Cache operations)

### Quality Metrics
- **TCP Retransmissions:** 15 (0.0004% of total)
- **Connection Resets:** 0
- **Average RTT:** 45ms
- **Peak Throughput:** 280 Mbps
- **Success Rate:** 99.98%

### Key Findings
1. ✅ All 1M transactions successfully traversed the entire stack
2. ✅ No connection pool exhaustion detected
3. ✅ Kafka event distribution completed within SLA
4. ✅ Database write latency remained < 100ms
5. ✅ No cascading failures or retry storms observed

### How to Analyze
```bash
# View summary
wireshark swiftpay-load-test.pcap

# Get HTTP statistics
tshark -r swiftpay-load-test.pcap -Y 'http.request' | wc -l

# Check response codes
tshark -r swiftpay-load-test.pcap -Y 'http.response' \
  -T fields -e 'http.response.code' | sort | uniq -c
```
```

## 🎓 What PCAP Proves (For Reviewers)

When reviewers open the PCAP file in Wireshark, they'll see:

1. **Complete Transaction Flow**
   - Every HTTP request with unique transaction ID
   - Corresponding database write
   - Kafka topic event
   - HTTP response with status PENDING/COMPLETED

2. **Timing Accuracy**
   - All events timestamped with microsecond precision
   - Can calculate actual TPS from frame timestamps
   - Proves 250 transactions/second for 30 minutes

3. **System Stability**
   - Connection resets = 0 (rock solid)
   - Retransmissions < 0.1% (excellent network)
   - No protocol errors or timeouts

4. **Data Integrity**
   - HTTP payload size matches expected request size
   - Kafka messages properly formatted
   - Database response codes all successful

5. **Scalability Evidence**
   - 250 concurrent connections maintained steadily
   - No connection pool exhaustion
   - Resource utilization within expected ranges

---

## Summary

**PCAP is the gold standard proof for load testing because it:**

1. ✅ **Irrefutable** - Network packets cannot be faked
2. ✅ **Complete** - Shows every interaction, not just aggregates
3. ✅ **Timestamped** - Proves exacttiming and throughput
4. ✅ **Debuggable** - Reviewers can drill down to specific transactions
5. ✅ **Compliant** - Meets fintech audit and regulatory requirements
6. ✅ **Professional** - Demonstrates system maturity and rigor

For the SwiftPay hackathon submission, including a PCAP trace shows you understand production-grade performance validation and are serious about evidence-based system reliability.

---

**Generated:** May 28, 2026 | **SwiftPay Team**

