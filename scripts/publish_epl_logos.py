#!/usr/bin/env python3
"""Upload reviewed EPL logo assets; update quiz data only after CDN verification."""
import argparse
from datetime import datetime, timezone
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'assets/logos/epl/teams.json'
PUBLISHED = ROOT / 'assets/logos/epl/published.json'
DATA = ROOT / 'src/main/resources/data/logos/epl.json'
CONFIG = ROOT / 'src/main/resources/application.yaml'
PREFIX = 'media/logos/epl'


def aws(*arguments):
    result = subprocess.run(['aws', *map(str, arguments), '--output', 'json'],
                            capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'AWS {arguments[0]} {arguments[1]} failed: {result.stderr.strip()}')
    return json.loads(result.stdout) if result.stdout.strip() else {}


def plan(source, base_url):
    parsed = urlsplit(base_url)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.path not in ('', '/') or parsed.query or parsed.fragment or parsed.port not in (None, 443):
        raise ValueError('--base-url must be an HTTPS CloudFront/site root with no credentials/path/query.')
    manifest = json.loads(MANIFEST.read_text())
    assets = []
    for team in manifest['teams']:
        name = team['file']
        path = (source / name).resolve()
        if Path(name).name != name or not path.is_relative_to(source.resolve()) or not path.is_file():
            raise ValueError(f'Missing/unsafe source image: {name}')
        content = path.read_bytes()
        if content.startswith(b'\x89PNG\r\n\x1a\n'):
            extension, content_type = '.png', 'image/png'
        elif content.startswith(b'\xff\xd8\xff'):
            extension, content_type = '.jpeg', 'image/jpeg'
        else:
            raise ValueError(f'Unsupported image bytes: {name}')
        digest = hashlib.sha256(content).hexdigest()
        key = f'{PREFIX}/{digest}{extension}'
        row = {key: value for key, value in team.items() if key != 'file'}
        row['logoUrl'] = f'{base_url.rstrip("/")}/{key}'
        assets.append({'file': path, 'key': key, 'sha256': digest,
                       'content_type': content_type, 'team': row})
    if len({item['team']['id'] for item in assets}) != len(assets):
        raise ValueError('Duplicate team IDs in logo manifest.')
    known = {team['file'] for team in manifest['teams']} | {item['file'] for item in manifest['excluded']}
    unknown = [p.name for p in source.iterdir() if p.is_file() and not p.name.startswith('.') and p.name not in known]
    if unknown:
        raise ValueError(f'Unmapped source files need review: {unknown}')
    return assets, manifest['excluded']


def find_origin(base_url, example_key):
    host = urlsplit(base_url).hostname
    distributions = aws('cloudfront', 'list-distributions').get('DistributionList', {}).get('Items', [])
    matches = [d for d in distributions if host == d['DomainName'] or host in d.get('Aliases', {}).get('Items', [])]
    if len(matches) != 1:
        raise ValueError(f'Expected one CloudFront distribution for {host}, found {len(matches)}.')
    distribution = matches[0]
    config = aws('cloudfront', 'get-distribution-config', '--id', distribution['Id'])['DistributionConfig']
    behavior = next((b for b in config.get('CacheBehaviors', {}).get('Items', [])
                     if fnmatch.fnmatchcase(example_key, b['PathPattern'].lstrip('/'))), config['DefaultCacheBehavior'])
    if behavior['ViewerProtocolPolicy'] not in ('https-only', 'redirect-to-https'):
        raise ValueError('Logo route must enforce HTTPS.')
    if any(behavior.get(name, {}).get('Enabled') for name in ('TrustedKeyGroups', 'TrustedSigners')):
        raise ValueError('Logo route requires signed viewer URLs; permanent JSON URLs would not work.')
    origin = next(o for o in config['Origins']['Items'] if o['Id'] == behavior['TargetOriginId'])
    match = re.fullmatch(r'(.+)\.s3(?:[.-][a-z0-9-]+)?\.amazonaws\.com', origin['DomainName'])
    if 'S3OriginConfig' not in origin or not match:
        raise ValueError('Logo route must use a private S3 REST origin, not a website/backend origin.')
    oac = origin.get('OriginAccessControlId')
    oai = origin['S3OriginConfig'].get('OriginAccessIdentity')
    if not oac and not oai:
        raise ValueError('S3 origin has no CloudFront OAC/OAI; configure private origin access first.')
    if oac:
        control = aws('cloudfront', 'get-origin-access-control', '--id', oac)['OriginAccessControl']['OriginAccessControlConfig']
        if control['SigningBehavior'] != 'always' or control['SigningProtocol'] != 'sigv4':
            raise ValueError('CloudFront OAC must always sign S3 requests with SigV4.')
    return match.group(1), origin.get('OriginPath', '').strip('/'), distribution['Id']


def require_private_bucket(bucket):
    settings = aws('s3api', 'get-public-access-block', '--bucket', bucket)['PublicAccessBlockConfiguration']
    required = ('BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets')
    if not all(settings.get(name) for name in required):
        raise ValueError(f'{bucket}: all four S3 Block Public Access settings must be enabled. No public ACLs will be added.')


def verify_asset(asset):
    last_error = None
    for attempt in range(3):
        try:
            with urlopen(asset['team']['logoUrl'], timeout=30) as response:
                if response.headers.get_content_type() != asset['content_type']:
                    raise ValueError('CDN did not return the expected image content type (possibly an HTML error fallback).')
                if hashlib.sha256(response.read()).hexdigest() != asset['sha256']:
                    raise ValueError('CDN image checksum does not match the local file.')
            return
        except (URLError, ValueError, TimeoutError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(1)
    raise ValueError(f'Cannot verify {asset["team"]["name"]} at {asset["team"]["logoUrl"]}: {last_error}')


def enable_epl(config):
    pattern = r'(?m)^(\s*)disabled-logo-leagues:\s*\n((?:\1  - [^\n]+\n)+)'
    match = re.search(pattern, config)
    if match:
        remaining = [line for line in match[2].splitlines(True) if line.strip() != '- EPL']
        replacement = f'{match[1]}disabled-logo-leagues:' + ('\n' + ''.join(remaining) if remaining else ' []\n')
        return config[:match.start()] + replacement + config[match.end():]
    if re.search(r'disabled-logo-leagues: *\[\]', config):
        return config
    raise ValueError('Unrecognised disabled-logo-leagues config; review manually before enabling EPL.')


def publish(source, base_url, dry_run=False, bucket=None):
    assets, excluded = plan(source, base_url)
    retained = json.loads(MANIFEST.read_text()).get('retainedTeams', [])
    teams = sorted([asset['team'] for asset in assets] + retained, key=lambda team: team['name'])
    if len({team['id'] for team in teams}) != len(teams):
        raise ValueError('Duplicate team IDs across uploaded and retained teams.')
    updated_config = enable_epl(CONFIG.read_text())  # Validate before any uploads.
    print(f'{len(assets)} reviewed logos; excluded: {", ".join(item["file"] for item in excluded)}')
    if retained:
        print('Retaining existing logo URLs (no supplied images): ' + ', '.join(team['name'] for team in retained))
    for asset in assets:
        print(f'{asset["file"].name} -> {asset["team"]["name"]} -> {asset["team"]["logoUrl"]}')
    if dry_run:
        print('Dry run: no AWS calls, uploads, quiz JSON changes or league activation.')
        return
    discovered, origin_path, distribution = find_origin(base_url, assets[0]['key'])
    if bucket and bucket != discovered:
        raise ValueError(f'--bucket {bucket} does not match the CDN S3 origin {discovered}.')
    bucket = discovered
    require_private_bucket(bucket)
    previous = json.loads(PUBLISHED.read_text()) if PUBLISHED.is_file() else {}
    published = {item['url']: item['sha256'] for item in previous.get('assets', [])} if previous.get('bucket') == bucket and previous.get('base_url') == base_url else {}
    print(f'Uploading to private bucket {bucket}; CloudFront {distribution}.')
    for asset in assets:
        key = '/'.join(p for p in (origin_path, asset['key']) if p)
        if published.get(asset['team']['logoUrl']) != asset['sha256']:
            aws('s3api', 'put-object', '--bucket', bucket, '--key', key, '--body', asset['file'],
                '--content-type', asset['content_type'], '--cache-control', 'public,max-age=31536000,immutable',
                '--metadata', json.dumps({'sha256': asset['sha256']}))
            head = aws('s3api', 'head-object', '--bucket', bucket, '--key', key)
            if head.get('Metadata', {}).get('sha256') != asset['sha256'] or head.get('ContentLength') != asset['file'].stat().st_size:
                raise ValueError(f'Uploaded object validation failed: {key}')
        # Even previously published immutable objects must still pass the live CDN checksum check.
        verify_asset(asset)
        print(f'Verified: {asset["team"]["name"]}')
    # No app changes are made on a failed/partial upload. Content-addressed objects are safe to retain/retry.
    data = json.dumps(teams, indent=2, ensure_ascii=False) + '\n'
    temporary = DATA.with_suffix('.json.pending')
    temporary.write_text(data)
    temporary.replace(DATA)
    CONFIG.write_text(updated_config)
    PUBLISHED.write_text(json.dumps({
        'verified_on': datetime.now(timezone.utc).date().isoformat(),
        'bucket': bucket, 'distribution_id': distribution, 'base_url': base_url,
        'assets': [{'file': asset['file'].name, 'id': asset['team']['id'],
                    'name': asset['team']['name'], 'url': asset['team']['logoUrl'],
                    'sha256': asset['sha256']} for asset in assets],
        'retainedTeams': retained, 'excluded': excluded
    }, indent=2) + '\n')
    print('All logos verified. Updated epl.json and enabled EPL for local/prod on the next backend restart/deploy.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path.home() / 'Desktop/quizimages/epl')
    parser.add_argument('--base-url', default='https://d2323rgqey2wlv.cloudfront.net')
    parser.add_argument('--bucket', help='Optional expected bucket; must match the discovered CDN origin')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        publish(args.source.expanduser(), args.base_url, args.dry_run, args.bucket)
    except (ValueError, OSError, KeyError) as error:
        print(f'Error: {error}\nEPL data/config were not activated unless all uploads and CDN checks completed.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
