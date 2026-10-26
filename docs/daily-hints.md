# Authoring daily challenge hints

Daily entries live in `src/main/resources/data/daily/YYYY-MM.json`. The date selects the challenge; the order of the `hints` array is the reveal order. Existing types remain supported: TEXT, STAT_LINE, IMAGE, AUDIO and VIDEO.

## Live daily and launch previews

Today's live challenge uses the configured daily timezone (Europe/London by default).
An explicitly authored entry for today takes priority. Otherwise, the backend picks
from all authored daily entries using a date-seeded random selection and serves that
content under today's date. The same date and catalog produce the same pick across
requests, users, instances and restarts. Tomorrow gets a new selection; repeats are
possible with the small pool. Only completed challenge JSON enters this pool; research
notes are not playable challenges. An empty pool still returns no challenge.

Ranked attempts, scores and leaderboards use the live date, rather than the reused
entry's original date. Existing attempts retain their persisted content snapshot.
Changing the pool can change a fallback pick for new attempts: avoid changing it
mid-day, or explicitly schedule today's entry before deploying a content update.

During launch, production enables the existing Practice picker for all authored
dates, including current/future entries. These previews remain unranked and cannot
be submitted to a leaderboard. Ranked production starts still require today's date.
Set `DAILY_ALLOW_PRACTICE_DATE_OVERRIDE=false` and restart/redeploy to restore
past-only practice. This switch does not enable development login or change auth.

## Accomplishments (reusable across players and sports)

Use the new ACCOMPLISHMENTS type for an honours board. Labels and counts come from JSON; the frontend contains no player-specific display logic.

```json
{
  "type": "ACCOMPLISHMENTS",
  "accomplishments": {
    "label": "Career accomplishments",
    "entries": [
      {
        "kind": "TROPHY",
        "name": "Ted Lindsay Award",
        "count": 1,
        "detail": "2003 · awarded as the Lester B. Pearson Award"
      },
      {
        "kind": "ALL_STAR",
        "name": "NHL All-Star",
        "count": 5,
        "detail": "All-Star Game selections"
      },
      {
        "kind": "APPEARANCE",
        "name": "Olympic selections",
        "count": 2,
        "detail": "Played in 2002; selected for 2006, withdrew injured"
      },
      {
        "kind": "MEDALS",
        "name": "World Championship medals",
        "count": 3,
        "medals": [
          { "medal": "SILVER", "count": 1 },
          { "medal": "BRONZE", "count": 2 }
        ]
      }
    ]
  }
}
```

- `kind`: TROPHY, ALL_STAR, APPEARANCE or MEDALS. It chooses a decorative icon; `name` supplies the actual competition/award text.
- `count`: positive integer, displayed as a multiplier. Omit accomplishments with zero counts.
- `detail`: optional text for years or important distinctions. Plain text is escaped by React.
- `medals`: only for MEDALS entries. GOLD/SILVER/BRONZE counts must be positive and add up to `count`. Combine duplicate medal types into one entry.
- `label`: optional on the backend, defaults to Career accomplishments. At least one entry is required.

Multiple competitions can have their own medal rows. Other examples: a TROPHY row for World Cups or championships, an ALL_STAR row for a different league, or an APPEARANCE row for international tournament appearances. No new frontend component is needed.

## Quotes with attribution

TEXT now accepts an optional attribution. Existing text hints without it are unchanged.

```json
{
  "type": "TEXT",
  "value": "The quote goes here.",
  "attribution": "Speaker or context"
}
```

The quote and attribution render separately. Never put the target’s name, surname, initials, accepted alias or identifying nickname in either field, including the fifth hint. Redact within the quote itself using `[name redacted]`; keep unredacted source wording and identifying attribution in author-only research notes. Check captions, alt text, filenames, embedded image text, subtitles and every second of an audio/video clip as well. A research event or media brief is not an approved asset. See [the spoiler review](../src/main/resources/research/hint-spoiler-review.md) for scope and outstanding media checks.

## Local media

Prepare MP3 hints with `make audio-clip URL="https://..." START="1:24" END="1:38" PLAYER="brady"`. The tool also accepts local video/MOV files, caches downloads, and records source details outside served assets. See [audio clipping setup and usage](audio-clips.md).

Copy prepared images into `src/main/resources/static/assets/daily/<date>/` and reference them with `source: LOCAL` and a relative URL such as `assets/daily/2026-10-24/hint-3.png`. Numbered filenames avoid giving the answer away in the URL. The original Desktop folder is not needed at runtime. Vite and the production CloudFront routing already forward these assets to the backend.

## 24 October 2026: Markus Näslund

1. The supplied West Coast Express quote with a separate attribution.
2. 2002–03 regular-season line: 48 goals, 56 assists, 104 points.
3. Supplied blurred NHL 2005 cover.
4. Supplied retired-number banner.
5. The reusable accomplishments board above.

Accepted spellings include Markus Näslund, Markus Naslund, Näslund, Naslund and Nazzy.

The Olympic entry deliberately says **selections**, not appearances or medals: he played in 2002 and was selected for 2006 but withdrew injured. His World Championship medals are one silver (1993) and two bronze (1999, 2002). The Ted Lindsay Award was called the Lester B. Pearson Award when he won it.

Fact-check references:

- [NHL statistics](https://www.nhl.com/player/markus-naslund-8458530/stats)
- [NHLPA: award and five All-Star Game selections](https://www.nhlpa.com/news/a-canuck-captain-honoured/)
- [Swedish Olympic Committee: Olympic appearance, 2006 withdrawal and World Championship medals](https://sok.se/idrottare/idrottare/m/markus-naslund.html)

Checks: `./gradlew test --tests '*DailyAccomplishmentsTest' --tests '*NaslundDailyApiTest'` and `cd frontend && npm test && npm run build`.

## 25 October 2026: Tom Brady

1. “The next one” — supplied 60 Minutes interview context, 2005.
2. Supplied Joaquim Valente image.
3. Supplied 13-second 28–3 audio clip; the longer recordings are excluded.
4. 2007 regular season: 50 TD, 4,806 yards, 8 INT.
5. Seven Super Bowl championships and three NFL MVP awards (regular-season MVPs).

Accepted answers: Tom Brady, Brady and Thomas Brady. Media is packaged locally under neutral dated filenames. The short source has an MP3 extension but a QuickTime container; its MP3 audio was remuxed losslessly into a browser-compatible MP3 file. Original Desktop files are unchanged.

## S3 / CloudFront publishing

Keep authoring local references in JSON, for example:

```json
{ "type": "AUDIO", "url": "assets/daily/2026-10-25/hint-3.mp3", "source": "LOCAL" }
```

`npm run build` in `frontend/` runs `scripts/prepare_daily_media.py` after Vite. Python 3.9+ is required. It validates all referenced local image/audio/video files and packages them into `frontend/dist/media/daily/<sha256>.<extension>`. A missing file or unsafe path fails the build. Remote media is left alone, and the build does not publish challenge JSON or an index of future clues.

The existing main-branch release action uploads those files to the existing `S3_BUCKET` under `media/daily/` **before** activating the backend. CloudFront serves `/media/daily/*` through the same default S3 origin as the frontend. No new bucket, public ACL, API proxy behavior or browser CORS configuration is needed. Keep the bucket's existing CloudFront access policy.

In production, `quiz.daily.media.base-url` defaults to `/media/daily`. When a hint is revealed, the API returns its hashed URL with `source: REMOTE`; the browser fetches it directly through CloudFront. Set `DAILY_MEDIA_BASE_URL=https://your-cdn.example/media/daily` for a separate CDN using the same published keys. Changing the upload bucket is a deployment setting; the default uses the frontend bucket. Files remain bundled in the backend to calculate hashes and support local development.

Local/dev leave the media base URL empty, so the same JSON continues to use backend media through the Vite proxy without AWS credentials. Direct remote HTTPS URLs with `source: REMOTE` also remain supported.

Uploads use immutable one-year caching. Changing file bytes changes the URL, so no media invalidation is needed. Deployment and rollback retain previous media; do not add `--delete` to S3 sync or expire media needed by retained releases. The legacy frontend upload helper also retains previous files. Production browser requests are HTTPS URLs, never `s3://` URLs.

To validate/package without contacting AWS:

```bash
python3 scripts/prepare_daily_media.py --output /tmp/sports-quiz-media
```

To publish normally, commit the media plus JSON and merge/push to main; the existing build/deploy action handles uploading. New media must go through the combined release so the backend and S3 files match.
