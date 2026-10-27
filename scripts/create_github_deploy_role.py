#!/usr/bin/env python3
"""Create the production GitHub OIDC deployment role using authenticated AWS CLI access."""
import argparse
import json
import os
import subprocess
import sys


def aws(region, *args):
    result = subprocess.run(['aws', '--region', region, '--no-cli-pager', *args, '--output', 'json'],
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else {}


def policies(account, partition, region, repository, bucket, distribution, service_arn, subject):
    provider = f'arn:{partition}:iam::{account}:oidc-provider/token.actions.githubusercontent.com'
    trust = {'Version': '2012-10-17', 'Statement': [{
        'Effect': 'Allow', 'Principal': {'Federated': provider},
        'Action': 'sts:AssumeRoleWithWebIdentity', 'Condition': {'StringEquals': {
            'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
            'token.actions.githubusercontent.com:sub': subject,
        }},
    }]}
    def allow(sid, actions, resource):
        return {'Sid': sid, 'Effect': 'Allow', 'Action': actions, 'Resource': resource}
    permissions = {'Version': '2012-10-17', 'Statement': [
        allow('EcrLogin', ['ecr:GetAuthorizationToken'], '*'),
        allow('ReleaseImages', ['ecr:DescribeRepositories', 'ecr:DescribeImages',
              'ecr:BatchCheckLayerAvailability', 'ecr:InitiateLayerUpload', 'ecr:UploadLayerPart',
              'ecr:CompleteLayerUpload', 'ecr:PutImage', 'ecr:BatchGetImage', 'ecr:GetDownloadUrlForLayer'],
              f'arn:{partition}:ecr:{region}:{account}:repository/{repository}'),
        # AWS does not support resource-level scope for GetContainerServices.
        allow('ReadContainerServices', ['lightsail:GetContainerServices'], '*'),
        allow('DeployService', ['lightsail:CreateContainerServiceDeployment'], service_arn),
        allow('ListSite', ['s3:ListBucket', 's3:GetBucketLocation'], f'arn:{partition}:s3:::{bucket}'),
        allow('PublishSiteAndReleases', ['s3:GetObject', 's3:PutObject'], f'arn:{partition}:s3:::{bucket}/*'),
        allow('InvalidateSite', ['cloudfront:CreateInvalidation', 'cloudfront:GetInvalidation'],
              f'arn:{partition}:cloudfront::{account}:distribution/{distribution}'),
    ]}
    return provider, trust, permissions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', default=os.environ.get('AWS_REGION', 'eu-west-2'))
    parser.add_argument('--repository', default='basdeo27/SportsQuiz')
    parser.add_argument('--role-name', default='SportsQuizGitHubProductionDeploy')
    parser.add_argument('--bucket', default=os.environ.get('S3_BUCKET'))
    parser.add_argument('--ecr-repository', default=os.environ.get('ECR_REPOSITORY'))
    parser.add_argument('--service-name', default=os.environ.get('LIGHTSAIL_SERVICE_NAME'))
    parser.add_argument('--distribution-id', default=os.environ.get('CLOUDFRONT_DISTRIBUTION_ID'))
    parser.add_argument('--oidc-subject', help='Exact custom/immutable GitHub sub, if enabled for this repository')
    args = parser.parse_args()
    for field in ('bucket', 'ecr_repository', 'service_name', 'distribution_id'):
        if not getattr(args, field):
            parser.error('--' + field.replace('_', '-') + ' is required (or set its deployment environment variable)')
    subject = args.oidc_subject or f'repo:{args.repository}:environment:production'
    if '*' in subject or '?' in subject or not subject.startswith('repo:') or not subject.endswith(':environment:production'):
        parser.error('OIDC subject must match the exact repository production environment, without wildcards')
    identity = aws(args.region, 'sts', 'get-caller-identity')
    account, partition = identity['Account'], identity['Arn'].split(':')[1]
    services = aws(args.region, 'lightsail', 'get-container-services', '--service-name', args.service_name)['containerServices']
    if len(services) != 1:
        raise RuntimeError('Could not identify exactly one Lightsail service')
    provider, trust, permissions = policies(account, partition, args.region, args.ecr_repository,
                                           args.bucket, args.distribution_id, services[0]['arn'], subject)
    # Resolve the existing resources before granting access to them.
    aws(args.region, 'ecr', 'describe-repositories', '--repository-names', args.ecr_repository)
    aws(args.region, 's3api', 'head-bucket', '--bucket', args.bucket, '--expected-bucket-owner', account)
    aws(args.region, 'cloudfront', 'get-distribution', '--id', args.distribution_id)
    providers = aws(args.region, 'iam', 'list-open-id-connect-providers')['OpenIDConnectProviderList']
    if provider not in [entry['Arn'] for entry in providers]:
        aws(args.region, 'iam', 'create-open-id-connect-provider',
            '--url', 'https://token.actions.githubusercontent.com', '--client-id-list', 'sts.amazonaws.com')
    else:
        current = aws(args.region, 'iam', 'get-open-id-connect-provider', '--open-id-connect-provider-arn', provider)
        if 'sts.amazonaws.com' not in current['ClientIDList']:
            raise RuntimeError('Existing GitHub OIDC provider must include the sts.amazonaws.com audience')
    # create-role deliberately fails if the name exists; never replace another role's trust/access.
    created = aws(args.region, 'iam', 'create-role', '--role-name', args.role_name,
                  '--description', 'SportsQuiz production deployments from GitHub Actions OIDC',
                  '--assume-role-policy-document', json.dumps(trust))
    arn = created['Role']['Arn']
    try:
        aws(args.region, 'iam', 'put-role-policy', '--role-name', args.role_name,
            '--policy-name', 'SportsQuizProductionDeploy', '--policy-document', json.dumps(permissions))
        actual_trust = aws(args.region, 'iam', 'get-role', '--role-name', args.role_name)['Role']
        actual_policy = aws(args.region, 'iam', 'get-role-policy', '--role-name', args.role_name,
                            '--policy-name', 'SportsQuizProductionDeploy')['PolicyDocument']
        if actual_trust['AssumeRolePolicyDocument'] != trust or actual_policy != permissions:
            raise RuntimeError('IAM policy verification failed')
    except Exception:
        print(f'Role created but setup incomplete: {arn}. Inspect it before retrying.', file=sys.stderr)
        raise
    print(arn)
    print(f'Set the GitHub production environment variable AWS_DEPLOY_ROLE_ARN to {arn}')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
