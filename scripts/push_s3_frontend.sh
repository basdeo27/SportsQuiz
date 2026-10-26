#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <aws_region> <s3_bucket> <cloudfront_distribution_id> [backend_root_url]"
  echo "Example: $0 us-east-1 my-bucket ABCD1234 https://api.example.com"
  exit 1
fi

REGION="$1"
BUCKET="$2"
DIST_ID="$3"
BACKEND_URL="${4:-}"

if [[ -n "$BACKEND_URL" ]]; then
  CLOUDFRONT_DISTRIBUTION_ID="$DIST_ID" BACKEND_URL="$BACKEND_URL" bash scripts/configure_api_proxy.sh
else
  echo "Using existing CloudFront API routing (pass a backend root URL to configure it)."
fi

echo "Building frontend..."
pushd frontend >/dev/null
npm ci
VITE_API_BASE_URL="" npm run build
popd >/dev/null

echo "Syncing to s3://${BUCKET}..."
aws s3 sync frontend/dist "s3://${BUCKET}" --region "${REGION}"

if [[ -n "${DIST_ID}" ]]; then
  echo "Invalidating CloudFront distribution ${DIST_ID}..."
  aws cloudfront create-invalidation --distribution-id "${DIST_ID}" --paths "/*" >/dev/null
fi

echo "Done."
