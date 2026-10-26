#!/usr/bin/env bash
set -euo pipefail
: "${SERVICE_NAME:?Set SERVICE_NAME}"
: "${AWS_REGION:?Set AWS_REGION}"
: "${DEPLOYMENT_VERSION:?Set DEPLOYMENT_VERSION}"
for TASK_CHECK_ATTEMPT in {1..60}; do
  TASK_SERVICE=$(aws lightsail get-container-services --service-name "$SERVICE_NAME" --region "$AWS_REGION" --output json)
  TASK_VERSION=$(jq -r '.containerServices[0].currentDeployment.version // 0' <<< "$TASK_SERVICE")
  TASK_STATE=$(jq -r '.containerServices[0].currentDeployment.state // "UNKNOWN"' <<< "$TASK_SERVICE")
  TASK_NEXT_STATE=$(jq -r '.containerServices[0].nextDeployment.state // "NONE"' <<< "$TASK_SERVICE")
  if [[ "$TASK_VERSION" == "$DEPLOYMENT_VERSION" && "$TASK_STATE" == "ACTIVE" ]]; then
    TASK_SERVICE_URL=$(jq -r '.containerServices[0].url' <<< "$TASK_SERVICE")
    curl --fail --silent --show-error --retry 3 --max-time 20 "$TASK_SERVICE_URL/actuator/health" > /dev/null
    curl --fail --silent --show-error --retry 3 --max-time 20 "$TASK_SERVICE_URL/v0/quiz/leagues" > /dev/null
    echo "Deployment $DEPLOYMENT_VERSION is active and API smoke checks passed."
    exit 0
  fi
  if [[ "$TASK_NEXT_STATE" == "FAILED" ]]; then
    echo "Lightsail deployment failed. The previous deployment may still be serving traffic." >&2
    exit 1
  fi
  sleep 10
done
echo "Timed out waiting for deployment $DEPLOYMENT_VERSION." >&2
exit 1
