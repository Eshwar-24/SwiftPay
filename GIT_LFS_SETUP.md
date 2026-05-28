# Git LFS Setup Guide for PCAP Files

## 📋 Overview

PCAP files from load testing can be **2-5 GB in size**, which exceeds GitHub's 100 MB file limit for standard git. To store and version-control PCAP files, we use **Git LFS (Large File Storage)**.

## 🔍 What is Git LFS?

**Git LFS** is an extension that:
- Replaces large files with text pointers in the git repository
- Stores the actual large files on a separate LFS server (GitHub's server)
- Keeps repository size small while allowing tracking of large artifacts
- Provides version history for large files
- Works transparently with standard git commands

### Git LFS vs Standard Git

| Aspect | Standard Git | Git LFS |
|--------|-------------|---------|
| Max file size | 100 MB (soft limit) | Unlimited |
| Repository size | Grows with files | Stays small |
| Clone time | Slow for large repos | Fast (pointers only) |
| Perfect for | Source code | PCAP, Videos, Models, Binaries |

## 📦 Installation

### Windows (Administrator Required)

#### Option 1: Using Chocolatey
```powershell
choco install git-lfs
```

#### Option 2: Using Git for Windows
Git LFS comes pre-installed with modern versions of Git for Windows (v2.35+).
Verify:
```bash
git lfs version
```

#### Option 3: Manual Installation
1. Download from: https://github.com/git-lfs/git-lfs/releases
2. Run the installer
3. Verify installation:
   ```bash
   git lfs version
   ```

### macOS

```bash
brew install git-lfs
git lfs install
```

### Linux (Ubuntu/Debian)

```bash
curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | sudo bash
sudo apt-get install git-lfs
git lfs install
```

## 🚀 Setup for SwiftPay Project

### Step 1: Configure Git LFS Globally (One-time)

```bash
git lfs install
```

This command:
- Installs LFS hooks in your git configuration
- Enables LFS support for all future repositories
- Creates `.gitattributes` entry for tracking

### Step 2: Configure PCAP Files for LFS

Navigate to your SwiftPay repository:

```bash
cd C:\Users\Manyu Manchala\java-Hackathon
cd SwiftPay
```

Tell Git LFS to manage PCAP files:

```bash
git lfs track "*.pcap"
```

This creates/updates `.gitattributes` file with:
```
*.pcap filter=lfs diff=lfs merge=lfs -text
```

### Step 3: Commit Git Attributes

```bash
git add .gitattributes
git commit -m "Configure Git LFS for PCAP files"
git push origin main
```

## 📤 Pushing PCAP Files to GitHub

### After Load Test Completes

```bash
# 1. Navigate to SwiftPay directory
cd C:\Users\Manyu Manchala\java-Hackathon\SwiftPay

# 2. Add the PCAP file(s)
git add pcap-captures/*.pcap

# 3. Check that LFS is tracking it
git lfs ls-files

# Expected output:
# <hash> * pcap-captures/swiftpay-load-test-20260528_120000.pcap

# 4. Commit with descriptive message
git commit -m "Add PCAP trace: 250 TPS × 1M transactions load test

- Load test duration: 34 minutes (2m ramp-up + 30m sustained + 2m ramp-down)
- Peak throughput: 250 transactions per second
- Total transactions: ~1.05 million
- Network services captured: API, Kafka, PostgreSQL, Redis
- File size: ~4.2 GB (compressed with gzip)
- Test timestamp: 2026-05-28 12:00:00 UTC

This PCAP provides network-level evidence of successful completion of the
load test with all components functioning correctly under sustained load."

# 5. Push to GitHub
git push origin main

# 6. Verify the push was successful
git lfs ls-files --all

# You should see:
# <hash> * pcap-captures/swiftpay-load-test-*.pcap (1 of 1 files, * MB)
```

## 🔍 Verifying LFS Configuration

### Check if PCAP is tracked by LFS

```bash
# Show all LFS-tracked files
git lfs ls-files

# Expected output:
# 7b32a... * pcap-captures/swiftpay-load-test-20260528_120000.pcap

# View the file pointer (not the actual content)
cat pcap-captures/swiftpay-load-test-20260528_120000.pcap

# Expected output:
# version https://git-lfs.github.com/spec/v1
# oid sha256:7b32a...
# size 4500000000
```

### View LFS pointer file

```bash
# Raw pointer
git show HEAD:pcap-captures/swiftpay-load-test-20260528_120000.pcap

# With human-readable info
git lfs show pcap-captures/*.pcap
```

## 🗂️ Organizing PCAP Artifacts

### Recommended Directory Structure

```
SwiftPay/
├── pcap-captures/
│   ├── swiftpay-load-test-20260528_120000.pcap
│   ├── swiftpay-load-test-20260528_120000-analysis.md
│   ├── load-test-report-20260528_120000.md
│   └── wireshark-screenshots/
│       ├── overall-summary.png
│       ├── http-statistics.png
│       └── tcp-stream-graph.png
├── PCAP_ANALYSIS.md          # Explains why PCAP is needed
├── load-test-pcap.sh         # Linux/macOS script
├── load-test-pcap.ps1        # Windows script
└── GIT_LFS_SETUP.md          # This file
```

## 📊 PCAP Analysis and Documentation

After pushing PCAP to GitHub:

### 1. Add Analysis Report

The automation scripts generate `load-test-report-*.md`. Add it alongside:

```bash
git add pcap-captures/load-test-report-*.md
git commit -m "Add load test analysis report"
git push origin main
```

### 2. Include Wireshark Screenshots

Analysis proof:

```bash
# In Wireshark:
# File → Export Specified Packets → PNG
# Statistics → Summary (screenshot)
# Statistics → TCP Stream Graphs (screenshot)

git add pcap-captures/wireshark-screenshots/
git commit -m "Add Wireshark analysis screenshots"
git push origin main
```

### 3. Create Summary in README

Update `README.md` to reference PCAP results:

```markdown
## Load Test Results

### PCAP Evidence of 250 TPS Load Test

- **PCAP File:** `pcap-captures/swiftpay-load-test-20260528_120000.pcap` (4.2 GB)
- **Duration:** 34 minutes (2m ramp-up + 30m sustained + 2m ramp-down)
- **Peak Throughput:** 250 TPS
- **Total Transactions:** 1,050,000
- **Network Trace:** Complete capture of API, Kafka, PostgreSQL, Redis traffic

See [PCAP_ANALYSIS.md](PCAP_ANALYSIS.md) for detailed explanation and analysis instructions.

### How to Review the PCAP

1. Clone the repository with LFS:
   ```bash
   git clone --depth 1 <repo-url>
   cd SwiftPay
   ```

2. Open in Wireshark:
   ```bash
   wireshark pcap-captures/swiftpay-load-test-*.pcap
   ```

3. Analyze using command-line:
   ```bash
   # Count HTTP transactions
   tshark -r pcap-captures/*.pcap -Y 'http.request' | wc -l
   
   # Check for errors
   tshark -r pcap-captures/*.pcap -Y 'tcp.analysis.retransmission'
   ```

See [LOAD_TESTING.md](LOAD_TESTING.md) for complete analysis guide.
```

## ⚙️ Advanced Git LFS Operations

### Tracking Multiple File Types

```bash
# Track verschiedene file formats
git lfs track "*.pcap"
git lfs track "*.mp4"
git lfs track "*.bin"

# Or track by path
git lfs track "pcap-captures/*"
git lfs track "load-test-results/*"

# Verify all tracked
cat .gitattributes
```

### Migrating Existing PCAP Files

If you already committed PCAP files before setting up LFS:

```bash
# 1. Remove from git history
git rm --cached pcap-captures/*.pcap

# 2. Configure LFS tracking
git lfs track "*.pcap"

# 3. Re-add the files
git add pcap-captures/*.pcap
git add .gitattributes

# 4. Commit and push
git commit -m "Migrate PCAP files to Git LFS"
git push origin main
```

### Pruning Old LFS Objects

```bash
# Delete old PCAP versions to save space
git lfs prune

# Force delete all old versions
git lfs prune --force
```

## 🐛 Troubleshooting

### Issue: File is > 100MB on GitHub

**Cause:** Git LFS wasn't configured before committing

**Solution:**
1. Remove from git history: `git rm --cached <file>`
2. Set up LFS: `git lfs track "*.pcap"`
3. Re-add and commit

### Issue: "Batch request: missing Authorization header"

**Cause:** GitHub credentials not configured for LFS

**Solution:**
```bash
# Re-authenticate with GitHub
git config --global --unset credential.helper
git config --global credential.helper osxkeychain  # macOS
# or
git config --global credential.helper wincred      # Windows
# or
git config --global credential.helper cache        # Linux

# Test connection
git lfs version
```

### Issue: Large bandwidth usage on clone

**Solution:** Shallow clone with LFS filter
```bash
# Clone without downloading LFS files initially
GIT_LFS_SKIP_SMUDGE=1 git clone <repo-url>

# Later, download specific LFS files
git lfs pull --exclude="" --include="*.pcap"
```

### Issue: PCAP file is "locked" or "sparse"

**Solution:** Force update from remote
```bash
# Force synchronize with remote
git lfs fetch --all
git lfs checkout
```

## 📈 Storage and Bandwidth

### GitHub LFS Quotas

| Plan | Storage | Bandwidth |
|------|---------|-----------|
| Free | 1 GB | 1 GB/month |
| Pro | 50 GB | 50 GB/month |
| Team | 200 GB | 200 GB/month |
| Enterprise | Custom | Custom |

**Note:** Each clone/download consumes bandwidth. "1 GB PCAP + 10 clones = 10 GB bandwidth used"

### Cost Optimization

```bash
# Compress PCAP before uploading
gzip -9 swiftpay-load-test.pcap
# 5.0 GB → 0.5 GB (10x compression)

# Push compressed version
git add swiftpay-load-test.pcap.gz
git commit -m "Add compressed PCAP"
git push
```

## ✅ Final Verification Checklist

- [ ] Git LFS installed: `git lfs version`
- [ ] PCAP files tracked: `git lfs ls-files`
- [ ] `.gitattributes` committed: `git log --name-only | grep attributes`
- [ ] PCAP file pushed: Check GitHub repository
- [ ] Clone test: `git clone` and verify PCAP downloads
- [ ] Wireshark can open: `wireshark <pcap-file>`
- [ ] Documentation updated: README.md includes PCAP reference
- [ ] Analysis done: LOAD_TESTING.md + PCAP_ANALYSIS.md available
- [ ] Report committed: `load-test-report-*.md` in repo

## 📚 Additional Resources

- [Git LFS Official Docs](https://github.com/git-lfs/git-lfs)
- [GitHub LFS Documentation](https://docs.github.com/en/repositories/working-with-files/managing-large-files)
- [Bandwidth Calculator](https://github.com/git-lfs/git-lfs/wiki/Bandwidth-and-Storage-Quotas)
- [Wireshark PCAP Format](https://www.wireshark.org/docs/wsug_html_chunked/ChIOOpen.html)

---

## Quick Reference

```bash
# One-time setup
git lfs install
git lfs track "*.pcap"
git add .gitattributes

# After load test
git add pcap-captures/*.pcap
git commit -m "Add PCAP trace from load test"
git push origin main

# Verify
git lfs ls-files
```

---

**Last Updated:** May 28, 2026 | SwiftPay Development Team

