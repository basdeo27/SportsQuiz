#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <aws_region> <s3_bucket> <cloudfront_distribution_id> [api_base_url]"
  echo "Example: $0 us-east-1 my-bucket ABCD1234 https://api.example.com"
  exit 1
fi

REGION="$1"
BUCKET="$2"
DIST_ID="$3"
API_BASE_URL="${4:-}"

echo "Building frontend..."
pushd frontend >/dev/null
npm install
if [[ -n "${API_BASE_URL}" ]]; then
  VITE_API_BASE_URL="${API_BASE_URL}" npm run build
else
  npm run build
fi
popd >/dev/null

echo "Syncing to s3://${BUCKET}..."
aws s3 sync frontend/dist "s3://${BUCKET}" --delete --region "${REGION}"

if [[ -n "${DIST_ID}" ]]; then
  echo "Invalidating CloudFront distribution ${DIST_ID}..."
  aws cloudfront create-invalidation --distribution-id "${DIST_ID}" --paths "/*" >/dev/null
fi

echo "Done."
