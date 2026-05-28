#!/bin/bash
#
# Windows PowerShell version of deploy script
#

param(
    [Parameter(Mandatory=$false)]
    [string]$Command
)

function Print-Status {
    Write-Host "[INFO] $args" -ForegroundColor Green
}

function Print-Error {
    Write-Host "[ERROR] $args" -ForegroundColor Red
}

function Print-Warning {
    Write-Host "[WARN] $args" -ForegroundColor Yellow
}

function Display-Usage {
    Write-Host "SwiftPay Setup and Deployment Script"
    Write-Host ""
    Write-Host "Usage: .\deploy.ps1 -Command <COMMAND>"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  build              Build the application"
    Write-Host "  test               Run tests"
    Write-Host "  docker-build       Build Docker image"
    Write-Host "  docker-up          Start Docker Compose stack"
    Write-Host "  docker-down        Stop Docker Compose stack"
    Write-Host "  docker-logs        View Docker logs"
    Write-Host "  k8s-deploy         Deploy to Kubernetes"
    Write-Host "  k8s-cleanup        Remove Kubernetes deployment"
    Write-Host "  load-test          Run load test"
    Write-Host "  clean              Clean build artifacts"
    Write-Host "  help               Display this help message"
    Write-Host ""
}

function Build-App {
    Print-Status "Building SwiftPay..."
    .\mvnw.cmd clean package
    Print-Status "Build completed successfully!"
}

function Test-App {
    Print-Status "Running tests..."
    .\mvnw.cmd test
    Print-Status "Tests completed!"
}

function Docker-Build {
    Print-Status "Building Docker image..."
    docker build -t swiftpay:latest .
    Print-Status "Docker image built successfully!"
}

function Docker-Up {
    Print-Status "Starting Docker Compose stack..."
    docker-compose up -d
    Print-Status "Waiting for services to be ready..."
    Start-Sleep -Seconds 10

    Print-Status "Checking health..."
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/health" -ErrorAction Stop | Out-Null
    } catch {
        Print-Warning "Application may still be starting..."
    }

    Print-Status "Services started successfully!"
    Write-Host ""
    Write-Host "Access points:"
    Write-Host "  API:       http://localhost:8080"
    Write-Host "  Swagger:   http://localhost:8080/swagger-ui.html"
    Write-Host "  Database:  localhost:5432"
    Write-Host "  Kafka:     localhost:9092"
    Write-Host "  Redis:     localhost:6379"
}

function Docker-Down {
    Print-Status "Stopping Docker Compose stack..."
    docker-compose down
    Print-Status "Services stopped!"
}

function Docker-Logs {
    Print-Status "Fetching logs..."
    docker-compose logs -f swiftpay
}

function K8s-Deploy {
    Print-Status "Deploying to Kubernetes..."

    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
        Print-Error "kubectl is not installed"
        exit 1
    }

    Print-Status "Creating namespace and deploying resources..."
    kubectl apply -f k8s-deployment.yaml

    Print-Status "Waiting for pods to be ready..."
    kubectl wait --for=condition=ready pod `
        -l app=swiftpay -n swiftpay `
        --timeout=300s

    Print-Status "Deployment completed! Access via:"
    Write-Host "  kubectl port-forward svc/swiftpay-service 8080:80 -n swiftpay"
}

function K8s-Cleanup {
    Print-Status "Removing Kubernetes deployment..."
    kubectl delete namespace swiftpay
    Print-Status "Kubernetes resources cleaned up!"
}

function Load-Test {
    Print-Status "Running load test..."

    if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
        Print-Error "K6 is not installed. Install from: https://k6.io/docs/getting-started/installation/"
        exit 1
    }

    Print-Status "Starting load test (250 TPS for 30 minutes)..."
    k6 run load-test.js

    Print-Status "Load test completed!"
}

function Clean-App {
    Print-Status "Cleaning build artifacts..."
    .\mvnw.cmd clean
    Remove-Item -Path "target" -Recurse -Force -ErrorAction SilentlyContinue
    Print-Status "Clean completed!"
}

# Main logic
if ([string]::IsNullOrEmpty($Command)) {
    Display-Usage
} else {
    switch ($Command) {
        "build" { Build-App }
        "test" { Test-App }
        "docker-build" { Docker-Build }
        "docker-up" { Docker-Up }
        "docker-down" { Docker-Down }
        "docker-logs" { Docker-Logs }
        "k8s-deploy" { K8s-Deploy }
        "k8s-cleanup" { K8s-Cleanup }
        "load-test" { Load-Test }
        "clean" { Clean-App }
        "help" { Display-Usage }
        default {
            Print-Error "Unknown command: $Command"
            Display-Usage
            exit 1
        }
    }
}

