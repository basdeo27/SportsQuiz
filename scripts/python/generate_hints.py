#!/usr/bin/env python3
"""
generate_hints.py

Generates Athlete of the Day hint JSON using Claude + web search.

Usage:
    # Set ANTHROPIC_API_KEY in your environment before running this script.

    # Generate all 5 hints for an athlete
    python generate_hints.py --name "Mike Trout" --date 2026-05-03

    # Load existing file and refresh a single hint (0-indexed)
    python generate_hints.py --name "Mike Trout" --date 2026-05-03 --refresh 2

    # Load existing file and refresh all hints
    python generate_hints.py --name "Mike Trout" --date 2026-05-03 --refresh all

    # Specify an input file to refresh from (defaults to <date>_<slug>.json)
    python generate_hints.py --name "Mike Trout" --date 2026-05-03 --refresh 0 --input ./athletes.json

Output is written to ./<date>_<name_slug>.json  (e.g. 2026-05-03_mike_trout.json)
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path

import anthropic

# ---------------------------------------------------------------------------
# Prompt constants
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """You are a sports trivia expert building a Wordle-style "Athlete of the Day" quiz for MLB.

You will be given an athlete's name and asked to generate hint objects.
Each hint must follow strict rules — violating them makes the quiz unfair or too easy.

== HINT TYPES ==
You may use these types, matching the TypeScript sealed class:

  IMAGE   - { "type": "IMAGE", "url": "<direct image URL>", "source": "REMOTE" }
            URL must be a real, publicly accessible image on the internet.
            Find real URLs by thinking about news articles, Wikipedia, sports sites.

  STAT_LINE - {
      "type": "STAT_LINE",
      "statLine": {
          "label": "<year> · <league>",    e.g. "2010 · AL"
          "stats": [ { "name": "HR", "value": "54" }, ... ]
      }
  }

  TEXT    - { "type": "TEXT", "value": "<string>" }
            Use for quotes, broadcast calls, redacted headlines, incident descriptions.

  AUDIO   - { "type": "AUDIO", "url": "<url>", "source": "REMOTE" }
  VIDEO   - { "type": "VIDEO", "url": "<url>", "source": "REMOTE" }

== DIFFICULTY PROGRESSION ==
Hints must get progressively more obvious from hint 1 (hardest) to hint 5 (easiest).

  Hint 1 (ICE COLD)   - Very obscure. Walk-up song, an opponent/rival photo, scoreboard from
                        a specific famous game, a redacted box score incident.
  Hint 2 (LUKEWARM)   - Stat line (year + league only). Choose a season that's memorable but
                        not the most famous one — make them work for it.
  Hint 3 (WARMING UP) - A famous quote (name redacted), a broadcast call with name bleeped,
                        or a famous incident described in text (no name).
  Hint 4 (HOT)        - Photo of a rival, opponent, or stadium associated with a famous moment.
                        Still no athlete photo.
  Hint 5 (RED HOT)    - Another quote or broadcast call that's more famous/recognisable.
                        Or a very distinctive stat line from their most iconic season.

== ABSOLUTE IMAGE RULES — NEVER VIOLATE ==
  ✗ Never show the athlete themselves (face, silhouette, back of jersey with number/name)
  ✗ Never show teammates
  ✗ Never show the team manager
  ✗ Never include team name or player number in image descriptions
  ✓ DO show: rivals/opponents (e.g. the pitcher who gave up the famous HR),
             stadiums, award trophies, crowd reactions, equipment (bat mid-air, no hands),
             scoreboards from famous games, people adjacent to a scandal (e.g. the interpreter)

== STAT LINE RULES ==
  ✗ Never include team name or position in the label
  ✓ Label format: "<year> · <league>"  e.g. "2010 · AL" or "2023 · NL"
  ✓ For two-way players (like Ohtani): show ONLY batting stats OR only pitching stats — never both
    (showing both immediately identifies a two-way player)

== TEXT HINT RULES ==
  ✗ Never include the athlete's full name, first name, surname, initials, accepted alias,
    or identifying nickname in any hint, including hint 5. This applies to value,
    attribution, captions, alt text, stat/award labels, URLs and filenames.
  ✗ Never use an image, video subtitle or audio clip that prints or speaks the answer.
    Review the entire asset, including chants, interview questions and introductions.
  ✓ Redact the actual display text; an author note saying "redact later" is insufficient.
  ✓ Keep answer-bearing source titles, links and original attributions in author notes.
  ✓ Use neutral numbered media filenames. Do not use the target's name as a file path.
  ✓ Bleep/redact names in quotes: use [NAME REDACTED] or ████
  ✓ Broadcast calls should have the athlete's name bleeped: "████ hits it OUT OF HERE!"
  ✓ Incidents should describe the situation without naming the athlete

== OUTPUT FORMAT ==
Return ONLY a valid JSON array of hint objects. No markdown, no explanation, no preamble.
The array must contain exactly the number of hints requested.

Example of valid output for 5 hints:
[
  { "type": "IMAGE", "url": "https://...", "source": "REMOTE" },
  { "type": "STAT_LINE", "statLine": { "label": "2010 · AL", "stats": [{"name":"HR","value":"54"}] } },
  { "type": "TEXT", "value": "\"████ hits it OUT OF HERE!\" — ESPN, 2015 ALDS Game 5" },
  { "type": "IMAGE", "url": "https://...", "source": "REMOTE" },
  { "type": "TEXT", "value": "\"I never thought I'd be a 50 home run guy.\"" }
]
"""

HINT_GENERATION_PROMPT = """Generate {count} hint(s) for the MLB athlete: {name}

{context}

Rules reminder:
- Hints {hint_indices} (out of 5 total, 1-indexed, where 1=hardest, 5=easiest)
- IMAGE hints: find real publicly accessible URLs — search your knowledge for images on
  Wikipedia, Bleacher Report, Sports Illustrated, ESPN, etc. The URL must be a direct image
  URL ending in .jpg/.png/.webp or a CDN path.
- No athlete photos. No teammate photos. No manager photos.
- STAT_LINE label = "<year> · <league>" only. No team, no position.
- All hints must hide the athlete's names, initials, aliases and identifying nicknames, including metadata and media URLs. Redact the actual displayed text, including any attribution. Verify media content separately.

Return ONLY a JSON array of {count} hint object(s). Nothing else."""

RESEARCH_PROMPT = """Research the MLB athlete: {name}

Provide a detailed briefing covering:
1. Most famous moments and controversies (specific game, date, opponent)
2. Best statistical seasons (year, key stats, league)
3. Famous quotes they've said
4. Famous broadcast calls about them (caller, moment)
5. Rivals and opponents associated with famous moments
6. Off-field incidents or notable stories
7. Walk-up song if known
8. Stadiums most associated with them
9. Awards and trophies won

Be specific — include years, opponents, exact stats. This will be used to generate
trivia hints, so accuracy matters.
"""

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def name_to_slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def output_path(date: str, name: str) -> Path:
    return Path(f"{date}_{name_to_slug(name)}.json")


def load_existing(path: Path) -> dict | None:
    if path.exists():
        with open(path) as f:
            return json.load(f)
    return None


def save(path: Path, data: dict):
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  ✓ Written to {path}")


def parse_hint_json(raw: str) -> list:
    """Parse JSON array from model response, stripping any accidental markdown fences."""
    raw = raw.strip()
    # Strip ```json ... ``` fences if present
    raw = re.sub(r"^```(?:json)?\s*", "", raw)
    raw = re.sub(r"\s*```$", "", raw)
    result = json.loads(raw)
    if not isinstance(result, list):
        raise ValueError(f"Expected JSON array, got {type(result)}")
    return result


# ---------------------------------------------------------------------------
# Core generation
# ---------------------------------------------------------------------------

RATE_LIMIT_SLEEP_SECONDS = 60


def create_with_retry(client: anthropic.Anthropic, **kwargs):
    """Call client.messages.create, sleeping and retrying on 429 rate limit errors."""
    while True:
        try:
            return client.messages.create(**kwargs)
        except anthropic.RateLimitError as exc:
            retry_after = int(getattr(exc, "response", None) and
                              exc.response.headers.get("retry-after", 0) or 0)
            sleep_secs = retry_after if retry_after > 0 else RATE_LIMIT_SLEEP_SECONDS
            print(f"  ⏳ Rate limited (429). Sleeping {sleep_secs}s before retrying...", flush=True)
            time.sleep(sleep_secs)


def research_athlete(client: anthropic.Anthropic, name: str) -> str:
    """Use Claude with web search to build a research briefing on the athlete."""
    print(f"  → Researching {name}...")
    response = create_with_retry(
        client,
        model="claude-sonnet-4-6",
        max_tokens=2000,
        tools=[{"type": "web_search_20250305", "name": "web_search"}],
        messages=[{"role": "user", "content": RESEARCH_PROMPT.format(name=name)}],
    )
    # Collect all text blocks from the response
    text_parts = [b.text for b in response.content if hasattr(b, "text")]
    return "\n".join(text_parts)


def generate_hints(
    client: anthropic.Anthropic,
    name: str,
    hint_indices: list[int],  # 1-indexed positions to generate
    research: str,
) -> list[dict]:
    """Generate hint objects for the given 1-indexed positions."""
    count = len(hint_indices)
    indices_str = ", ".join(str(i) for i in hint_indices)

    context = f"Athlete research briefing:\n{research}" if research else ""

    prompt = HINT_GENERATION_PROMPT.format(
        count=count,
        name=name,
        context=context,
        hint_indices=indices_str,
    )

    print(f"  → Generating hint(s) {indices_str}...")
    response = create_with_retry(
        client,
        model="claude-sonnet-4-6",
        max_tokens=2000,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = response.content[0].text
    hints = parse_hint_json(raw)

    if len(hints) != count:
        raise ValueError(
            f"Expected {count} hint(s), model returned {len(hints)}"
        )
    return hints


def build_athlete_object(
    name: str,
    date: str,
    hints: list[dict],
) -> dict:
    """Build the full athlete JSON object."""
    # Generate answer aliases: full name + last name + common nickname placeholder
    parts = name.split()
    answers = [name]
    if len(parts) > 1:
        answers.append(parts[-1])  # last name only

    return {
        "date": date,
        "answer": name,
        "answers": answers,
        "hints": hints,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Generate Athlete of the Day hint JSON using Claude."
    )
    parser.add_argument("--name", required=True, help='Athlete full name, e.g. "Mike Trout"')
    parser.add_argument("--date", required=True, help="Date string, e.g. 2026-05-03")
    parser.add_argument(
        "--refresh",
        default=None,
        help='Hint to refresh: 0-indexed integer (0–4), or "all"',
    )
    parser.add_argument(
        "--input",
        default=None,
        help="Input JSON file to refresh from (default: auto-detected from name+date)",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="Output JSON file path (default: <date>_<name_slug>.json)",
    )
    parser.add_argument(
        "--no-research",
        action="store_true",
        help="Skip web research step (faster, less accurate)",
    )
    args = parser.parse_args()

    # Validate date format
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", args.date):
        print(f"Error: --date must be in YYYY-MM-DD format, got: {args.date}")
        sys.exit(1)

    # Resolve paths
    out_path = Path(args.output) if args.output else output_path(args.date, args.name)
    in_path = Path(args.input) if args.input else out_path

    # Init client (reads ANTHROPIC_API_KEY from env)
    api_key = os.environ.get("ANTHROPIC_API_KEY", "").strip()
    if not api_key:
        print("Error: ANTHROPIC_API_KEY environment variable not set.")
        sys.exit(1)
    client = anthropic.Anthropic(api_key=api_key)

    print(f"\n🎯 Athlete of the Day Generator")
    print(f"   Athlete : {args.name}")
    print(f"   Date    : {args.date}")

    # ------------------------------------------------------------------
    # Determine which hints to (re)generate
    # ------------------------------------------------------------------
    existing: dict | None = None
    hints: list[dict | None] = [None] * 5  # 5 slots, 0-indexed

    if args.refresh is not None:
        # Load existing file
        existing = load_existing(in_path)
        if existing is None:
            print(f"\nError: --refresh specified but no existing file found at {in_path}")
            print("Run without --refresh first to generate the initial set.")
            sys.exit(1)

        current_hints = existing.get("hints", [])
        # Populate slots from existing
        for i, h in enumerate(current_hints[:5]):
            hints[i] = h

        if args.refresh.lower() == "all":
            indices_to_generate = [1, 2, 3, 4, 5]  # 1-indexed
            print(f"   Mode    : Refresh ALL hints\n")
        else:
            try:
                idx_0 = int(args.refresh)
            except ValueError:
                print(f"Error: --refresh must be an integer (0–4) or 'all', got: {args.refresh}")
                sys.exit(1)
            if not 0 <= idx_0 <= 4:
                print(f"Error: --refresh index must be 0–4, got: {idx_0}")
                sys.exit(1)
            indices_to_generate = [idx_0 + 1]  # convert to 1-indexed
            print(f"   Mode    : Refresh hint {idx_0} (position {idx_0 + 1}/5)\n")
    else:
        indices_to_generate = [1, 2, 3, 4, 5]
        print(f"   Mode    : Generate all 5 hints\n")

    def dump_partial(exc: BaseException | None = None):
        filled = [h for h in hints if h is not None]
        if not filled:
            print("\n⚠  No hints generated yet — nothing to save.")
            return
        if existing is not None:
            partial_obj = dict(existing)
            partial_obj["hints"] = hints
        else:
            partial_obj = build_athlete_object(args.name, args.date, filled)
            partial_obj["hints"] = hints  # preserve None slots so the caller can see gaps
        print(f"\n⚠  Partial result ({len(filled)}/5 hints filled):")
        print(json.dumps(partial_obj, indent=2, ensure_ascii=False))
        partial_path = out_path.with_suffix(".partial.json")
        try:
            save(partial_path, partial_obj)
        except Exception as save_err:
            print(f"  Could not save partial file: {save_err}")

    try:
        # ------------------------------------------------------------------
        # Research phase
        # ------------------------------------------------------------------
        research = ""
        if not args.no_research:
            research = research_athlete(client, args.name)
            print(f"  ✓ Research complete ({len(research)} chars)\n")
        else:
            print("  ⚡ Skipping research (--no-research)\n")

        # ------------------------------------------------------------------
        # Generation phase
        # ------------------------------------------------------------------
        new_hints = generate_hints(client, args.name, indices_to_generate, research)

        # Slot new hints into the right positions
        for i, hint in zip(indices_to_generate, new_hints):
            hints[i - 1] = hint  # convert back to 0-indexed

        # Verify all slots filled
        missing = [i for i, h in enumerate(hints) if h is None]
        if missing:
            print(f"\nError: Slots {missing} are still empty. Something went wrong.")
            dump_partial()
            sys.exit(1)

        # ------------------------------------------------------------------
        # Build and save output
        # ------------------------------------------------------------------
        if existing is not None:
            athlete_obj = dict(existing)
            athlete_obj["hints"] = hints
        else:
            athlete_obj = build_athlete_object(args.name, args.date, hints)

        print(f"\n📋 Generated object:")
        print(json.dumps(athlete_obj, indent=2, ensure_ascii=False))

        print(f"\n💾 Saving...")
        save(out_path, athlete_obj)
        print(f"\n✅ Done!")

    except Exception as exc:
        print(f"\n❌ Failed: {exc}")
        dump_partial(exc)
        sys.exit(1)


if __name__ == "__main__":
    main()
