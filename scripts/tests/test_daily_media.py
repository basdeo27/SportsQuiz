"""Content publishing checks; no AWS calls."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('daily_media', ROOT / 'scripts/prepare_daily_media.py')
media = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(media)


class DailyMediaTests(unittest.TestCase):
    def test_all_authored_media_is_packaged_with_content_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary)
            count = media.package_media(media.RESOURCES, destination)
            self.assertGreaterEqual(count, 5)
            for path in destination.iterdir():
                self.assertEqual(path.stem, hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertTrue(any(path.suffix == '.mp3' for path in destination.iterdir()))
            # Sources and a mapping of future hints must never be bundled as JSON.
            self.assertFalse(any(destination.glob('*.json')))

    def test_missing_file_and_traversal_fail_before_upload(self):
        for url in ('assets/daily/missing.mp3', 'assets/daily/../private.mp3'):
            with self.subTest(url=url), tempfile.TemporaryDirectory() as temporary:
                resources = Path(temporary)
                (resources / 'data/daily').mkdir(parents=True)
                (resources / 'data/daily/2026-10.json').write_text(json.dumps([
                    {'hints': [{'type': 'AUDIO', 'url': url, 'source': 'LOCAL'}]}]))
                with self.assertRaises(ValueError):
                    media.package_media(resources, resources / 'output')
                self.assertFalse((resources / 'output').exists())

    def test_remote_media_is_not_downloaded(self):
        with tempfile.TemporaryDirectory() as temporary:
            resources = Path(temporary)
            (resources / 'data/daily').mkdir(parents=True)
            (resources / 'data/daily/2026-10.json').write_text(json.dumps([
                {'hints': [{'type': 'IMAGE', 'url': 'https://example.test/image.jpg', 'source': 'REMOTE'}]}]))
            self.assertEqual(media.package_media(resources, resources / 'output'), 0)
