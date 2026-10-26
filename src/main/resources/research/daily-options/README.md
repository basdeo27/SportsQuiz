# Unscheduled daily options

These are authoring drafts, outside `data/daily/`, so the application does not
load or schedule them. Each JSON file wraps an eventual daily `challenge` with
author-only selection, source and media-review notes. Do not copy the wrapper
or authoring notes into the player-facing hint data.

## Derek Jeter

`derek-jeter.json` uses the user's confirmed order:

1. Prepared 2014 final home-game walk-off audio, nine seconds.
2. “I’d boo myself, too.” — 2004 slump reaction.
3. Original November calendar image.
4. 1999 regular-season line: .349 AVG, 219 H, 24 HR, 102 RBI, 19 SB.
5. Career accomplishments board, using the existing reusable hint type.

The calendar SVG shows only a month page and dates. It contains no player image,
name, nickname or team. The existing MP3 matches the checksum in its source
record; it was moved from the clipping tool's generated path to the neutral
`assets/daily/2026-10-26/hint-1.mp3` path before this conversion. That directory
name does **not** schedule this option for October 26.

**Outstanding review:** the audio could not be listened to/transcribed by this
environment. Listen to all nine seconds and trim/bleep any spoken target names,
surname, accepted aliases, identifying nickname or crowd chants. Its neutral
filename and verified checksum do not certify the spoken content. Preserve the
source URL, 52–61 second range and checksum in the authoring record if re-edited.

To schedule after review, add a `date` to the `challenge` object, append that
object to the corresponding `data/daily/YYYY-MM.json`, and validate the media.
Do not add this whole draft file to that directory. The normal release will
package its referenced local assets and publish them to S3/CloudFront.
