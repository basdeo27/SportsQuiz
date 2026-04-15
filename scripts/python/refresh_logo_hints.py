#!/usr/bin/env python3
from __future__ import annotations

import argparse
import abc
import json
import os
import sys
import re
from pathlib import Path
from typing import Any


DIFFICULTIES = ("EASY", "MEDIUM", "HARD")
DEFAULT_TIMEOUT_MS = 300000
DEFAULT_BATCH_SIZE = 10
DEFAULT_PROVIDER = "claude"
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
        description="Refresh logo quiz hints for a league using a configurable AI provider."
    )
    parser.add_argument(
        "--input",
        default="src/main/resources/data/logos/mlb.json",
        help="Path to the league logo JSON file.",
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
        help=f"Number of teams to send per request. Default: {DEFAULT_BATCH_SIZE}",
    )
    return parser.parse_args()


def load_teams(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as infile:
        teams = json.load(infile)
    if not isinstance(teams, list) or not teams:
        raise ValueError(f"{path} does not contain a non-empty JSON array.")
    return teams


def build_prompt(league: str, teams: list[dict[str, Any]]) -> str:
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


def normalize_hint_list(team_id: str, hints: dict[str, Any]) -> dict[str, list[str]]:
    normalized: dict[str, list[str]] = {}
    for difficulty in DIFFICULTIES:
        values = hints.get(difficulty)
        if not isinstance(values, list):
            raise ValueError(f"{team_id}: {difficulty} must be a list.")
        cleaned = [str(value).strip() for value in values if str(value).strip()]
        if not cleaned:
            raise ValueError(f"{team_id}: {difficulty} must contain at least one hint.")
        normalized[difficulty] = cleaned
    return normalized


def validate_payload(payload: dict[str, Any], teams: list[dict[str, Any]]) -> dict[str, dict[str, list[str]]]:
    generated_teams = payload.get("teams")
    if not isinstance(generated_teams, list):
        raise ValueError("Response JSON must include a `teams` array.")

    by_id: dict[str, dict[str, list[str]]] = {}
    for item in generated_teams:
        if not isinstance(item, dict):
            raise ValueError("Each generated team entry must be an object.")
        team_id = item.get("id")
        hints = item.get("hints")
        if not isinstance(team_id, str) or not team_id:
            raise ValueError("Each generated team entry must include a string `id`.")
        if not isinstance(hints, dict):
            raise ValueError(f"{team_id}: missing `hints` object.")
        by_id[team_id] = normalize_hint_list(team_id, hints)

    input_ids = {team["id"] for team in teams}
    generated_ids = set(by_id)
    missing_ids = sorted(input_ids - generated_ids)
    extra_ids = sorted(generated_ids - input_ids)
    if missing_ids or extra_ids:
        raise ValueError(
            f"Generated ids did not match input ids. Missing={missing_ids}, extra={extra_ids}"
        )

    for team in teams:
        full_name = team["name"].strip().lower()
        for difficulty, hints in by_id[team["id"]].items():
            for hint in hints:
                if full_name in hint.lower():
                    raise ValueError(
                        f"{team['id']}: {difficulty} hint reveals the full team name: {hint}"
                    )
    return by_id


def merge_hints(
    teams: list[dict[str, Any]], generated_hints: dict[str, dict[str, list[str]]]
) -> list[dict[str, Any]]:
    updated_teams: list[dict[str, Any]] = []
    for team in teams:
        updated_team = {
            "id": team["id"],
            "name": team["name"],
            "logoUrl": team["logoUrl"],
            "answers": team.get("answers", []),
            "hints": generated_hints[team["id"]],
        }
        updated_teams.append(updated_team)
    return updated_teams


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

    teams = load_teams(input_path)
    generated_hints: dict[str, dict[str, list[str]]] = {}
    team_batches = chunked(teams, args.batch_size)

    print(f"Using AI provider={provider}, model={model}")

    for batch_index, team_batch in enumerate(team_batches, start=1):
        print(
            f"Requesting hints for batch {batch_index} of "
            f"{len(team_batches)} ({len(team_batch)} teams)..."
        )
        prompt = build_prompt(args.league, team_batch)
        payload = client.request_hints(
            model=model,
            prompt=prompt,
            timeout_ms=args.timeout_ms,
        )
        generated_hints.update(validate_payload(payload, team_batch))

    updated_teams = merge_hints(teams, generated_hints)

    with output_path.open("w", encoding="utf-8") as outfile:
        json.dump(updated_teams, outfile, indent=2)
        outfile.write("\n")

    print(f"Wrote refreshed hints for {len(updated_teams)} teams to {output_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
