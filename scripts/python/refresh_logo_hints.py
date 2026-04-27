#!/usr/bin/env python3
from __future__ import annotations

import argparse
import abc
import json
import os
import sys
import re
import time
from pathlib import Path
from typing import Any


DIFFICULTIES = ("EASY", "MEDIUM", "HARD")
DEFAULT_TIMEOUT_MS = 300000
DEFAULT_BATCH_SIZE = 10
DEFAULT_PROVIDER = "claude"
MAX_RETRIES = 3
DEFAULT_MODELS = {
    "claude": "claude-3-7-sonnet-latest",
    "mistral": "mistral-large-latest",
}


class HintGenerationClient(abc.ABC):
    @abc.abstractmethod
    def request_hints(self, *, model: str, prompt: str, timeout_ms: int) -> dict[str, Any]:
        raise NotImplementedError


class MistralHintGenerationClient(HintGenerationClient):
    def __init__(self, api_key: str) -> None:
        try:
            from mistralai.client.sdk import Mistral
        except ImportError as exc:  # pragma: no cover
            raise SystemExit(
                "The `mistralai` package is required for AI_PROVIDER=mistral. "
                "Install it with `pip install mistralai`."
            ) from exc
        self._client_class = Mistral
        self._api_key = api_key

    def request_hints(self, *, model: str, prompt: str, timeout_ms: int) -> dict[str, Any]:
        client = self._client_class(api_key=self._api_key, timeout_ms=timeout_ms)
        response = client.chat.complete(
            model=model,
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "user",
                    "content": prompt,
                }
            ],
        )
        content = response.choices[0].message.content
        if isinstance(content, list):
            content = "".join(
                part.get("text", "") for part in content if isinstance(part, dict)
            )
        if not isinstance(content, str) or not content.strip():
            raise ValueError("Mistral returned an empty response.")
        return json.loads(content)


class ClaudeHintGenerationClient(HintGenerationClient):
    def __init__(self, api_key: str) -> None:
        try:
            from anthropic import Anthropic
        except ImportError as exc:  # pragma: no cover
            raise SystemExit(
                "The `anthropic` package is required for AI_PROVIDER=claude. "
                "Install it with `pip install anthropic`."
            ) from exc
        self._client_class = Anthropic
        self._api_key = api_key

    def request_hints(self, *, model: str, prompt: str, timeout_ms: int) -> dict[str, Any]:
        client = self._client_class(api_key=self._api_key, timeout=timeout_ms / 1000)
        response = client.messages.create(
            model=model,
            max_tokens=4000,
            messages=[
                {
                    "role": "user",
                    "content": (
                        f"{prompt}\n\n"
                        "Output only valid JSON. Do not wrap the JSON in markdown fences."
                    ),
                }
            ],
        )
        text_parts = [
            block.text for block in response.content
            if getattr(block, "type", None) == "text"
        ]
        content = "\n".join(text_parts).strip()
        if not content:
            raise ValueError("Claude returned an empty response.")
        return parse_json_content(content)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Refresh quiz hints for a league using a configurable AI provider."
    )
    parser.add_argument(
        "--type",
        choices=["logo", "face"],
        default="logo",
        help="Quiz type: 'logo' for team logo hints, 'face' for player face hints. Default: logo",
    )
    parser.add_argument(
        "--input",
        default="src/main/resources/data/logos/mlb.json",
        help="Path to the league JSON file.",
    )
    parser.add_argument(
        "--output",
        help="Optional output path. Defaults to overwriting the input file.",
    )
    parser.add_argument(
        "--model",
        default=None,
        help=(
            "Model to use. Defaults by provider: "
            + ", ".join(
                f"{provider}={model}" for provider, model in DEFAULT_MODELS.items()
            )
        ),
    )
    parser.add_argument(
        "--league",
        default="MLB",
        help="League label to mention in the prompt. Default: MLB",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=DEFAULT_TIMEOUT_MS,
        help=f"HTTP timeout in milliseconds. Default: {DEFAULT_TIMEOUT_MS}",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_BATCH_SIZE,
        help=f"Number of entries to send per request. Default: {DEFAULT_BATCH_SIZE}",
    )
    return parser.parse_args()


def load_entries(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as infile:
        entries = json.load(infile)
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{path} does not contain a non-empty JSON array.")
    return entries


def build_logo_prompt(league: str, teams: list[dict[str, Any]]) -> str:
    team_names = [team["name"] for team in teams]
    compact_teams = [
        {
            "id": team["id"],
            "name": team["name"],
            "answers": team.get("answers", []),
        }
        for team in teams
    ]
    return f"""
You are writing hints for a sports logo quiz.

Context:
- League: {league}
- We show users one team logo at a time.
- The user is trying to guess the team name from the logo.
- Here is the full list of teams in this league so hints can be distinctive:
{json.dumps(team_names, indent=2)}

Task:
- For every team below, write exactly 3 hints total: one EASY hint, one MEDIUM hint, and one HARD hint.
- EASY should be quite helpful.
- MEDIUM should be moderately helpful.
- HARD should be intentionally subtle or obscure and not very helpful.
- Do not mention that this is a logo.
- Do not reveal the exact full team name.
- Do not rely on letters, abbreviations, or words visibly printed on the logo.
- Prefer facts that help distinguish the team from others in the same league.
- If the nickname is an animal, mascot, or natural object, a HARD hint can be an obscure fact about that thing.
- Keep each hint to one sentence.
- Return valid JSON only.

Return this exact shape:
{{
  "teams": [
    {{
      "id": "team-id",
      "hints": {{
        "EASY": ["..."],
        "MEDIUM": ["..."],
        "HARD": ["..."]
      }}
    }}
  ]
}}

Teams to annotate:
{json.dumps(compact_teams, indent=2)}
""".strip()


def build_face_prompt(league: str, players: list[dict[str, Any]]) -> str:
    player_names = [player["name"] for player in players]
    compact_players = [
        {
            "id": player["id"],
            "name": player["name"],
            "team": player.get("team", ""),
            "answers": player.get("answers", []),
        }
        for player in players
    ]
    return f"""
You are writing hints for a sports player face quiz.

Context:
- League: {league}
- We show users one player's headshot photo at a time.
- The user is trying to guess the player's name from their photo.
- Here is the full list of players in this batch so hints can be distinctive:
{json.dumps(player_names, indent=2)}

Task:
- For every player below, write exactly 3 hints total: one EASY hint, one MEDIUM hint, and one HARD hint.
- EASY should be quite helpful (e.g. the team they currently play for, a major award or championship).
- MEDIUM should be moderately helpful (e.g. a notable career stat or milestone).
- HARD should be intentionally subtle or obscure (e.g. a lesser-known fact about their career or background).
- Do not reveal the player's first name, last name, or full name.
- Do not mention the player's jersey number.
- Prefer facts that help distinguish this player from others in the same league.
- Keep each hint to one sentence.
- Return valid JSON only.

Return this exact shape:
{{
  "teams": [
    {{
      "id": "player-id",
      "hints": {{
        "EASY": ["..."],
        "MEDIUM": ["..."],
        "HARD": ["..."]
      }}
    }}
  ]
}}

Players to annotate:
{json.dumps(compact_players, indent=2)}
""".strip()


def parse_json_content(content: str) -> dict[str, Any]:
    stripped = content.strip()
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        fenced_match = re.search(r"```(?:json)?\s*(\{.*\})\s*```", stripped, re.DOTALL)
        if fenced_match:
            return json.loads(fenced_match.group(1))
        json_match = re.search(r"(\{.*\})", stripped, re.DOTALL)
        if json_match:
            return json.loads(json_match.group(1))
        raise


def get_provider_name() -> str:
    provider = os.environ.get("AI_PROVIDER", DEFAULT_PROVIDER).strip().lower()
    if provider not in DEFAULT_MODELS:
        supported = ", ".join(sorted(DEFAULT_MODELS))
        raise SystemExit(
            f"Unsupported AI_PROVIDER={provider!r}. Supported providers: {supported}."
        )
    return provider


def build_hint_generation_client(provider: str) -> HintGenerationClient:
    if provider == "claude":
        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise SystemExit(
                "Set ANTHROPIC_API_KEY before running with AI_PROVIDER=claude."
            )
        return ClaudeHintGenerationClient(api_key)

    if provider == "mistral":
        api_key = os.environ.get("MISTRAL_API_KEY")
        if not api_key:
            raise SystemExit(
                "Set MISTRAL_API_KEY before running with AI_PROVIDER=mistral."
            )
        return MistralHintGenerationClient(api_key)

    raise SystemExit(f"Unsupported AI provider: {provider}")


def chunked(items: list[dict[str, Any]], size: int) -> list[list[dict[str, Any]]]:
    return [items[index:index + size] for index in range(0, len(items), size)]


RATE_LIMIT_SLEEP_SECONDS = 60


def request_hints_with_retry(
    client: HintGenerationClient, *, model: str, prompt: str, timeout_ms: int
) -> dict[str, Any]:
    while True:
        try:
            return client.request_hints(model=model, prompt=prompt, timeout_ms=timeout_ms)
        except Exception as exc:
            status = getattr(exc, "status_code", None) or getattr(exc, "status", None)
            message = str(exc).lower()
            is_rate_limit = status == 429 or "rate limit" in message or "429" in message
            if not is_rate_limit:
                raise
            print(
                f"Rate limited (429). Sleeping for {RATE_LIMIT_SLEEP_SECONDS} seconds before retrying...",
                flush=True,
            )
            time.sleep(RATE_LIMIT_SLEEP_SECONDS)


def normalize_hint_list(entry_id: str, hints: dict[str, Any]) -> dict[str, list[str]]:
    normalized: dict[str, list[str]] = {}
    for difficulty in DIFFICULTIES:
        values = hints.get(difficulty)
        if not isinstance(values, list):
            raise ValueError(f"{entry_id}: {difficulty} must be a list.")
        cleaned = [str(value).strip() for value in values if str(value).strip()]
        if not cleaned:
            raise ValueError(f"{entry_id}: {difficulty} must contain at least one hint.")
        normalized[difficulty] = cleaned
    return normalized


def validate_structure(
    payload: dict[str, Any],
    entries: list[dict[str, Any]],
) -> dict[str, dict[str, list[str]]]:
    """Validates response structure and returns hints keyed by entry ID. Raises on bad shape."""
    generated_entries = payload.get("teams")
    if not isinstance(generated_entries, list):
        raise ValueError("Response JSON must include a `teams` array.")

    by_id: dict[str, dict[str, list[str]]] = {}
    for item in generated_entries:
        if not isinstance(item, dict):
            raise ValueError("Each generated entry must be an object.")
        entry_id = item.get("id")
        hints = item.get("hints")
        if not isinstance(entry_id, str) or not entry_id:
            raise ValueError("Each generated entry must include a string `id`.")
        if not isinstance(hints, dict):
            raise ValueError(f"{entry_id}: missing `hints` object.")
        by_id[entry_id] = normalize_hint_list(entry_id, hints)

    input_ids = {entry["id"] for entry in entries}
    generated_ids = set(by_id)
    missing_ids = sorted(input_ids - generated_ids)
    extra_ids = sorted(generated_ids - input_ids)
    if missing_ids or extra_ids:
        raise ValueError(
            f"Generated ids did not match input ids. Missing={missing_ids}, extra={extra_ids}"
        )

    return by_id


def find_name_violations(
    by_id: dict[str, dict[str, list[str]]],
    entries: list[dict[str, Any]],
    quiz_type: str,
) -> list[dict[str, Any]]:
    """Returns the subset of entries whose generated hints reveal the entry name."""
    violations = []
    for entry in entries:
        full_name = entry["name"].strip().lower()
        names_to_check = [full_name]
        if quiz_type == "face":
            last_name = full_name.split()[-1]
            if last_name != full_name:
                names_to_check.append(last_name)
        hints_for_entry = by_id.get(entry["id"], {})
        if any(
            name in hint.lower()
            for hints in hints_for_entry.values()
            for hint in hints
            for name in names_to_check
        ):
            violations.append(entry)
    return violations


def merge_hints(
    entries: list[dict[str, Any]], generated_hints: dict[str, dict[str, list[str]]]
) -> list[dict[str, Any]]:
    return [{**entry, "hints": generated_hints[entry["id"]]} for entry in entries]


def main() -> int:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output) if args.output else input_path
    provider = get_provider_name()
    model = args.model or DEFAULT_MODELS[provider]
    client = build_hint_generation_client(provider)

    if args.timeout_ms <= 0:
        raise SystemExit("--timeout-ms must be greater than 0.")
    if args.batch_size <= 0:
        raise SystemExit("--batch-size must be greater than 0.")

    entries = load_entries(input_path)
    generated_hints: dict[str, dict[str, list[str]]] = {}
    batches = chunked(entries, args.batch_size)

    print(f"Using AI provider={provider}, model={model}, type={args.type}")

    for batch_index, batch in enumerate(batches, start=1):
        print(
            f"Requesting hints for batch {batch_index} of "
            f"{len(batches)} ({len(batch)} entries)..."
        )
        prompt = build_face_prompt(args.league, batch) if args.type == "face" else build_logo_prompt(args.league, batch)
        payload = request_hints_with_retry(client, model=model, prompt=prompt, timeout_ms=args.timeout_ms)
        by_id = validate_structure(payload, batch)

        # Retry any entries whose hints reveal the player/team name
        to_retry = find_name_violations(by_id, batch, args.type)
        for attempt in range(1, MAX_RETRIES + 1):
            if not to_retry:
                break
            names = [e["name"] for e in to_retry]
            print(f"  {len(to_retry)} entries revealed names in hints, retrying (attempt {attempt}/{MAX_RETRIES}): {names}")
            retry_prompt = build_face_prompt(args.league, to_retry) if args.type == "face" else build_logo_prompt(args.league, to_retry)
            retry_payload = request_hints_with_retry(client, model=model, prompt=retry_prompt, timeout_ms=args.timeout_ms)
            retry_by_id = validate_structure(retry_payload, to_retry)
            by_id.update(retry_by_id)
            to_retry = find_name_violations(retry_by_id, to_retry, args.type)

        if to_retry:
            names = [e["name"] for e in to_retry]
            raise SystemExit(f"Entries still revealed names after {MAX_RETRIES} retries: {names}")

        generated_hints.update(by_id)

    updated_entries = merge_hints(entries, generated_hints)

    with output_path.open("w", encoding="utf-8") as outfile:
        json.dump(updated_entries, outfile, indent=2)
        outfile.write("\n")

    print(f"Wrote refreshed hints for {len(updated_entries)} entries to {output_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
