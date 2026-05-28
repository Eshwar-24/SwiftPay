#!/bin/bash
#
# SwiftPay Setup and Deployment Script
# This script helps with building, testing, and deploying SwiftPay
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to display usage
display_usage() {
    echo "SwiftPay Setup and Deployment Script"
    echo ""
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  build              Build the application"
    echo "  test               Run tests"
    echo "  docker-build       Build Docker image"
    echo "  docker-up          Start Docker Compose stack"
    echo "  docker-down        Stop Docker Compose stack"
    echo "  docker-logs        View Docker logs"
    echo "  k8s-deploy         Deploy to Kubernetes"
    echo "  k8s-cleanup        Remove Kubernetes deployment"
    echo "  load-test          Run load test"
    echo "  clean              Clean build artifacts"
    echo "  help               Display this help message"
    echo ""
}

# Build function
build() {
    print_status "Building SwiftPay..."
    ./mvnw clean package
    print_status "Build completed successfully!"
}

# Test function
test_app() {
    print_status "Running tests..."
    ./mvnw test
    print_status "Tests completed!"
}

# Docker build function
docker_build() {
    print_status "Building Docker image..."
    docker build -t swiftpay:latest .
    print_status "Docker image built successfully!"
}

# Docker up function
docker_up() {
    print_status "Starting Docker Compose stack..."
    docker-compose up -d
    print_status "Waiting for services to be ready..."
    sleep 10

    print_status "Checking health..."
    curl -f http://localhost:8080/health > /dev/null 2>&1 || {
        print_warning "Application may still be starting..."
    }

    print_status "Services started successfully!"
    echo ""
    echo "Access points:"
    echo "  API:       http://localhost:8080"
    echo "  Swagger:   http://localhost:8080/swagger-ui.html"
    echo "  Database:  localhost:5432"
    echo "  Kafka:     localhost:9092"
    echo "  Redis:     localhost:6379"
}

# Docker down function
docker_down() {
    print_status "Stopping Docker Compose stack..."
    docker-compose down
    print_status "Services stopped!"
}

# Docker logs function
docker_logs() {
    print_status "Fetching logs..."
    docker-compose logs -f swiftpay
}

# Kubernetes deploy function
k8s_deploy() {
    print_status "Deploying to Kubernetes..."

    if ! command_exists kubectl; then
        print_error "kubectl is not installed"
        exit 1
    fi

    print_status "Creating namespace and deploying resources..."
    kubectl apply -f k8s-deployment.yaml

    print_status "Waiting for pods to be ready..."
    kubectl wait --for=condition=ready pod \
        -l app=swiftpay -n swiftpay \
        --timeout=300s || true

    print_status "Deployment completed! Access via:"
    echo "  kubectl port-forward svc/swiftpay-service 8080:80 -n swiftpay"
}

# Kubernetes cleanup function
k8s_cleanup() {
    print_status "Removing Kubernetes deployment..."
    kubectl delete namespace swiftpay || true
    print_status "Kubernetes resources cleaned up!"
}

# Load test function
load_test() {
    print_status "Running load test..."

    if ! command_exists k6; then
        print_error "K6 is not installed. Install from: https://k6.io/docs/getting-started/installation/"
        exit 1
    fi

    print_status "Starting load test (250 TPS for 30 minutes)..."
    k6 run load-test.js

    print_status "Load test completed!"
}

# Clean function
clean() {
    print_status "Cleaning build artifacts..."
    ./mvnw clean
    rm -rf target/
    print_status "Clean completed!"
}

# Main script logic
main() {
    if [ $# -eq 0 ]; then
        display_usage
        exit 0
    fi

    case "$1" in
        build)
            build
            ;;
        test)
            test_app
            ;;
        docker-build)
            docker_build
            ;;
        docker-up)
            docker_up
            ;;
        docker-down)
            docker_down
            ;;
        docker-logs)
            docker_logs
            ;;
        k8s-deploy)
            k8s_deploy
            ;;
        k8s-cleanup)
            k8s_cleanup
            ;;
        load-test)
            load_test
            ;;
        clean)
            clean
            ;;
        help|--help|-h)
            display_usage
            ;;
        *)
            print_error "Unknown command: $1"
            display_usage
            exit 1
            ;;
    esac
}

# Run main function
main "$@"

