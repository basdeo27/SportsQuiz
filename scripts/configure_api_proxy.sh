#!/usr/bin/env bash
# Route browser API traffic through the frontend domain so HttpOnly sessions are first-party.
set -euo pipefail
: "${CLOUDFRONT_DISTRIBUTION_ID:?Set CLOUDFRONT_DISTRIBUTION_ID}"
: "${BACKEND_URL:?Set BACKEND_URL to the HTTPS backend root}"
TASK_PROXY_TMP=$(mktemp -d)
trap 'rm -f "$TASK_PROXY_TMP/current.json" "$TASK_PROXY_TMP/updated.json"; rmdir "$TASK_PROXY_TMP"' EXIT
aws cloudfront get-distribution-config --id "$CLOUDFRONT_DISTRIBUTION_ID" > "$TASK_PROXY_TMP/current.json"
# AWS-managed CachingDisabled and AllViewerExceptHostHeader policy IDs.
TASK_CACHE_POLICY="${CLOUDFRONT_CACHE_POLICY_ID:-4135ea2d-6df8-44a3-9df3-4b5a84be39ad}"
TASK_ORIGIN_POLICY="${CLOUDFRONT_ORIGIN_POLICY_ID:-b689b0a8-53d0-40ab-baf2-68738e2966ac}"
export TASK_CACHE_POLICY TASK_ORIGIN_POLICY TASK_PROXY_TMP
python3 - <<'PY'
import json, os
from pathlib import Path
from urllib.parse import urlparse
root = Path(os.environ['TASK_PROXY_TMP'])
current = json.loads((root / 'current.json').read_text())
config = current['DistributionConfig']
backend = urlparse(os.environ['BACKEND_URL'])
if backend.scheme != 'https' or not backend.hostname or backend.username or backend.password or backend.port not in (None, 443) or backend.path not in ('', '/') or backend.query:
    raise SystemExit('BACKEND_URL must be an HTTPS root URL without a path or credentials.')
for name in ('TASK_CACHE_POLICY', 'TASK_ORIGIN_POLICY'):
    if os.environ[name] in ('', 'None', 'null'):
        raise SystemExit(f'Missing CloudFront managed policy: {name}')
origin_id = 'SportsQuizApi'
origin = {'Id': origin_id, 'DomainName': backend.hostname, 'OriginPath': '',
          'CustomHeaders': {'Quantity': 0},
          'CustomOriginConfig': {'HTTPPort': 80, 'HTTPSPort': 443, 'OriginProtocolPolicy': 'https-only',
                                 'OriginReadTimeout': 30, 'OriginKeepaliveTimeout': 5,
                                 'OriginSslProtocols': {'Quantity': 1, 'Items': ['TLSv1.2']}}}
origins = [item for item in config['Origins']['Items'] if item['Id'] != origin_id] + [origin]
# Updates submit every origin, including legacy S3 origins whose read timeout
# may be absent from GetDistributionConfig. Supply defaults without changing
# explicit timeout settings or origin access configuration.
for item in origins:
    for kind in ('S3OriginConfig', 'CustomOriginConfig', 'VpcOriginConfig'):
        if kind in item:
            if item[kind].get('OriginReadTimeout') is None:
                item[kind]['OriginReadTimeout'] = 30
            if kind != 'S3OriginConfig':
                if item[kind].get('OriginKeepaliveTimeout') is None:
                    item[kind]['OriginKeepaliveTimeout'] = 5
            print(f"CloudFront origin {item['Id']} ({kind}): OriginReadTimeout={item[kind]['OriginReadTimeout']}", flush=True)
config['Origins'] = {'Quantity': len(origins), 'Items': origins}
behavior = {'PathPattern': '/v0/*', 'TargetOriginId': origin_id, 'ViewerProtocolPolicy': 'redirect-to-https',
            'TrustedSigners': {'Enabled': False, 'Quantity': 0},
            'TrustedKeyGroups': {'Enabled': False, 'Quantity': 0},
            'AllowedMethods': {'Quantity': 7, 'Items': ['GET', 'HEAD', 'OPTIONS', 'PUT', 'POST', 'PATCH', 'DELETE'],
                               'CachedMethods': {'Quantity': 3, 'Items': ['GET', 'HEAD', 'OPTIONS']}},
            'Compress': True, 'SmoothStreaming': False, 'CachePolicyId': os.environ['TASK_CACHE_POLICY'],
            'OriginRequestPolicyId': os.environ['TASK_ORIGIN_POLICY'],
            'LambdaFunctionAssociations': {'Quantity': 0}, 'FunctionAssociations': {'Quantity': 0},
            'FieldLevelEncryptionId': ''}
patterns = ['/v0/*', '/assets/daily/*']
behaviors = [dict(behavior, PathPattern=pattern) for pattern in patterns] + [item for item in config.get('CacheBehaviors', {}).get('Items', []) if item['PathPattern'] not in patterns]
config['CacheBehaviors'] = {'Quantity': len(behaviors), 'Items': behaviors}
# CloudFront requires this flag on updates even for non-streaming behaviors.
# Preserve any existing streaming configuration on unrelated routes.
for item in [config['DefaultCacheBehavior'], *behaviors]:
    item.setdefault('SmoothStreaming', False)
(root / 'updated.json').write_text(json.dumps(config))
PY
TASK_DISTRIBUTION_ETAG=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["ETag"])' "$TASK_PROXY_TMP/current.json")
aws cloudfront update-distribution --id "$CLOUDFRONT_DISTRIBUTION_ID" --if-match "$TASK_DISTRIBUTION_ETAG" \
  --distribution-config "file://$TASK_PROXY_TMP/updated.json" --query 'Distribution.Status' --output text
aws cloudfront wait distribution-deployed --id "$CLOUDFRONT_DISTRIBUTION_ID"
TASK_DISTRIBUTION_DOMAIN=$(aws cloudfront get-distribution --id "$CLOUDFRONT_DISTRIBUTION_ID" --query 'Distribution.DomainName' --output text)
curl --fail --silent --show-error --retry 3 --max-time 30 "https://$TASK_DISTRIBUTION_DOMAIN/v0/quiz/leagues" > /dev/null
