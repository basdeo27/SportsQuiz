"""Audio preparation checks: real FFmpeg encoding plus isolated downloader caching."""
import argparse
from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('clip_audio', ROOT / 'scripts/python/clip_audio.py')
clip = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(clip)


class TimestampTests(unittest.TestCase):
    def test_formats_and_invalid_ranges(self):
        for text, seconds in [('84', 84), ('1:24.250', 84.25), ('1:02:03', 3723), ('0', 0)]:
            self.assertEqual(clip.parse_timestamp(text), seconds)
        for value in ('-1', 'nan', 'inf', '1:60', '1:60:00', '1:2:3:4', 'abc', ''):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                clip.parse_timestamp(value)

    def test_make_dry_run_preserves_url_query_and_needs_no_tools(self):
        env = {**os.environ, 'URL': '', 'INPUT': '', 'DRY_RUN': '', 'INFO': ''}
        url = 'https://example.test/watch?v=test&other=two'
        result = subprocess.run(['make', 'audio-clip', f'URL={url}', 'START=1:24', 'END=1:38',
                                 'PLAYER=brady', 'DRY_RUN=1'], cwd=ROOT, env=env,
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['source'], url)
        self.assertEqual(json.loads(result.stdout)['end_seconds'], 98)

    def test_validation_happens_before_download(self):
        with patch.dict(os.environ, {}, clear=True):
            for arguments in [
                ['--url', 'https://example.test/video', '--start', '4', '--end', '2', '--player', 'brady'],
                ['--url', 'https://example.test/video', '--start', '0', '--end', '2', '--player', '../brady'],
                ['--url', 'file:///tmp/input', '--start', '0', '--end', '2', '--player', 'brady'],
            ]:
                with self.subTest(arguments=arguments), patch.object(clip, 'download_audio') as download:
                    with self.assertRaises(clip.ClipError):
                        clip.execute(clip.parser().parse_args(arguments))
                    download.assert_not_called()

    def test_playlists_and_live_streams_rejected(self):
        for info in ({'entries': []}, {'_type': 'playlist'}, {'is_live': True}, {'live_status': 'is_upcoming'}):
            with self.assertRaises(clip.ClipError):
                clip.validate_download(info)


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg required')
class EncodingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.source = self.directory / 'source with spaces.mov'
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi',
                        '-i', 'sine=frequency=440:sample_rate=48000:duration=3',
                        '-metadata', 'title=Answer spoiler', '-c:a', 'pcm_s16le', str(self.source)], check=True)
        self.source_hash = clip.fingerprint(self.source)

    def arguments(self, *extra):
        with patch.dict(os.environ, {}, clear=True):
            return clip.parser().parse_args(['--input', str(self.source), '--start', '0.5', '--end', '2',
                                            '--player', 'brady', *extra])

    def execute(self, args):
        with patch.object(clip, 'MEDIA', self.directory / 'static'), \
             patch.object(clip, 'PROVENANCE', self.directory / 'research'), redirect_stdout(io.StringIO()) as output:
            clip.execute(args)
            return output.getvalue()

    def test_mov_to_mp3_duration_provenance_and_no_spoiler_metadata(self):
        text = self.execute(self.arguments('--source-note', 'Test recording'))
        outputs = list((self.directory / 'static/assets/daily/audio').glob('*.mp3'))
        self.assertEqual(len(outputs), 1)
        result = outputs[0]
        duration, stream = clip.probe(result)
        self.assertAlmostEqual(duration, 1.5, delta=.15)
        self.assertEqual(stream['codec_name'], 'mp3')
        self.assertEqual(stream['sample_rate'], '44100')
        self.assertEqual(stream['channels'], 2)
        tags = clip.run(['ffprobe', '-v', 'error', '-show_format', '-of', 'json', str(result)])
        self.assertNotIn('Answer spoiler', tags)
        self.assertNotIn('brady', result.name)
        record = json.loads(next((self.directory / 'research/brady').glob('*.json')).read_text())
        self.assertEqual(record['source']['sha256'], self.source_hash)
        self.assertEqual(record['processing']['start_seconds'], .5)
        self.assertEqual(record['output_sha256'], clip.fingerprint(result))
        self.assertEqual(record['source_note'], 'Test recording')
        self.assertIn('"type": "AUDIO"', text)
        self.assertEqual(clip.fingerprint(self.source), self.source_hash)
        with self.assertRaisesRegex(clip.ClipError, 'already exists'):
            self.execute(self.arguments())
        self.execute(self.arguments('--force'))

    def test_out_of_bounds_does_not_publish_clip_or_record(self):
        args = self.arguments()
        args.end = 4
        with self.assertRaisesRegex(clip.ClipError, 'exceeds source duration'):
            self.execute(args)
        self.assertFalse((self.directory / 'static').exists())
        self.assertFalse((self.directory / 'research').exists())

    def test_local_mode_without_downloader_and_processing_opt_out(self):
        output = self.directory / 'custom.mp3'
        with patch.object(clip, 'youtube_dl', side_effect=AssertionError('local mode imported downloader')):
            self.execute(self.arguments('--out', str(output), '--no-normalize', '--fade', '0'))
        self.assertEqual(clip.probe(output)[1]['codec_name'], 'mp3')

    def test_download_reused_offline_and_corrupt_cache_is_refetched(self):
        calls = []
        fixture = self.source

        class Downloader:
            def __init__(self, options):
                self.options = options
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def extract_info(self, url, download):
                calls.append(download)
                if download:
                    shutil.copyfile(fixture, self.options['outtmpl'].replace('%(ext)s', 'mov'))
                return {'title': 'Test recording', 'id': 'example', 'ext': 'mov', 'duration': 3}
            def prepare_filename(self, info):
                return self.options['outtmpl'].replace('%(ext)s', 'mov')

        cache = self.directory / 'cache'
        with patch.object(clip, 'youtube_dl', return_value=Downloader):
            source, record = clip.download_audio('https://example.test/recording', cache)
            self.assertEqual(calls, [False, True])
            with patch.object(clip, 'youtube_dl', side_effect=AssertionError('cache must work offline')):
                cached, _ = clip.download_audio('https://example.test/recording', cache)
            self.assertEqual(source, cached)
            self.assertEqual(clip.fingerprint(source), record['sha256'])
            source.write_bytes(b'corrupt')
            clip.download_audio('https://example.test/recording', cache)
            self.assertEqual(calls, [False, True, False, True])

    def test_encoding_failure_does_not_publish(self):
        with patch.object(clip, 'trim_audio', side_effect=clip.ClipError('encoding failed')):
            with self.assertRaises(clip.ClipError):
                self.execute(self.arguments())
        self.assertEqual(list(self.directory.rglob('*.mp3')), [])
        self.assertFalse((self.directory / 'research').exists())


if __name__ == '__main__':
    unittest.main()
