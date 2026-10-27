"""Deployment role checks; no AWS calls are made."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('github_deploy_role', Path(__file__).resolve().parents[1] / 'create_github_deploy_role.py')
role = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(role)


class GitHubDeployRoleTests(unittest.TestCase):
    def test_trust_is_exactly_the_production_environment_and_sts_audience(self):
        provider, trust, _ = role.policies('123456789012', 'aws', 'eu-west-2', 'quiz', 'site', 'DIST',
                                          'service-arn', 'repo:basdeo27/SportsQuiz:environment:production')
        statement = trust['Statement'][0]
        self.assertEqual(statement['Principal'], {'Federated': provider})
        self.assertEqual(statement['Action'], 'sts:AssumeRoleWithWebIdentity')
        self.assertEqual(statement['Condition']['StringEquals'], {
            'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
            'token.actions.githubusercontent.com:sub': 'repo:basdeo27/SportsQuiz:environment:production'})

    def test_write_permissions_are_scoped_and_do_not_grant_iam_or_deletion(self):
        _, _, policy = role.policies('123456789012', 'aws', 'eu-west-2', 'quiz', 'site', 'DIST',
                                     'service-arn', 'subject')
        for statement in policy['Statement']:
            for action in statement['Action']:
                self.assertNotIn('*', action)
                self.assertNotIn('Delete', action)
                self.assertFalse(action.startswith('iam:'))
            if statement['Resource'] == '*':
                self.assertIn(statement['Sid'], ['EcrLogin', 'ReadContainerServices'])
        scoped = {s['Sid']: s['Resource'] for s in policy['Statement']}
        self.assertEqual(scoped['DeployService'], 'service-arn')
        self.assertEqual(scoped['PublishSiteAndReleases'], 'arn:aws:s3:::site/*')
        self.assertEqual(scoped['ReleaseImages'], 'arn:aws:ecr:eu-west-2:123456789012:repository/quiz')
        self.assertEqual(scoped['InvalidateSite'], 'arn:aws:cloudfront::123456789012:distribution/DIST')

    def test_wildcard_trust_rejected_before_any_aws_call(self):
        with patch('sys.argv', ['role', '--bucket', 'site', '--ecr-repository', 'quiz', '--service-name', 'svc',
                                '--distribution-id', 'DIST', '--oidc-subject', 'repo:*:environment:production']), \
                patch.object(role, 'aws') as aws, patch('sys.stderr'):
            with self.assertRaises(SystemExit):
                role.main()
            aws.assert_not_called()
