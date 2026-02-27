#!/usr/bin/env bash

# Script to start the containerized application stack
# Can be run from anywhere; it resolves the project root based on this script's location.

set -u
set -o pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Starting Containerized Application Stack ==="
echo

# Check if Docker is available
if ! command -v docker >/dev/null 2>&1; then
  echo -e "${RED}ERROR: 'docker' command not found on PATH.${NC}"
  echo "      Please install Docker and make sure it is available on PATH."
  exit 1
fi

# Check if Docker Compose is available
if ! command -v docker compose >/dev/null 2>&1; then
  echo -e "${RED}ERROR: 'docker compose' command not found on PATH.${NC}"
  echo "      Please install Docker Compose and make sure it is available on PATH."
  exit 1
fi

# Check if Docker daemon is running
if ! docker info >/dev/null 2>&1; then
  echo -e "${RED}ERROR: Docker daemon is not running.${NC}"
  echo "      Please start Docker Desktop or Docker daemon and try again."
  exit 1
fi

echo -e "${GREEN}Docker: OK${NC}"
echo -e "${GREEN}Docker Compose: OK${NC}"
echo

# Change to project root
cd "${PROJECT_ROOT}" || {
  echo -e "${RED}ERROR: Could not change to project root: ${PROJECT_ROOT}${NC}"
  exit 1
}

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ]; then
  echo -e "${RED}ERROR: docker-compose.yml not found in project root.${NC}"
  exit 1
fi

# Parse command line arguments
BUILD=false
DETACHED=true
FOLLOW_LOGS=false
STOP=false
CLEAN=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --build|-b)
      BUILD=true
      shift
      ;;
    --logs|-l)
      FOLLOW_LOGS=true
      DETACHED=false
      shift
      ;;
    --stop|-s)
      STOP=true
      shift
      ;;
    --clean|-c)
      CLEAN=true
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo
      echo "Options:"
      echo "  --build, -b      Build images before starting containers"
      echo "  --logs, -l       Follow logs after starting (runs in foreground)"
      echo "  --stop, -s       Stop running containers"
      echo "  --clean, -c      Stop containers and remove volumes (clean slate)"
      echo "  --help, -h       Show this help message"
      echo
      echo "Examples:"
      echo "  $0                Start containers in background"
      echo "  $0 --build        Build and start containers"
      echo "  $0 --logs         Start containers and follow logs"
      echo "  $0 --stop         Stop running containers"
      echo "  $0 --clean        Stop containers and remove volumes"
      exit 0
      ;;
    *)
      echo -e "${RED}ERROR: Unknown option: $1${NC}"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Handle stop command
if [ "$STOP" = true ]; then
  echo "Stopping containers..."
  docker compose down
  exit 0
fi

# Handle clean command
if [ "$CLEAN" = true ]; then
  echo -e "${YELLOW}WARNING: This will stop containers and remove all volumes (data will be lost).${NC}"
  read -p "Are you sure? (y/N) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Stopping containers and removing volumes..."
    docker compose down -v
    echo -e "${GREEN}Cleanup completed.${NC}"
  else
    echo "Cancelled."
  fi
  exit 0
fi

# Build if requested
if [ "$BUILD" = true ]; then
  echo "Building Docker images..."
  docker compose build
  if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Docker build failed.${NC}"
    exit 1
  fi
  echo -e "${GREEN}Build completed successfully.${NC}"
  echo
fi

# Start containers
echo "Starting containers..."
if [ "$DETACHED" = true ]; then
  docker compose up -d
else
  docker compose up
  exit $?
fi

if [ $? -ne 0 ]; then
  echo -e "${RED}ERROR: Failed to start containers.${NC}"
  exit 1
fi

echo -e "${GREEN}Containers started successfully.${NC}"
echo

# Show container status
echo "=== Container Status ==="
docker compose ps
echo

# Show logs if requested
if [ "$FOLLOW_LOGS" = true ]; then
  echo "=== Following logs (Ctrl+C to exit) ==="
  docker compose logs -f
else
  echo "=== Application Logs (last 20 lines) ==="
  docker compose logs --tail=20 sample-service
  echo
  echo "To view logs: docker compose logs -f sample-service"
  echo "To stop containers: docker compose down"
  echo "Or run: $0 --stop"
fi

echo
echo -e "${GREEN}Application is available at: http://localhost:8080${NC}"

