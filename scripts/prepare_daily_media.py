#!/usr/bin/env python3
"""Package referenced daily media for the existing S3 frontend release (no AWS calls)."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.svg', '.avif', '.mp3', '.m4a', '.ogg', '.wav', '.mp4', '.webm'}


def package_media(resources, destination):
    media_root = (resources / 'static/assets/daily').resolve()
    files = set()
    # Validate everything before writing a partial bundle.
    for month in sorted((resources / 'data/daily').glob('*.json')):
        for entry in json.loads(month.read_text()):
            for hint in entry['hints']:
                if hint['type'] not in ('IMAGE', 'AUDIO', 'VIDEO') or hint.get('source', 'REMOTE') != 'LOCAL':
                    continue
                url = hint['url'].lstrip('/')
                if not url.startswith('assets/daily/') or '..' in url.split('/'):
                    raise ValueError(f'Invalid daily media path in {month.name}: {url}')
                source = (resources / 'static' / url).resolve()
                if not source.is_relative_to(media_root) or not source.is_file() or source.suffix.lower() not in EXTENSIONS:
                    raise ValueError(f'Missing or unsupported daily media: {url}')
                files.add(source)
    destination.mkdir(parents=True, exist_ok=True)
    for source in sorted(files):
        name = hashlib.sha256(source.read_bytes()).hexdigest() + source.suffix.lower()
        shutil.copyfile(source, destination / name)
    return len(files)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'frontend/dist/media/daily')
    args = parser.parse_args()
    print(f'Packaged {package_media(RESOURCES, args.output)} daily media files for S3.')
