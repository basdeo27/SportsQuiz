#!/usr/bin/env python3
"""Prepare daily-challenge MP3 clips. See docs/audio-clips.md."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
MEDIA = ROOT / 'src/main/resources/static'
CACHE = ROOT / '.cache/audio-clips'
PROVENANCE = ROOT / 'src/main/resources/research/audio-clips'


class ClipError(Exception):
    pass


def parse_timestamp(value):
    """Accept seconds, MM:SS or HH:MM:SS, including fractional seconds."""
    if not re.fullmatch(r'\d+(?::\d{1,2}){0,2}(?:\.\d+)?', value.strip()):
        raise argparse.ArgumentTypeError('Use nonnegative SS, MM:SS or HH:MM:SS timestamps.')
    parts = [float(p) for p in value.strip().split(':')]
    if len(parts) > 1 and any(p >= 60 for p in parts[1:]):
        raise argparse.ArgumentTypeError('Minutes/seconds after a colon must be below 60.')
    result = sum(p * 60 ** i for i, p in enumerate(reversed(parts)))
    if not math.isfinite(result):
        raise argparse.ArgumentTypeError('Timestamp must be finite.')
    return result


def fingerprint(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def run(command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise ClipError(f'{command[0]} failed:\n{result.stderr[-3000:]}')
    return result.stdout


def require_media_tools():
    for executable in ('ffmpeg', 'ffprobe'):
        if not shutil.which(executable):
            raise ClipError(f'{executable} is missing. Install FFmpeg: brew install ffmpeg')


def probe(path):
    data = json.loads(run(['ffprobe', '-v', 'error', '-show_format', '-show_streams',
                           '-of', 'json', str(path)]))
    streams = [stream for stream in data.get('streams', []) if stream.get('codec_type') == 'audio']
    if not streams:
        raise ClipError(f'No audio track found in {path.name}.')
    try:
        duration = float(data['format']['duration'])
    except (KeyError, ValueError, TypeError):
        raise ClipError('Source must have a known finite duration; live streams are not supported.')
    if not math.isfinite(duration) or duration <= 0:
        raise ClipError('Source must have a positive finite duration.')
    return duration, streams[0]


def youtube_dl():
    try:
        import yt_dlp
    except ImportError:
        raise ClipError('yt-dlp is missing. Run make audio-setup, then retry.')
    return yt_dlp.YoutubeDL


def validate_download(info):
    if info.get('_type') in ('playlist', 'multi_video') or 'entries' in info:
        raise ClipError('Use a single video URL, not a playlist.')
    if info.get('is_live') or info.get('live_status') in ('is_live', 'is_upcoming'):
        raise ClipError('Live/upcoming videos are not supported. Use a finished recording.')


def video_info(url, verbose=False):
    with youtube_dl()({'quiet': not verbose, 'noplaylist': True, 'lazy_playlist': True,
                       'js_runtimes': {'deno': {}, 'node': {}}}) as downloader:
        info = downloader.extract_info(url, download=False)
        if not info:
            raise ClipError('The source returned no video information.')
        validate_download(info)
        return info


def download_audio(url, cache=CACHE, verbose=False):
    """Cache the native track, avoiding a lossy intermediate MP3 conversion."""
    key = hashlib.sha256(url.encode()).hexdigest()
    directory = cache / key
    manifest = directory / 'source.json'
    if manifest.is_file():
        try:
            record = json.loads(manifest.read_text())
            name = record['file']
            candidate = directory / name
            if Path(name).name == name and candidate.is_file() and fingerprint(candidate) == record['sha256']:
                print('Using cached audio.')
                return candidate, record
        except (OSError, ValueError, KeyError, TypeError):
            pass
    info = video_info(url, verbose)
    directory.mkdir(parents=True, exist_ok=True)
    # A failed download never creates a usable cache manifest.
    with tempfile.TemporaryDirectory(dir=directory) as temporary:
        options = {'format': 'bestaudio/best', 'outtmpl': str(Path(temporary) / 'source.%(ext)s'),
                   'noplaylist': True, 'quiet': not verbose, 'lazy_playlist': True,
                   'js_runtimes': {'deno': {}, 'node': {}}}
        with youtube_dl()(options) as downloader:
            downloaded = downloader.extract_info(url, download=True)
            if not downloaded:
                raise ClipError('No audio was downloaded.')
            validate_download(downloaded)
            source = Path(downloader.prepare_filename(downloaded))
        if not source.is_file():
            raise ClipError('Downloader did not produce the expected audio file.')
        probe(source)
        target = directory / source.name
        record = {'url': url, 'webpage_url': info.get('webpage_url', url),
                  'title': info.get('title'), 'uploader': info.get('uploader'),
                  'video_id': info.get('id'), 'file': target.name, 'sha256': fingerprint(source)}
        os.replace(source, target)
        manifest.write_text(json.dumps(record, indent=2) + '\n')
    return target, record


def trim_audio(source, output, start, end, normalize=True, fade=0.08):
    duration = end - start
    filters = []
    if normalize:
        filters.append('loudnorm=I=-16:TP=-1.5:LRA=11')
    edge = min(fade, duration / 2)
    if edge:
        filters += [f'afade=t=in:st=0:d={edge}', f'afade=t=out:st={duration-edge}:d={edge}']
    command = ['ffmpeg', '-nostdin', '-hide_banner', '-loglevel', 'error', '-y',
               '-ss', str(start), '-i', str(source), '-t', str(duration),
               '-map', '0:a:0', '-vn', '-map_metadata', '-1', '-map_chapters', '-1']
    if filters:
        command += ['-af', ','.join(filters)]
    command += ['-ar', '44100', '-ac', '2', '-c:a', 'libmp3lame', '-b:a', '192k', str(output)]
    run(command)
    actual, stream = probe(output)
    if stream.get('codec_name') != 'mp3' or abs(actual - duration) > 0.15:
        raise ClipError('Generated clip failed MP3/duration validation.')


def environment_flag(name):
    return os.environ.get(name, '').lower() in ('1', 'true', 'yes')


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    sources = result.add_mutually_exclusive_group(required=False)
    sources.add_argument('--url', default=os.environ.get('URL') or None)
    sources.add_argument('--input', type=Path, default=os.environ.get('INPUT') or None,
                         help='Local audio/video, including MOV; no downloader needed')
    result.add_argument('--start', type=parse_timestamp, default=os.environ.get('START') or None)
    result.add_argument('--end', type=parse_timestamp, default=os.environ.get('END') or None)
    result.add_argument('--player', default=os.environ.get('PLAYER') or None,
                        help='Authoring slug, e.g. brady (kept out of the MP3 URL)')
    result.add_argument('--out', type=Path, default=os.environ.get('OUT') or None,
                        help='Optional .mp3 output path; refuses overwrites by default')
    result.add_argument('--cache-dir', type=Path, default=CACHE)
    result.add_argument('--force', action='store_true', default=environment_flag('FORCE'))
    result.add_argument('--no-normalize', action='store_true')
    result.add_argument('--fade', type=float, default=0.08, help='Fade at each edge, seconds (default .08)')
    result.add_argument('--source-note', default=os.environ.get('SOURCE_NOTE', ''),
                        help='Optional attribution/permission/recording notes')
    result.add_argument('--info', action='store_true', default=environment_flag('INFO'))
    result.add_argument('--dry-run', action='store_true', default=environment_flag('DRY_RUN'))
    result.add_argument('--verbose', action='store_true')
    return result


def execute(args):
    if bool(args.url) == bool(args.input):
        raise ClipError('Provide exactly one of URL/--url or INPUT/--input.')
    if args.url and (urlsplit(args.url).scheme not in ('http', 'https') or not urlsplit(args.url).netloc):
        raise ClipError('URL must be an http(s) video URL.')
    if args.input:
        args.input = args.input.expanduser().resolve()
        if not args.input.is_file():
            raise ClipError(f'Local source does not exist: {args.input}')
    if args.info:
        if args.input:
            require_media_tools()
            duration, stream = probe(args.input)
            print(json.dumps({'file': str(args.input), 'duration': duration, 'codec': stream['codec_name']}, indent=2))
        else:
            info = video_info(args.url, args.verbose)
            print(json.dumps({key: info.get(key) for key in ('title', 'uploader', 'duration', 'webpage_url')}, indent=2))
        return
    if args.start is None or args.end is None or args.end <= args.start:
        raise ClipError('START and END are required; END must be after START.')
    if not args.player and not args.out:
        raise ClipError('Provide PLAYER (e.g. PLAYER="brady") or an explicit --out path.')
    player = args.player or 'unassigned'
    if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', player):
        raise ClipError('PLAYER must be a lowercase slug, e.g. ken-griffey-jr.')
    if not math.isfinite(args.fade) or args.fade < 0:
        raise ClipError('--fade must be a nonnegative finite number.')
    settings = {'start_seconds': args.start, 'end_seconds': args.end,
                'normalize': not args.no_normalize, 'fade_seconds': args.fade,
                'mp3_bitrate': '192k', 'sample_rate': 44100, 'channels': 2, 'processing_version': 1}
    # Dry runs do not hash large files, create directories or require external tools.
    if args.dry_run:
        print(json.dumps({'source': args.url or str(args.input), 'player': player, **settings,
                          'output': str(args.out or MEDIA / 'assets/daily/audio/<clip-hash>.mp3')}, indent=2))
        return
    require_media_tools()
    if args.out and args.out.suffix.lower() != '.mp3':
        raise ClipError('OUT/--out must end in .mp3.')
    source_id = args.url or fingerprint(args.input)
    clip_id = hashlib.sha256(json.dumps([source_id, settings], sort_keys=True).encode()).hexdigest()[:24]
    output = (args.out.expanduser() if args.out else MEDIA / f'assets/daily/audio/{clip_id}.mp3').resolve()
    metadata = PROVENANCE / player / f'{clip_id}.json'
    if output == args.input:
        raise ClipError('Output cannot replace the source file.')
    if not args.force and (output.exists() or metadata.exists()):
        raise ClipError('Clip or source record already exists. Use FORCE=1/--force to replace it, or change timestamps/OUT.')
    if args.url:
        source, record = download_audio(args.url, args.cache_dir.expanduser().resolve(), args.verbose)
    else:
        source = args.input
        record = {'file': str(source), 'sha256': source_id}
    duration, _ = probe(source)
    if args.end > duration + 0.001:
        raise ClipError(f'END ({args.end:g}s) exceeds source duration ({duration:g}s).')
    if output == source.resolve():
        raise ClipError('Output cannot replace cached source audio.')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        candidate = Path(temporary) / 'clip.mp3'
        trim_audio(source, candidate, args.start, args.end, not args.no_normalize, args.fade)
        # Write provenance before publishing the final file; it is never served as a hint asset.
        document = {'player': player, 'created_at': datetime.now(timezone.utc).isoformat(),
                    'source': record, 'source_note': args.source_note, 'processing': settings,
                    'output': str(output.relative_to(ROOT)) if output.is_relative_to(ROOT) else str(output),
                    'output_sha256': fingerprint(candidate)}
        metadata.parent.mkdir(parents=True, exist_ok=True)
        staged_metadata = Path(temporary) / 'record.json'
        staged_metadata.write_text(json.dumps(document, indent=2) + '\n')
        os.replace(staged_metadata, metadata)
        os.replace(candidate, output)
    print(f'Saved {args.end-args.start:g}s MP3: {output}')
    print(f'Source record: {metadata}')
    if output.is_relative_to(MEDIA.resolve() / 'assets/daily'):
        print('Hint JSON: ' + json.dumps({'type': 'AUDIO', 'url': output.relative_to(MEDIA.resolve()).as_posix(), 'source': 'LOCAL'}))
        print('Preview the MP3, then add the hint JSON. The normal release publishes referenced media to S3.')
    else:
        print('Preview the MP3; copy it under src/main/resources/static/assets/daily/ before referencing it in a challenge.')


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        execute(args)
    except (ClipError, OSError, ValueError) as error:
        print(f'Error: {error}', file=sys.stderr)
        return 1
    except Exception as error:
        # Downloader failures should be actionable, without a wall of Python traceback.
        if type(error).__module__.startswith('yt_dlp'):
            print(f'Download failed: {error}\nTry updating with make audio-setup, or use INPUT with a local recording.', file=sys.stderr)
            return 1
        raise
    return 0


if __name__ == '__main__':
    sys.exit(main())
