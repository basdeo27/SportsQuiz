#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <aws_account_id> <aws_region> <ecr_repo_name> [image_tag]"
  exit 1
fi

ACCOUNT_ID="$1"
REGION="$2"
REPO="$3"
TAG="${4:-latest}"

IMAGE_LOCAL="sports-quiz-backend:${TAG}"
IMAGE_REMOTE="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPO}:${TAG}"

echo "Logging in to ECR..."
aws ecr get-login-password --region "${REGION}" | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo "Ensuring ECR repository exists..."
if ! aws ecr describe-repositories --repository-names "${REPO}" --region "${REGION}" >/dev/null 2>&1; then
  aws ecr create-repository --repository-name "${REPO}" --region "${REGION}" >/dev/null
fi

echo "Building image (linux/amd64)..."
docker build --platform=linux/amd64 -t "${IMAGE_LOCAL}" .

echo "Tagging image..."
docker tag "${IMAGE_LOCAL}" "${IMAGE_REMOTE}"

echo "Pushing image..."
docker push "${IMAGE_REMOTE}"

echo "Done. Pushed ${IMAGE_REMOTE}"
