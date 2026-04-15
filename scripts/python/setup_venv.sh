#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="${SCRIPT_DIR}/.venv"

python3 -m venv "${VENV_DIR}"
source "${VENV_DIR}/bin/activate"

python -m pip install --upgrade pip
pip install -r "${SCRIPT_DIR}/requirements.txt"

echo "Virtual env ready at ${VENV_DIR}"
echo "Activate it with: source ${VENV_DIR}/bin/activate"
echo "Default provider is Claude via AI_PROVIDER=claude."
echo "Set ANTHROPIC_API_KEY for Claude or MISTRAL_API_KEY for Mistral."
echo "Then run: python ${SCRIPT_DIR}/refresh_logo_hints.py --league MLB --input src/main/resources/data/logos/mlb.json"
