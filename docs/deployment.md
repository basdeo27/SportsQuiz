# Automatic releases from main

The `Build and deploy` GitHub Actions workflow runs on pull requests targeting main and on pushes to main (including PR merges). Direct pushes to main also deploy; protect main with required PR checks if releases must only follow a merge.

## Release sequence

1. Run backend tests and build the Spring Boot JAR, run deployment-helper tests, and build the frontend. No production credentials are exposed to PR checks.
2. Package the tested JAR and frontend as one commit-identified Actions artifact.
3. For main only, assume the AWS deployment role using GitHub OIDC and authenticate Docker to private ECR.
4. Build a small runtime image from that exact JAR using Dockerfile.release, then push it with the full commit SHA as its immutable tag. Rerunning a failed job reuses an existing commit image.
5. Save the frontend archive, checksum, image URI and ECR digest under `s3://<site-bucket>/_releases/<commit>/`. Existing published releases are reused intact on reruns.
6. Deploy the image to Lightsail, wait for the exact deployment version to become ACTIVE, and check backend health/API responses.
7. Upload frontend assets first, then index.html and release.json. Keep previous hashed assets and release archives. Invalidate the HTML/release marker, wait for CloudFront, and verify the expected frontend commit and same-origin API.
8. Only after success, update `_releases/latest.json` and write a GitHub Actions release summary.

A newer merge never cancels a release halfway through. Production deploy, setup and rollback workflows share one concurrency group. PR checks can be superseded by newer PR commits. GitHub retains only the newest pending run in a concurrency group, so intermediate queued commits can be skipped; the active release finishes.

If a release fails after deployment starts and there is a previous recorded release, the helper attempts to restore both that image and its frontend archive, then marks the workflow failed. The first automated release has no prior recorded frontend/image pair to restore; Lightsail itself retains its old deployment when a new one fails to start, but a failure after activation may need a manual recovery.

## One-time GitHub configuration

Create the `production` GitHub Environment. Restrict deployment branches to main. No reviewer gate is needed if merges should deploy automatically. Set these as repository or production-environment **variables**:

| Variable | Value |
| --- | --- |
| AWS_DEPLOY_ROLE_ARN | IAM role GitHub Actions assumes through OIDC |
| AWS_INFRA_ROLE_ARN | Optional separate role for manual infrastructure setup; defaults to the deploy role |
| ECR_REPOSITORY | Your existing private ECR repository name, without registry hostname |
| AWS_REGION | AWS region; defaults to eu-west-2 |
| LIGHTSAIL_SERVICE_NAME | Existing container service name |
| S3_BUCKET | Existing frontend bucket name |
| CLOUDFRONT_DISTRIBUTION_ID | Existing frontend distribution ID |
| FRONTEND_URL | Public HTTPS site root; defaults to https://funsportsquiz.com |
| BACKEND_URL | Public HTTPS Lightsail backend root, used only for infrastructure setup |
| MAIL_FROM | Optional verified SES sender; defaults to noreply@funsportsquiz.com |
| DAILY_LOGIN_REQUIRED | `false` (default) lets guests play today's challenge unranked; `true` requires login before playing |

Existing AWS_REGION, LIGHTSAIL_SERVICE_NAME, S3_BUCKET, CLOUDFRONT_DISTRIBUTION_ID and VITE_API_BASE_URL secrets are accepted as fallbacks. VITE_API_BASE_URL is now only the infrastructure helper's backend-origin fallback; browser requests remain relative /v0.

Change `DAILY_LOGIN_REQUIRED` and run a deployment to apply it to the backend.
No separate frontend configuration/build is needed for this switch. Leaderboard
entry and account history always require login; guest results cannot be claimed
after signing in.

Application **secrets**, as needed:

| Secret | Purpose |
| --- | --- |
| APP_AWS_ACCESS_KEY_ID / APP_AWS_SECRET_ACCESS_KEY | Application DynamoDB/SES credentials, distinct from the temporary CI role |
| MONITORING_API_KEY | At least 32 characters to enable the private monitoring summary |
| MISTRAL_API_KEY | Optional AI summaries |

Existing AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY secrets are accepted as runtime-credential fallbacks for migration. If no APP_AWS_* secrets are supplied, the deployment helper preserves credentials already configured in the active Lightsail container. Other existing runtime settings and sidecar containers are also preserved. CI's temporary AWS credentials are never copied into the application. Provide both runtime credential secrets together when rotating them.

## AWS OIDC and permissions

Create/trust GitHub's OIDC identity provider (`https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`). Scope the role to this repository's **production environment**, not every GitHub repository. For repositories using the traditional subject format, the trust condition is:

```json
{
  "StringEquals": {
    "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
    "token.actions.githubusercontent.com:sub": "repo:OWNER/REPOSITORY:environment:production"
  }
}
```

Replace OWNER/REPOSITORY with the real repository. Newer repositories or custom OIDC configurations may use immutable ID-qualified subject claims; match the actual subject rather than weakening the trust condition. The production environment must restrict deployment branches to main.

Routine deployment role permissions:

- ECR GetAuthorizationToken, DescribeRepositories, DescribeImages, BatchCheckLayerAvailability, InitiateLayerUpload, UploadLayerPart, CompleteLayerUpload, PutImage, BatchGetImage and GetDownloadUrlForLayer. Scope repository actions to the release repository; GetAuthorizationToken uses resource `*`.
- Lightsail GetContainerServices and CreateContainerServiceDeployment for the intended service, scoped where the service supports resource-level permissions.
- S3 ListBucket for the frontend bucket; GetObject/PutObject for frontend assets and `_releases/*`. Routine releases do not require DeleteObject.
- CloudFront CreateInvalidation/GetInvalidation for the intended distribution. Routine application releases do not update distribution configuration.

The optional infrastructure role additionally needs ECR repository creation/tag-mutability/policy access, Lightsail image-puller role activation permissions (including AWS-required IAM setup permissions), and CloudFront GetDistributionConfig/UpdateDistribution/GetDistribution. It uses the same repository/environment OIDC restriction. Follow AWS's private ECR/Lightsail permission guide when provisioning that role.

Application credentials need the DynamoDB/SES permissions described in production-readiness.md. Switching CI to OIDC does not create a runtime IAM role for the application.

## Bootstrap once

The ECR repository, Lightsail pull permissions and CloudFront API/media origins must be configured before an automatic release. The existing backend should be healthy at BACKEND_URL.

1. Add the GitHub variables, application secrets and OIDC roles above.
2. In Actions, run **Configure deployment infrastructure** from main. It creates/reuses ECR, enforces immutable tags, enables private image access for Lightsail, preserves existing ECR policy statements and configures first-party API/media routing.
3. Run **Build and deploy** from main, or merge the next PR. Later releases require no ECR upload or console deployment steps.

The initial merge that introduces these workflows can fail its preflight until bootstrap is complete; it will not deploy a container before those checks pass. Setup changes only run when explicitly selecting the infrastructure workflow, not on each merge.

## Rollback

In Actions select **Roll back production**, choose main and enter the full commit SHA of a previously published release. No rebuild occurs: the helper downloads that release's manifest/frontend, verifies the archive checksum and the ECR image digest, then redeploys both and smoke checks them.

Keep ECR commit images and S3 release archives for the desired rollback window. Old hashed frontend assets are intentionally retained so existing browser tabs continue working during releases. Any cleanup policy should retain assets needed by supported releases. GitHub's downloadable build artifact retention is separate from the durable S3 release archive.

Rollback uses current runtime secrets/settings, not copies of old secret values. Database changes must remain compatible with the releases you intend to roll back to. A failed or unhealthy rollback remains a failed workflow and needs operator attention.

## Local verification

```bash
python3 -m unittest discover -s scripts/tests -v
QUIZ_STORAGE=local ./gradlew --no-daemon test bootJar
cd frontend && npm ci && npm run build
```

Release-helper tests use temporary files and mocked AWS calls. No live ECR push, deployment, infrastructure configuration or email is performed by local validation.

References:

- [Lightsail private ECR access](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-container-service-ecr-private-repo-access.html)
- [GitHub OIDC with AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
- [GitHub workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts)

## Daily challenge media

Frontend builds package referenced daily media as content-addressed files in `dist/media/daily/`. Releases upload these to `s3://<S3_BUCKET>/media/daily/` before backend activation, using the existing deployment role's S3 permissions. CloudFront's default S3 origin serves them; `/assets/daily/*` stays a backend route for local references and older clients. The production API resolves local JSON references to `/media/daily/<sha256>.<extension>` when revealing hints. No public bucket access or additional infrastructure workflow is required for the existing site origin.

Media is part of the checksummed release archive and is retained during rollbacks. Use the combined main deployment when adding/changing hints. See [daily hint authoring](daily-hints.md#s3--cloudfront-publishing) for local behavior, optional CDN URL configuration and validation.
