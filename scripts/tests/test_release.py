"""Release safety checks. AWS, Docker and live infrastructure are never contacted."""
from contextlib import contextmanager
import importlib.util
import io
import json
import os
import sys
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('sports_quiz_release', ROOT / 'scripts/release.py')
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
COMMIT = 'a' * 40
PREVIOUS = 'b' * 40
IMAGE = f'123456789012.dkr.ecr.eu-west-2.amazonaws.com/quiz:{COMMIT}'
DIGEST = 'sha256:' + 'c' * 64


@contextmanager
def cwd(path):
    old = Path.cwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(old)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.environment = patch.dict(os.environ, {
            'AWS_REGION': 'eu-west-2', 'RELEASE_COMMIT': COMMIT, 'ECR_REPOSITORY': 'quiz',
            'S3_BUCKET': 'site-bucket', 'CLOUDFRONT_DISTRIBUTION_ID': 'distribution',
            'SITE_URL': 'https://app.example.test', 'SERVICE_NAME': 'quiz-service',
            'BACKEND_URL': 'https://backend.example.test',
            'APP_AWS_ACCESS_KEY_ID': 'application-key', 'APP_AWS_SECRET_ACCESS_KEY': 'application-secret',
        }, clear=True)
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def manifest(self, commit=COMMIT):
        return {'schema': 1, 'commit': commit, 'frontendSha256': 'f' * 64,
                'image': IMAGE.replace(COMMIT, commit), 'imageDigest': DIGEST}

    def test_backend_smoke_check_root_uses_configured_backend(self):
        with patch.object(release, 'current_service') as service:
            self.assertEqual(release.backend_root(), 'https://backend.example.test')
            service.assert_not_called()

    def test_frontend_only_deploy_publishes_built_files_without_backend_or_release_history(self):
        with tempfile.TemporaryDirectory() as temporary, cwd(temporary):
            Path('frontend/dist').mkdir(parents=True)
            Path('frontend/dist/index.html').write_text('tested frontend')
            def verify(directory, manifest):
                self.assertEqual(manifest, {'commit': COMMIT})
                self.assertEqual((directory / 'frontend/index.html').read_text(), 'tested frontend')
                self.assertEqual(json.loads((directory / 'frontend/release.json').read_text()), {'commit': COMMIT})
            with patch.object(release, 'publish_frontend', side_effect=verify) as publish:
                with patch.object(release, 'aws') as api, patch.object(release, 'current_service') as service:
                    release.deploy_frontend()
                    publish.assert_called_once()
                    api.assert_not_called()
                    service.assert_not_called()

    def test_frontend_only_deploy_rejects_missing_build_before_upload(self):
        with tempfile.TemporaryDirectory() as temporary, cwd(temporary):
            with patch.object(release, 'aws') as api:
                with self.assertRaisesRegex(release.ReleaseError, 'Build the frontend'):
                    release.deploy_frontend()
                api.assert_not_called()

    def test_backend_smoke_check_root_falls_back_to_lightsail(self):
        os.environ.pop('BACKEND_URL')
        with patch.object(release, 'current_service', return_value={'url': 'https://lightsail.example.test/'}):
            self.assertEqual(release.backend_root(), 'https://lightsail.example.test')

    def service(self):
        return {'privateRegistryAccess': {'ecrImagePullerRole': {'isActive': True}}, 'currentDeployment': {
            'containers': {'backend': {'image': 'old-image', 'environment': {'KEEP_ME': 'yes'}, 'ports': {'8080': 'HTTP'}},
                           'sidecar': {'image': 'logs', 'environment': {'OTHER': 'yes'}}},
            'publicEndpoint': {'containerName': 'backend', 'containerPort': 8080},
        }}

    def test_prepare_packages_the_tested_jar_and_matching_frontend(self):
        with tempfile.TemporaryDirectory() as temporary, cwd(temporary):
            Path('build/libs').mkdir(parents=True)
            Path('build/libs/app.jar').write_bytes(b'tested boot jar')
            Path('build/libs/app-plain.jar').write_bytes(b'not the boot jar')
            Path('frontend/dist/assets').mkdir(parents=True)
            Path('frontend/dist/index.html').write_text('<html>release</html>')
            Path('frontend/dist/assets/app.js').write_text('console.log(1)')

            release.prepare(Path('release'))
            manifest = release.load_manifest('release/manifest.json', COMMIT)
            release.extract_frontend(Path('release/frontend.tar.gz'), Path('restored'), manifest)

            self.assertEqual(Path('release/backend/app.jar').read_bytes(), b'tested boot jar')
            self.assertEqual(json.loads(Path('restored/release.json').read_text()), {'commit': COMMIT})
            self.assertEqual(Path('restored/assets/app.js').read_text(), 'console.log(1)')
            self.assertNotIn('secret', Path('release/manifest.json').read_text())

    def test_rejects_archive_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'frontend.tar.gz'
            path.write_bytes(b'corrupt')
            with self.assertRaisesRegex(release.ReleaseError, 'checksum'):
                release.extract_frontend(path, Path(temporary) / 'output', self.manifest())

    def test_rejects_archive_traversal_and_links(self):
        for name, symlink in [('../escape', False), ('link', True)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                archive = Path(temporary) / 'frontend.tar.gz'
                with tarfile.open(archive, 'w:gz') as bundle:
                    member = tarfile.TarInfo(name)
                    if symlink:
                        member.type = tarfile.SYMTYPE
                        member.linkname = '../escape'
                        bundle.addfile(member)
                    else:
                        member.size = 1
                        bundle.addfile(member, io.BytesIO(b'x'))
                manifest = {**self.manifest(), 'frontendSha256': release.checksum(archive)}
                with self.assertRaisesRegex(release.ReleaseError, 'unsafe'):
                    release.extract_frontend(archive, Path(temporary) / 'output', manifest)
                self.assertFalse((Path(temporary) / 'escape').exists())

    def test_commit_and_site_inputs_cannot_inject_paths_or_commands(self):
        for value in ['main', '../main', 'a' * 39, 'a' * 40 + '\nvalue']:
            with self.assertRaises(release.ReleaseError):
                release.commit_sha(value)
        for value in ['http://example.test', 'https://user:pass@example.test', 'https://example.test/v0', 'https://example.test#fragment']:
            with self.assertRaises(release.ReleaseError):
                release.https_root(value)

    def test_ci_temporary_credentials_are_not_copied_to_runtime(self):
        os.environ.pop('APP_AWS_ACCESS_KEY_ID')
        os.environ.pop('APP_AWS_SECRET_ACCESS_KEY')
        os.environ.update(AWS_ACCESS_KEY_ID='temporary-ci-key', AWS_SECRET_ACCESS_KEY='temporary-ci-secret')
        environment = release.runtime_environment({'AWS_ACCESS_KEY_ID': 'old-runtime-key', 'AWS_SECRET_ACCESS_KEY': 'old-runtime-secret'})
        self.assertEqual(environment['AWS_ACCESS_KEY_ID'], 'old-runtime-key')
        self.assertEqual(environment['SPRING_PROFILES_ACTIVE'], 'prod')
        with self.assertRaisesRegex(release.ReleaseError, 'CI role credentials'):
            release.runtime_environment({})

    def test_daily_login_requirement_is_configurable_in_runtime_environment(self):
        for value in ('true', 'false'):
            with self.subTest(value=value), patch.dict(os.environ, {'DAILY_LOGIN_REQUIRED': value}):
                environment = release.runtime_environment({'DAILY_LOGIN_REQUIRED': 'true'})
                self.assertEqual(environment['DAILY_LOGIN_REQUIRED'], value)

    def test_preserves_active_container_name_sidecars_and_extra_settings(self):
        request = release.backend_request(self.service(), self.manifest())
        self.assertEqual(request['containers']['backend']['image'], IMAGE)
        self.assertEqual(request['containers']['backend']['environment']['KEEP_ME'], 'yes')
        self.assertEqual(request['containers']['sidecar']['image'], 'logs')
        self.assertEqual(request['publicEndpoint']['containerName'], 'backend')
        self.assertEqual(request['containers']['backend']['environment']['AWS_ACCESS_KEY_ID'], 'application-key')

    def test_backend_failure_never_publishes_new_frontend(self):
        with patch.object(release, 'validate_image'), patch.object(release, 'publish_daily_media'), patch.object(release, 'activate_backend', side_effect=release.ReleaseError('unhealthy')):
            with patch.object(release, 'publish_frontend') as publish:
                with self.assertRaisesRegex(release.ReleaseError, 'unhealthy'):
                    release.activate_release(Path('release'), self.manifest())
                publish.assert_not_called()

    def test_daily_media_is_published_before_backend_activation(self):
        events = []
        with patch.object(release, 'validate_image'), patch.object(release, 'record_success'):
            with patch.object(release, 'publish_daily_media', side_effect=lambda *args: events.append('media')):
                with patch.object(release, 'activate_backend', side_effect=lambda *args: events.append('backend')):
                    with patch.object(release, 'publish_frontend', side_effect=lambda *args: events.append('frontend')):
                        release.activate_release(Path('release'), self.manifest())
        self.assertEqual(events, ['media', 'backend', 'frontend'])

    def test_daily_media_upload_is_immutable_and_preserves_old_objects(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / 'frontend/media/daily').mkdir(parents=True)
            (directory / 'frontend/index.html').write_text('html')
            with patch.object(release, 'aws') as api:
                release.publish_daily_media(directory, self.manifest())
            arguments = api.call_args.args
            self.assertIn('s3://site-bucket/media/daily', arguments)
            self.assertIn('public,max-age=31536000,immutable', arguments)
            self.assertNotIn('--delete', arguments)

    def test_assets_publish_before_html_without_deleting_old_assets_or_releases(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / 'frontend').mkdir()
            (directory / 'frontend/index.html').write_text('html')
            with patch.object(release, 'aws', return_value={'Invalidation': {'Id': 'invalidation'}}) as api:
                with patch.object(release, 'http_json', side_effect=[{'commit': COMMIT}, {'LOGO': ['NBA']}]) as smoke:
                    release.publish_frontend(directory, self.manifest())
                    self.assertEqual([call.args[0] for call in smoke.call_args_list], [
                        'https://app.example.test/release.json',
                        'https://backend.example.test/v0/quiz/leagues',
                    ])
            calls = [call.args for call in api.call_args_list]
            self.assertEqual(calls[0][:2], ('s3', 'sync'))
            self.assertIn('index.html', calls[0])
            self.assertIn('_releases/*', calls[0])
            self.assertTrue(str(calls[1][2]).endswith('index.html'))
            self.assertNotIn('--delete', [argument for call in calls for argument in call])
            self.assertEqual(calls[-1][:3], ('cloudfront', 'wait', 'invalidation-completed'))

    def test_failed_new_release_restores_recorded_image_and_frontend(self):
        previous, current = self.manifest(PREVIOUS), self.manifest()
        with patch.object(release, 'http_json', return_value={'LOGO': ['NBA']}), patch.object(release, 'current_service', return_value=self.service()):
            with patch.object(release, 'remote_json', side_effect=[previous, current]):
                with patch.object(release, 'download_release', side_effect=[current, previous]) as download:
                    with patch.object(release, 'activate_release', side_effect=[release.ReleaseError('frontend unhealthy'), None]) as activate:
                        with self.assertRaisesRegex(release.ReleaseError, 'previous recorded release was restored'):
                            release.deploy(Path('release'))
                        self.assertEqual(download.call_args_list[-1].args[0], PREVIOUS)
                        self.assertEqual(activate.call_args_list[-1].args[1]['commit'], PREVIOUS)

    def test_access_denied_is_not_misreported_as_a_missing_release(self):
        with patch.object(release, 'aws', side_effect=release.AwsError('s3api head-object', 'AccessDenied')):
            with self.assertRaises(release.AwsError):
                release.remote_json('_releases/latest.json')

    def test_missing_image_is_distinguished_from_permissions_failures(self):
        with patch.object(release, 'aws', side_effect=release.AwsError('ecr describe-images', 'ImageNotFoundException')):
            self.assertIsNone(release.image_details(COMMIT))
        with patch.object(release, 'aws', side_effect=release.AwsError('ecr describe-images', 'AccessDeniedException')):
            with self.assertRaises(release.AwsError):
                release.image_details(COMMIT)

    def test_aws_errors_never_echo_runtime_secrets(self):
        failure = subprocess.CompletedProcess([], 1, stdout='', stderr='An error occurred (AccessDenied) app-password=never-print-this')
        with patch.object(release.subprocess, 'run', return_value=failure):
            with self.assertRaises(release.AwsError) as captured:
                release.aws('lightsail', 'create-container-service-deployment', '--cli-input-json', 'file://temporary')
            self.assertIn('AccessDenied', str(captured.exception))
            self.assertNotIn('never-print-this', str(captured.exception))


class SetupTests(unittest.TestCase):
    def test_infrastructure_setup_preserves_other_repository_permissions(self):
        spec = importlib.util.spec_from_file_location('sports_quiz_setup', ROOT / 'scripts/setup_deployment.py')
        setup = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'release': release}):
            spec.loader.exec_module(setup)
        principal = 'arn:aws:iam::123456789012:role/lightsail-puller'
        old_statement = {'Sid': 'KeepExistingConsumer', 'Effect': 'Allow', 'Principal': {'AWS': 'other-role'}, 'Action': ['ecr:BatchGetImage']}
        captured = []

        def api(*arguments, **kwargs):
            if arguments[:2] == ('ecr', 'describe-repositories'):
                return {'repositories': [{'imageTagMutability': 'IMMUTABLE'}]}
            if arguments[:2] == ('ecr', 'get-repository-policy'):
                return {'policyText': json.dumps({'Version': '2012-10-17', 'Statement': [old_statement]})}
            if arguments[:2] == ('ecr', 'set-repository-policy'):
                captured.append(json.loads(arguments[arguments.index('--policy-text') + 1]))
            return {}

        with patch.dict(os.environ, {'AWS_REGION': 'eu-west-2', 'ECR_REPOSITORY': 'quiz', 'SERVICE_NAME': 'service',
                                     'CLOUDFRONT_DISTRIBUTION_ID': 'distribution', 'BACKEND_URL': 'https://backend.example.test'}, clear=True):
            with patch.object(setup, 'aws', side_effect=api), patch.object(setup, 'current_service', return_value={
                'privateRegistryAccess': {'ecrImagePullerRole': {'isActive': True, 'principalArn': principal}}}):
                with patch.object(setup.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)):
                    setup.configure()
        self.assertIn(old_statement, captured[0]['Statement'])
        self.assertEqual(captured[0]['Statement'][-1]['Principal']['AWS'], principal)



if __name__ == '__main__':
    unittest.main()
