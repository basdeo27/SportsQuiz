# Prepare audio hints

From the project root, install FFmpeg and the isolated download dependencies once:

```bash
brew install ffmpeg
make audio-setup
```

Python 3.10+ is required for the downloader. Setup creates `.venv-audio/`; other Python dependencies are not required. Rerun `make audio-setup` to update yt-dlp if a video provider changes its format. FFmpeg supplies both `ffmpeg` and `ffprobe`. YouTube extraction can also require a JavaScript runtime: the tool enables Deno and Node automatically when installed. A recent Node installation used for this project's frontend is suitable; on a fresh machine, install Node or Deno if yt-dlp reports no supported runtime.

## Clip a video URL

```bash
make audio-clip URL="https://www.youtube.com/watch?v=VIDEO_ID" START="1:24" END="1:38" PLAYER="brady"
```

This downloads the best available audio once, caches the native track, extracts the requested 14 seconds, normalises volume, adds 80 ms fades, and encodes a 192 kbps stereo MP3. It validates that the requested range exists and checks the output codec/duration. It strips embedded source titles, artwork and chapter metadata. It does not remove athlete names spoken in the recording: listen before choosing the final timestamps.

- MP3: `src/main/resources/static/assets/daily/audio/<clip-hash>.mp3`. The opaque filename keeps the player/title out of the served URL.
- Source record: `src/main/resources/research/audio-clips/brady/<clip-hash>.json`. Contains source URL/title, source checksum, timestamps, processing settings, output checksum and optional source notes. This stays outside served static files.
- Download cache: `.cache/audio-clips/`, ignored by Git. A new time range from the **same URL** reuses the download without another network request. Different URL spellings can create separate entries. Delete a source's cache directory to refresh a changed upstream recording.

The command prints a ready-to-paste local `AUDIO` hint JSON reference. Preview the MP3, then add that reference to the chosen challenge. Commit the MP3, source record and challenge JSON; the normal combined release packages referenced clips and uploads them to S3/CloudFront. The command itself does not publish a challenge or contact AWS.

## Local files, including MOV

```bash
make audio-clip INPUT="$HOME/Desktop/quizimages/players/brady/short.mov" START="0" END="13" PLAYER="brady"
```

Local files work without yt-dlp or `audio-setup`; they need FFmpeg and Python. Originals are left intact. File contents determine their clip identity, so editing a source file creates a new output filename. A local source record stores its path and checksum.

## Useful options

```bash
# Inspect the source duration/title before deciding on a range.
make audio-clip URL="https://www.youtube.com/watch?v=VIDEO_ID" INFO=1

# Validate arguments and print a plan, without downloading or requiring media tools.
make audio-clip URL="https://www.youtube.com/watch?v=VIDEO_ID" START="1:24" END="1:38" PLAYER="brady" DRY_RUN=1

# Add attribution, recording details or permission notes to the source record.
make audio-clip INPUT="/path/to/recording.mov" START="84" END="98" PLAYER="brady" SOURCE_NOTE="Broadcaster, event date, permission reference"

# Explicit output; existing clips/records are protected unless FORCE=1 is passed.
make audio-clip INPUT="/path/to/recording.mov" START="1:24" END="1:38" PLAYER="brady" OUT="/tmp/preview.mp3"
```

Times accept seconds, `MM:SS` or `HH:MM:SS`, including fractional seconds (`1:24.250`). End is exclusive; it must follow start and fit inside the recording. Use a lowercase player slug, e.g. `ken-griffey-jr`. Changing the range or processing settings creates a new filename; an identical rerun refuses to overwrite and explains `FORCE=1`.

For advanced options, invoke the same script directly (the existing `--url --start --end --out` form still works):

```bash
.venv-audio/bin/python scripts/python/clip_audio.py --input /path/to/source.mov --start 84 --end 98 --player brady --no-normalize --fade 0 --verbose
```

`--no-normalize` preserves the original level, and `--fade 0` disables fades. Normalisation uses FFmpeg's single-pass EBU R128 filter with a -16 LUFS target and -1.5 dBTP peak ceiling; very short/silent clips may not reach the target. Playlists and live/upcoming streams are rejected. Video URLs must be supported by yt-dlp and downloadable without additional access configuration; when downloading fails, the local-file input remains available. Use recordings you can reuse and keep their attribution/permission details in `SOURCE_NOTE`.

Tool references: [yt-dlp](https://github.com/yt-dlp/yt-dlp#embedding-yt-dlp), [FFmpeg loudnorm](https://ffmpeg.org/ffmpeg-filters.html#loudnorm), [FFmpeg fades](https://ffmpeg.org/ffmpeg-filters.html#afade).
