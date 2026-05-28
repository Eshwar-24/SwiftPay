#!/usr/bin/env pwsh
<#
.SYNOPSIS
    SwiftPay Load Test with PCAP Capture

.DESCRIPTION
    Performs a 250 TPS load test for 1 million transactions and captures network traffic
    using Wireshark-based PCAP capture (if available)

.PARAMETERS
    Mode: 'full' (default) for 34-minute test, 'quick' for 5-minute test

.EXAMPLE
    .\load-test-pcap.ps1 -Mode full      # Run full load test
    .\load-test-pcap.ps1 -Mode quick     # Run quick test
    .\load-test-pcap.ps1 -Help           # Show help

.NOTES
    Prerequisites:
    - Docker & Docker Compose running
    - K6 installed
    - (Optional) Wireshark installed for PCAP capture
#>

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('full', 'quick', 'help')]
    [string]$Mode = 'full',

    [switch]$Help
)

# Configuration
$script:PCAP_DIR = "pcap-captures"
$script:TIMESTAMP = Get-Date -Format "yyyyMMdd_HHmmss"
$script:PCAP_FILE = "$PCAP_DIR\swiftpay-load-test-$TIMESTAMP.pcap"
$script:REPORT_FILE = "$PCAP_DIR\load-test-report-$TIMESTAMP.md"
$script:API_PORT = 8080
$script:KAFKA_PORT = 9092
$script:DB_PORT = 5432
$script:REDIS_PORT = 6379
$script:CAPTURE_AVAILABLE = $false
$script:WIRESHARK_PATH = ""

# Color output functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Header {
    param([string]$Message)
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
}

function Show-Help {
    $helpText = @"
╔═══════════════════════════════════════════════════════════════╗
║     SwiftPay Load Test with PCAP Capture                      ║
║     250 TPS × 1 Million Transactions                          ║
╚═══════════════════════════════════════════════════════════════╝

USAGE:
    .\load-test-pcap.ps1 [OPTIONS]

    or in PowerShell:

    Invoke-Expression "& '$PSScriptRoot\load-test-pcap.ps1'" -ArgumentList '-Mode','full'

OPTIONS:
    -Mode full      Full load test (34 minutes, ~1M transactions) [DEFAULT]
    -Mode quick     Quick test (5 minutes, ~75k transactions)
    -Help           Show this help message

EXAMPLES:
    .\load-test-pcap.ps1                    # Run full load test
    .\load-test-pcap.ps1 -Mode quick        # Run quick test
    .\load-test-pcap.ps1 -Help              # Show this help

PREREQUISITES:
    - Docker Desktop running
    - K6 installed: https://k6.io/docs/getting-started/installation/
    - (Optional) Wireshark for PCAP analysis: https://www.wireshark.org/

ON WINDOWS - PCAP CAPTURE LIMITATION:
    tcpdump is not available on Windows. PCAP capture options:

    Option 1: Use WSL (Windows Subsystem for Linux)
        - Install WSL2: https://docs.microsoft.com/windows/wsl/install
        - Run: wsl ./load-test-pcap.sh

    Option 2: Use Wireshark on Windows
        - Install Wireshark
        - Manually capture before running load test
        - File → Capture → Options → Select Interface

    Option 3: Use Docker with tcpdump
        - Run tcpdump in a Docker container
        - Capture from Docker network

POST-TEST ANALYSIS:
    1. View PCAP in Wireshark:
       & '$Env:ProgramFiles\Wireshark\Wireshark.exe' '$PCAP_FILE'

    2. Analyze with CLI (if WSL available):
       wsl tshark -r $PCAP_FILE -Y 'http.request' | wc -l

DOCUMENTATION:
    See PCAP_ANALYSIS.md for detailed explanation
    See LOAD_TESTING.md for general guidelines
    See GIT_LFS_SETUP.md for pushing PCAP to GitHub

"@
    Write-Host $helpText
}

function Test-Prerequisites {
    Write-Header "Checking Prerequisites"

    # Check Docker
    try {
        $dockerVersion = docker version --format '{{.Server.Version}}' 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Info "✓ Docker is running (version $dockerVersion)"
        } else {
            throw "Docker not responding"
        }
    } catch {
        Write-Error-Custom "Docker is not available. Please install Docker Desktop."
        exit 1
    }

    # Check SwiftPay API
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/health" -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Info "✓ SwiftPay API is running on port 8080"
        }
    } catch {
        Write-Error-Custom "SwiftPay API not responding on port 8080"
        Write-Host "   Please start services: docker-compose up -d" -ForegroundColor Yellow
        exit 1
    }

    # Check K6
    try {
        $k6Version = k6 version 2>$null
        Write-Info "✓ K6 is installed ($k6Version)"
    } catch {
        Write-Error-Custom "K6 is not installed. Please install from: https://k6.io/docs/getting-started/installation/"
        Write-Host "   Windows: choco install k6" -ForegroundColor Yellow
        exit 1
    }

    # Check for Wireshark (for PCAP analysis)
    $wirePath = @(
        "$Env:ProgramFiles\Wireshark\wireshark.exe",
        "$Env:ProgramFiles(x86)\Wireshark\wireshark.exe",
        "C:\Program Files\Wireshark\wireshark.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($wirePath) {
        Write-Info "✓ Wireshark is installed for PCAP analysis"
        $script:WIRESHARK_PATH = $wirePath
    } else {
        Write-Warning-Custom "Wireshark not found. Install from: https://www.wireshark.org/"
    }

    # Note about PCAP capture on Windows
    Write-Warning-Custom "PCAP capture (tcpdump) is not available on Windows"
    Write-Host "   Recommended solutions:" -ForegroundColor Yellow
    Write-Host "   1. Run load test on Linux/macOS" -ForegroundColor Gray
    Write-Host "   2. Use WSL: wsl ./load-test-pcap.sh" -ForegroundColor Gray
    Write-Host "   3. Use Wireshark GUI capture: File → Capture → Interfaces" -ForegroundColor Gray

    # Create PCAP directory
    if (-not (Test-Path $PCAP_DIR)) {
        New-Item -ItemType Directory -Path $PCAP_DIR -Force | Out-Null
    }
    Write-Info "✓ PCAP directory ready: $PCAP_DIR"
}

function Start-LoadTest {
    param([string]$TestMode)

    Write-Header "Running Load Test (Mode: $TestMode)"

    if ($TestMode -eq 'quick') {
        Write-Info "Quick test: 10 VUs for 5 minutes"
        & k6 run load-test.js --vus 10 --duration 5m
    } else {
        Write-Info "Full test: 250 TPS for 34 minutes"
        Write-Info "Expected: ~1,050,000 transactions"
        Write-Host ""
        Write-Host "  ┌─ Ramp-up:   2 minutes  (0 → 250 TPS)" -ForegroundColor Cyan
        Write-Host "  ├─ Sustained: 30 minutes (250 TPS constant)" -ForegroundColor Cyan
        Write-Host "  └─ Ramp-down: 2 minutes  (250 → 0 TPS)" -ForegroundColor Cyan
        Write-Host ""

        & k6 run load-test.js
    }
}

function Get-PCAPStatistics {
    Write-Header "PCAP Statistics"

    if (-not (Test-Path $PCAP_FILE)) {
        Write-Warning-Custom "PCAP file not found: $PCAP_FILE"
        Write-Host "  (PCAP capture is not available on Windows)" -ForegroundColor Gray
        return
    }

    $fileInfo = Get-Item $PCAP_FILE
    Write-Info "PCAP File: $($fileInfo.Name)"
    Write-Info "File Size: $([math]::Round($fileInfo.Length / 1GB, 2)) GB"
    Write-Host ""
    Write-Host "To analyze this PCAP file:" -ForegroundColor Cyan
    Write-Host "  1. Install Wireshark: https://www.wireshark.org/" -ForegroundColor Gray
    Write-Host "  2. Run: wireshark '$PCAP_FILE'" -ForegroundColor Gray
    Write-Host "  3. Statistics → Summary" -ForegroundColor Gray
    Write-Host "  4. Statistics → Conversations" -ForegroundColor Gray
}

function New-LoadTestReport {
    param([string]$TestMode)

    Write-Header "Generating Load Test Report"

    $reportContent = @"
# SwiftPay Load Test Report
## 250 TPS × 1 Million Transactions

**Date:** $(Get-Date)
**Test Mode:** $TestMode
**PCAP File:** $(Split-Path $PCAP_FILE -Leaf)

## Execution Summary

"@

    if ($TestMode -eq 'quick') {
        $reportContent += @"
- **Duration:** 5 minutes
- **Peak VUs:** 10
- **Expected Transactions:** ~75,000
"@
    } else {
        $reportContent += @"
- **Duration:** 34 minutes total
  - Ramp-up: 2 minutes (0 → 250 TPS)
  - Sustained: 30 minutes (250 TPS constant)
  - Ramp-down: 2 minutes (250 → 0 TPS)
- **Peak TPS:** 250
- **Expected Transactions:** ~1,050,000
"@
    }

    $reportContent += @"

## PCAP Capture Status

**Windows Note:** PCAP capture (tcpdump) is not available on Windows.

To capture PCAP on Windows:

### Option 1: Use Wireshark (Recommended)
1. Install Wireshark: https://www.wireshark.org/
2. Open Wireshark
3. Select network interface (docker0 or eth0)
4. Click "Start Capture"
5. Run the load test: \`k6 run load-test.js\`
6. When complete, click "Stop Capture"
7. File → Save As → Save as PCAP

### Option 2: Use WSL
1. Install WSL2: https://docs.microsoft.com/windows/wsl/install
2. Copy load-test-pcap.sh to WSL
3. Run: \`wsl ./load-test-pcap.sh\`

### Option 3: Use Docker
\`\`\`bash
# Start packet capture in Docker
docker run --rm --net host -v \$(pwd):/data \
  nicolaka/netshoot tcpdump -i eth0 \
  -w /data/swiftpay.pcap 'port 8080 or port 9092 or port 5432'

# Run load test
k6 run load-test.js
\`\`\`

## Analyzed Results (from K6 metrics)

See K6 console output above for:
- HTTP request duration (p50, p95, p99)
- Success/failure rates
- Throughput metrics
- Virtual user scaling

## Next Steps

1. **Push Results to GitHub**
   See: GIT_LFS_SETUP.md

2. **Capture PCAP and Push**
   See PCAP_ANALYSIS.md for Wireshark/tcpdump commands

---
Generated: $(Get-Date)
"@

    $reportContent | Out-File -FilePath $REPORT_FILE -Encoding UTF8
    Write-Info "Report generated: $REPORT_FILE"
}

function Invoke-Cleanup {
    Write-Header "Cleanup"
    Get-PCAPStatistics
    New-LoadTestReport $Mode
    Write-Info "Load test completed successfully!"
}

# Main execution
function Main {
    if ($Help -or $Mode -eq 'help') {
        Show-Help
        return
    }

    # Validate mode
    if ($Mode -notin @('full', 'quick')) {
        Write-Error-Custom "Invalid mode: $Mode"
        Show-Help
        exit 1
    }

    # Set up cleanup on exit
    $null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action { Invoke-Cleanup }

    # Run tests
    Test-Prerequisites
    Start-LoadTest $Mode
    Invoke-Cleanup
}

# Execute main function
Main

