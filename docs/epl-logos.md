# EPL logo images

The quiz roster contains **20 clubs**, following the user-specified promotion/
relegation update: **Coventry City, Hull City and Ipswich Town replace West Ham
United, Wolverhampton Wanderers and Burnley**. The three relegated clubs are not
in the active quiz dataset. Membership is maintained explicitly in the manifest
when promotion/relegation changes it; a missing image must not remove a club.

`assets/logos/epl/teams.json` maps supplied files to club IDs, accepted answers
and hints. All **20 clubs** now have supplied images, including the user-approved
Fulham shield, Sunderland and the replacement Arsenal JPEG. There are no
remaining ESPN logo URLs in the EPL quiz pool. `retainedTeams` is empty; it can
preserve a non-relegated club's existing URL during future image updates if a
replacement file is temporarily unavailable.

Uploaded images use the private `sports-quiz-frontend` bucket and CloudFront
distribution `E11UKL2VJ2WK4N`. `assets/logos/epl/published.json` is refreshed after
successful publishing with verified image URLs/checksums and retained external
entries. EPL remains enabled in application configuration. Restart/deploy the
backend to load the updated dataset.

## Upload and activate

Use your AWS CLI login/profile; never add AWS keys to frontend code, JSON URLs
or Vite environment variables. If the session expired, run `aws login` first.

```bash
# Review mappings, generated URLs and source files without contacting AWS.
python3 scripts/publish_epl_logos.py --dry-run

# Upload, verify, then replace the quiz pool and enable EPL.
python3 scripts/publish_epl_logos.py
```

The helper defaults to the existing
`https://d2323rgqey2wlv.cloudfront.net` distribution. Use `--base-url` for another
HTTPS CloudFront/site hostname and `--source` for another local image folder.
`--bucket` optionally asserts an expected bucket; it must match the actual CDN
origin. AWS CLI profile selection uses the normal `AWS_PROFILE` mechanism.

Retained entries are preserved alongside uploaded entries, so publishing does not silently drop non-relegated clubs with missing images.

Before uploading, the helper discovers the matching distribution/S3 origin,
checks private OAC/OAI access, HTTPS enforcement, and all four bucket-level S3
Block Public Access settings. It requires no public ACL and makes no bucket
policy or distribution configuration changes. If the existing infrastructure
does not meet these conditions, it reports the issue for review.

Images retain their original bytes and use SHA-256 object names under
`media/logos/epl/`. Their filenames do not reveal the club. They have the correct
image content type and immutable one-year caching. Unchanged images recorded in the last verified upload inventory are not reuploaded;
their live CDN bytes are still checked on every run. Uploading the same files is
safe to retry; old objects are retained. No cache invalidation is required for
new image content because its hash changes.

Each upload is checked through S3 metadata and an HTTPS CDN GET, including the
image content type and full byte checksum. An HTML fallback with HTTP 200 is
treated as a failure. Only after **all supplied** images verify does the helper replace
`src/main/resources/data/logos/epl.json` and remove EPL from
`quiz.settings.disabled-logo-leagues` in `application.yaml`. A failed or partial
upload leaves the original quiz data and league configuration unchanged.

Restart the local backend to load the new dataset. Local browsers and production
browsers use the same permanent HTTPS image URLs; S3 itself stays private and
the application needs no S3 credentials to show logos. Ordinary `<img>` loading
does not require browser AWS credentials or signed URLs. The CDN image URLs are
viewable by quiz players; private storage does not mean login-restricted image
viewing. [AWS private S3 origin documentation](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html).

Deploy the application changes through the normal main-branch workflow to enable
the updated pool in production. The images are uploaded separately by this
helper and are retained by the existing frontend deployment (which does not
delete old S3 objects). No challenge/application deployment is triggered by the
upload helper itself.

## Checks

```bash
python3 -m unittest discover -s scripts/tests -p test_epl_logos.py -v
```

These cover activation ordering, unmapped files, retained non-relegated clubs, public-access checks,
CDN HTML fallbacks, configuration preservation and the no-network dry run.
