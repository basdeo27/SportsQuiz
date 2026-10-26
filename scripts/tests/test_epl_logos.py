import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('epl_logos', ROOT / 'scripts/publish_epl_logos.py')
logos = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(logos)


class EplLogoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'images'
        self.source.mkdir()
        (self.source / 'arsenal.png').write_bytes(b'\x89PNG\r\n\x1a\nfixture')
        self.manifest = self.root / 'teams.json'
        self.manifest.write_text(json.dumps({'teams': [{'file': 'arsenal.png', 'id': 'epl-arsenal',
                                                      'name': 'Arsenal', 'answers': ['Arsenal'], 'hint': 'Gunners'}],
                                            'excluded': []}))
        self.data = self.root / 'epl.json'
        self.data.write_text('["original"]')
        self.config = self.root / 'application.yaml'
        self.config.write_text('quiz:\n  settings:\n    disabled-logo-leagues:\n      - EPL\n    disabled-face-leagues: []\n')
        for name, value in [('MANIFEST', self.manifest), ('DATA', self.data), ('CONFIG', self.config),
                            ('PUBLISHED', self.root / 'published.json')]:
            patcher = patch.object(logos, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_plan_uses_opaque_https_urls_and_detects_unknown_files(self):
        assets, _ = logos.plan(self.source, 'https://cdn.example')
        self.assertNotIn('arsenal', assets[0]['team']['logoUrl'])
        self.assertEqual(assets[0]['content_type'], 'image/png')
        (self.source / 'unknown.jpeg').write_bytes(b'\xff\xd8\xfffixture')
        with self.assertRaisesRegex(ValueError, 'Unmapped source files'):
            logos.plan(self.source, 'https://cdn.example')

    def test_enable_only_epl_preserves_other_league_settings(self):
        config = self.config.read_text().replace('      - EPL\n', '      - EPL\n      - NFL\n')
        updated = logos.enable_epl(config)
        self.assertNotIn('- EPL', updated)
        self.assertIn('- NFL', updated)
        self.assertIn('disabled-face-leagues: []', updated)
        self.assertIn('disabled-logo-leagues: []', logos.enable_epl(self.config.read_text()))

    def test_dry_run_makes_no_aws_calls_or_changes(self):
        with patch.object(logos, 'aws') as aws, redirect_stdout(io.StringIO()):
            logos.publish(self.source, 'https://cdn.example', dry_run=True)
        aws.assert_not_called()
        self.assertEqual(self.data.read_text(), '["original"]')
        self.assertIn('- EPL', self.config.read_text())

    def test_failed_cdn_verification_never_activates_dataset(self):
        with patch.object(logos, 'find_origin', return_value=('private-bucket', '', 'DIST')), \
             patch.object(logos, 'require_private_bucket'), \
             patch.object(logos, 'aws', return_value={'Metadata': {}, 'ContentLength': 0}), \
             redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(ValueError, 'validation failed'):
                logos.publish(self.source, 'https://cdn.example')
        self.assertEqual(self.data.read_text(), '["original"]')
        self.assertIn('- EPL', self.config.read_text())

    def test_activation_follows_verified_upload(self):
        assets, _ = logos.plan(self.source, 'https://cdn.example')
        calls = []
        def aws(*args):
            calls.append(args)
            return {'Metadata': {'sha256': assets[0]['sha256']}, 'ContentLength': assets[0]['file'].stat().st_size}
        def verify(asset):
            self.assertEqual(self.data.read_text(), '["original"]')
            self.assertIn('- EPL', self.config.read_text())
            calls.append(('verified',))
        with patch.object(logos, 'find_origin', return_value=('private-bucket', 'site', 'DIST')), \
             patch.object(logos, 'require_private_bucket'), patch.object(logos, 'aws', side_effect=aws), \
             patch.object(logos, 'verify_asset', side_effect=verify), redirect_stdout(io.StringIO()):
            logos.publish(self.source, 'https://cdn.example')
        self.assertEqual(calls[0][:2], ('s3api', 'put-object'))
        self.assertIn('site/' + assets[0]['key'], calls[0])
        self.assertNotIn('--acl', calls[0])
        self.assertEqual(calls[-1], ('verified',))
        self.assertEqual(json.loads(self.data.read_text())[0]['name'], 'Arsenal')
        self.assertNotIn('- EPL', self.config.read_text())

    def test_bucket_must_block_public_access(self):
        with patch.object(logos, 'aws', return_value={'PublicAccessBlockConfiguration': {'BlockPublicAcls': True}}):
            with self.assertRaisesRegex(ValueError, 'all four'):
                logos.require_private_bucket('bucket')

    def test_unchanged_published_image_is_reverified_without_reupload(self):
        asset = logos.plan(self.source, 'https://cdn.example')[0][0]
        logos.PUBLISHED.write_text(json.dumps({
            'bucket': 'private-bucket', 'base_url': 'https://cdn.example',
            'assets': [{'url': asset['team']['logoUrl'], 'sha256': asset['sha256']}]
        }))
        with patch.object(logos, 'find_origin', return_value=('private-bucket', '', 'DIST')), \
             patch.object(logos, 'require_private_bucket'), patch.object(logos, 'aws') as aws, \
             patch.object(logos, 'verify_asset') as verify, redirect_stdout(io.StringIO()):
            logos.publish(self.source, 'https://cdn.example')
        aws.assert_not_called()
        verify.assert_called_once()
        self.assertEqual(json.loads(self.data.read_text())[0]['name'], 'Arsenal')

    def test_missing_images_do_not_remove_retained_non_relegated_clubs(self):
        manifest = json.loads(self.manifest.read_text())
        manifest['retainedTeams'] = [{
            'id': 'epl-newcastle-united', 'name': 'Newcastle United',
            'logoUrl': 'https://a.espncdn.com/i/teamlogos/soccer/500/361.png',
            'answers': ['Newcastle United'], 'hint': 'Magpies'
        }]
        self.manifest.write_text(json.dumps(manifest))
        asset = logos.plan(self.source, 'https://cdn.example')[0][0]
        head = {'Metadata': {'sha256': asset['sha256']}, 'ContentLength': asset['file'].stat().st_size}
        with patch.object(logos, 'find_origin', return_value=('private-bucket', '', 'DIST')), \
             patch.object(logos, 'require_private_bucket'), patch.object(logos, 'aws', return_value=head), \
             patch.object(logos, 'verify_asset'), redirect_stdout(io.StringIO()):
            logos.publish(self.source, 'https://cdn.example')
        teams = json.loads(self.data.read_text())
        self.assertEqual({team['id'] for team in teams}, {'epl-arsenal', 'epl-newcastle-united'})
        self.assertEqual(teams[1]['logoUrl'], manifest['retainedTeams'][0]['logoUrl'])

    def test_cdn_html_fallback_is_not_accepted_as_an_image(self):
        class Headers:
            def get_content_type(self): return 'text/html'
        class Response:
            headers = Headers()
            def __enter__(self): return self
            def __exit__(self, *args): pass
        asset = logos.plan(self.source, 'https://cdn.example')[0][0]
        with patch.object(logos, 'urlopen', return_value=Response()), patch.object(logos.time, 'sleep'):
            with self.assertRaisesRegex(ValueError, 'content type'):
                logos.verify_asset(asset)


if __name__ == '__main__':
    unittest.main()
