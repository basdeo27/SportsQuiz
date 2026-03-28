#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-latest}"
IMAGE_NAME="sports-quiz-backend:${TAG}"

echo "Building ${IMAGE_NAME} (linux/amd64)..."
docker build --platform=linux/amd64 -t "${IMAGE_NAME}" .
echo "Done."
