#!/bin/bash
#
# SwiftPay - Load Test with PCAP Capture Script
# Performs a 250 TPS load test for 1 million transactions and captures network traffic
#
# Usage:
#   ./load-test-pcap.sh              # Full load test (34 minutes)
#   ./load-test-pcap.sh quick        # Quick test (5 minutes)
#   ./load-test-pcap.sh --help       # Show help
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PCAP_DIR="pcap-captures"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PCAP_FILE="${PCAP_DIR}/swiftpay-load-test-${TIMESTAMP}.pcap"
REPORT_FILE="${PCAP_DIR}/load-test-report-${TIMESTAMP}.md"
API_PORT=8080
KAFKA_PORT=9092
DB_PORT=5432
REDIS_PORT=6379
TEST_MODE="${1:-full}"

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_header() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

show_help() {
    cat << EOF
╔═══════════════════════════════════════════════════════════════╗
║     SwiftPay Load Test with PCAP Capture                      ║
║     250 TPS × 1 Million Transactions                          ║
╚═══════════════════════════════════════════════════════════════╝

USAGE: ./load-test-pcap.sh [OPTIONS]

OPTIONS:
    full        Full load test (34 minutes, ~1M transactions) [DEFAULT]
    quick       Quick test (5 minutes, ~75k transactions)
    help        Show this help message

EXAMPLES:
    ./load-test-pcap.sh                 # Run full load test
    ./load-test-pcap.sh quick           # Run quick test
    ./load-test-pcap.sh --help          # Show help

PREREQUISITES:
    - Docker & Docker Compose running
    - K6 installed: https://k6.io/docs/getting-started/installation/
    - tcpdump installed (Linux/macOS): brew install tcpdump
    - Wireshark (optional): https://www.wireshark.org/

POST-TEST ANALYSIS:
    1. Open PCAP in Wireshark:
       wireshark ${PCAP_DIR}/swiftpay-load-test-*.pcap

    2. Analyze with CLI:
       tshark -r ${PCAP_DIR}/swiftpay-load-test-*.pcap -Y 'http.request' | wc -l

    3. Check for errors:
       tshark -r ${PCAP_DIR}/swiftpay-load-test-*.pcap -Y 'tcp.analysis.flags.retransmission'

DOCUMENTATION:
    See PCAP_ANALYSIS.md for detailed explanation of PCAP and load testing strategies
    See LOAD_TESTING.md for general load testing guidelines

EOF
}

check_prerequisites() {
    log_header "Checking Prerequisites"

    # Check if Docker is running
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker."
        exit 1
    fi

    if ! docker ps > /dev/null 2>&1; then
        log_error "Docker daemon is not running. Please start Docker."
        exit 1
    fi
    log_info "✓ Docker is running"

    # Check if services are running
    if ! curl -f http://localhost:8080/health > /dev/null 2>&1; then
        log_error "SwiftPay API not responding on port 8080"
        echo "   Please start services: docker-compose up -d"
        exit 1
    fi
    log_info "✓ SwiftPay API is running on port 8080"

    # Check if K6 is installed
    if ! command -v k6 &> /dev/null; then
        log_error "K6 is not installed. Please install from: https://k6.io/docs/getting-started/installation/"
        exit 1
    fi
    log_info "✓ K6 is installed ($(k6 version))"

    # Check if tcpdump is available
    if command -v tcpdump &> /dev/null; then
        log_info "✓ tcpdump is available"
        CAPTURE_AVAILABLE=true
    else
        log_warning "tcpdump is not available. Installing per-platform instructions:"
        echo "   Linux:  sudo apt-get install tcpdump"
        echo "   macOS:  brew install tcpdump"
        CAPTURE_AVAILABLE=false
    fi

    # Create PCAP directory
    mkdir -p "$PCAP_DIR"
    log_info "✓ PCAP directory ready: ${PCAP_DIR}"
}

start_pcap_capture() {
    log_header "Starting PCAP Capture"

    if [ "$CAPTURE_AVAILABLE" = false ]; then
        log_warning "PCAP capture disabled (tcpdump not available)"
        return
    fi

    log_info "Capturing packets on ports: ${API_PORT}, ${KAFKA_PORT}, ${DB_PORT}, ${REDIS_PORT}"

    # Determine appropriate network interface
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Try docker0 first, then any
        if ip link show docker0 &> /dev/null; then
            INTERFACE="docker0"
        else
            INTERFACE="any"
        fi
    else
        # macOS
        INTERFACE="en0"  # You may need to adjust this
    fi

    log_info "Using network interface: $INTERFACE"

    # Start tcpdump in background
    sudo tcpdump -i "$INTERFACE" -w "$PCAP_FILE" \
        "tcp port ${API_PORT} or tcp port ${KAFKA_PORT} or tcp port ${DB_PORT} or tcp port ${REDIS_PORT}" \
        2>/dev/null &

    TCPDUMP_PID=$!
    echo $TCPDUMP_PID > "${PCAP_DIR}/.tcpdump.pid"

    log_info "PCAP capture started (PID: $TCPDUMP_PID)"
    log_info "Output file: ${PCAP_FILE}"
    sleep 2
}

stop_pcap_capture() {
    log_header "Stopping PCAP Capture"

    if [ -f "${PCAP_DIR}/.tcpdump.pid" ]; then
        TCPDUMP_PID=$(cat "${PCAP_DIR}/.tcpdump.pid")
        if sudo kill $TCPDUMP_PID 2>/dev/null; then
            log_info "PCAP capture stopped (PID: $TCPDUMP_PID)"
        fi
        rm "${PCAP_DIR}/.tcpdump.pid"
    fi
}

run_load_test() {
    local mode=$1

    log_header "Running Load Test (Mode: $mode)"

    if [ "$mode" == "quick" ]; then
        log_info "Quick test: 10 VUs for 5 minutes"
        k6 run load-test.js --vus 10 --duration 5m
    else
        log_info "Full test: 250 TPS for 34 minutes (ramp-up 2m + sustained 30m + ramp-down 2m)"
        log_info "Expected: ~1,050,000 transactions"
        k6 run load-test.js
    fi
}

get_pcap_statistics() {
    log_header "PCAP Statistics"

    if [ ! -f "$PCAP_FILE" ]; then
        log_warning "PCAP file not found: $PCAP_FILE"
        return
    fi

    log_info "PCAP File: $(basename $PCAP_FILE)"
    log_info "File Size: $(du -h $PCAP_FILE | cut -f1)"

    if command -v tcpdump &> /dev/null; then
        local packet_count=$(tcpdump -r "$PCAP_FILE" -nn -q 2>/dev/null | wc -l)
        log_info "Total Packets: $packet_count"

        # Count packets by port
        local api_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${API_PORT}" 2>/dev/null | wc -l)
        local kafka_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${KAFKA_PORT}" 2>/dev/null | wc -l)
        local db_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${DB_PORT}" 2>/dev/null | wc -l)
        local redis_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${REDIS_PORT}" 2>/dev/null | wc -l)

        log_info "API Traffic (port ${API_PORT}): $api_packets packets"
        log_info "Kafka Traffic (port ${KAFKA_PORT}): $kafka_packets packets"
        log_info "Database Traffic (port ${DB_PORT}): $db_packets packets"
        log_info "Redis Traffic (port ${REDIS_PORT}): $redis_packets packets"
    fi
}

generate_report() {
    log_header "Generating Load Test Report"

    cat > "$REPORT_FILE" << 'EOF'
# SwiftPay Load Test Report
## 250 TPS × 1 Million Transactions

EOF

    echo "**Date:** $(date)" >> "$REPORT_FILE"
    echo "**Test Mode:** $TEST_MODE" >> "$REPORT_FILE"
    echo "**PCAP File:** $(basename $PCAP_FILE)" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"

    echo "## Execution Summary" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"

    if [ "$TEST_MODE" == "quick" ]; then
        echo "- **Duration:** 5 minutes" >> "$REPORT_FILE"
        echo "- **Peak TPS:** 10 VUs" >> "$REPORT_FILE"
        echo "- **Expected Transactions:** ~75,000" >> "$REPORT_FILE"
    else
        echo "- **Duration:** 34 minutes total" >> "$REPORT_FILE"
        echo "  - Ramp-up: 2 minutes (0 → 250 TPS)" >> "$REPORT_FILE"
        echo "  - Sustained: 30 minutes (250 TPS constant)" >> "$REPORT_FILE"
        echo "  - Ramp-down: 2 minutes (250 → 0 TPS)" >> "$REPORT_FILE"
        echo "- **Peak TPS:** 250" >> "$REPORT_FILE"
        echo "- **Expected Transactions:** ~1,050,000" >> "$REPORT_FILE"
    fi

    echo "" >> "$REPORT_FILE"
    echo "## PCAP Capture Details" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"

    if [ -f "$PCAP_FILE" ]; then
        echo "- **File:** $PCAP_FILE" >> "$REPORT_FILE"
        echo "- **Size:** $(du -h $PCAP_FILE | cut -f1)" >> "$REPORT_FILE"

        if command -v tcpdump &> /dev/null; then
            local packet_count=$(tcpdump -r "$PCAP_FILE" -nn -q 2>/dev/null | wc -l)
            echo "- **Packets Captured:** $packet_count" >> "$REPORT_FILE"
            echo "" >> "$REPORT_FILE"
            echo "### Traffic Distribution by Service" >> "$REPORT_FILE"
            echo "" >> "$REPORT_FILE"

            local api_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${API_PORT}" 2>/dev/null | wc -l)
            local kafka_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${KAFKA_PORT}" 2>/dev/null | wc -l)
            local db_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${DB_PORT}" 2>/dev/null | wc -l)
            local redis_packets=$(tcpdump -r "$PCAP_FILE" -nn "port ${REDIS_PORT}" 2>/dev/null | wc -l)

            echo "| Service | Port | Packets | %" >> "$REPORT_FILE"
            echo "|---------|------|---------|---" >> "$REPORT_FILE"
            echo "| API Gateway | ${API_PORT} | $api_packets | $((api_packets * 100 / packet_count))% |" >> "$REPORT_FILE"
            echo "| Kafka | ${KAFKA_PORT} | $kafka_packets | $((kafka_packets * 100 / packet_count))% |" >> "$REPORT_FILE"
            echo "| PostgreSQL | ${DB_PORT} | $db_packets | $((db_packets * 100 / packet_count))% |" >> "$REPORT_FILE"
            echo "| Redis | ${REDIS_PORT} | $redis_packets | $((redis_packets * 100 / packet_count))% |" >> "$REPORT_FILE"
        fi
    else
        echo "- **Status:** PCAP capture was not available" >> "$REPORT_FILE"
    fi

    echo "" >> "$REPORT_FILE"
    echo "## How to Analyze the PCAP" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "\`\`\`bash" >> "$REPORT_FILE"
    echo "# View in Wireshark (GUI)" >> "$REPORT_FILE"
    echo "wireshark $PCAP_FILE" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "# Count HTTP requests" >> "$REPORT_FILE"
    echo "tshark -r $PCAP_FILE -Y 'http.request' | wc -l" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "# Check for TCP retransmissions" >> "$REPORT_FILE"
    echo "tshark -r $PCAP_FILE -Y 'tcp.analysis.retransmission' | wc -l" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "# Check for connection resets" >> "$REPORT_FILE"
    echo "tshark -r $PCAP_FILE -Y 'tcp.flags.reset' | wc -l" >> "$REPORT_FILE"
    echo "\`\`\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "## Next Steps" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "1. **Push PCAP to GitHub** (using Git LFS for large files)" >> "$REPORT_FILE"
    echo "   see: GIT_LFS_SETUP.md" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "2. **Analyze with Wireshark**" >> "$REPORT_FILE"
    echo "   - Open the PCAP file" >> "$REPORT_FILE"
    echo "   - Statistics → Summary (overview)" >> "$REPORT_FILE"
    echo "   - Statistics → Conversations (connection analysis)" >> "$REPORT_FILE"
    echo "   - Statistics → TCP Stream Graph (visualize traffic)" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "3. **Generate Evidence**" >> "$REPORT_FILE"
    echo "   - Export statistics as CSV/PDF" >> "$REPORT_FILE"
    echo "   - Create screenshot of protocol hierarchy" >> "$REPORT_FILE"
    echo "   - Document traffic patterns and timing" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "---" >> "$REPORT_FILE"
    echo "Generated: $(date)" >> "$REPORT_FILE"

    log_info "Report generated: $REPORT_FILE"
}

cleanup_on_exit() {
    log_header "Cleanup"
    stop_pcap_capture
    get_pcap_statistics
    generate_report
    log_info "Load test completed successfully!"
}

# Main execution
main() {
    case "$TEST_MODE" in
        help|--help|-h)
            show_help
            exit 0
            ;;
        quick|full)
            ;;
        *)
            log_error "Unknown test mode: $TEST_MODE"
            echo ""
            show_help
            exit 1
            ;;
    esac

    # Set up exit handler
    trap cleanup_on_exit EXIT

    # Pre-test checks
    check_prerequisites

    # Start PCAP capture
    start_pcap_capture

    # Run load test
    run_load_test "$TEST_MODE"
}

# Execute main function only if not sourced
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi

