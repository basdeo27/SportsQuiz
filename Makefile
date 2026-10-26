.PHONY: db-up db-down db-logs dev-backend

db-up:
	docker run -d --name sports-quiz-dynamodb -p 8000:8000 amazon/dynamodb-local

db-down:
	docker rm -f sports-quiz-dynamodb

db-logs:
	docker logs -f sports-quiz-dynamodb

dev-backend: db-up
	./gradlew bootRun --args='--spring.profiles.active=local'

# Values travel via environment variables, never interpolated into shell commands.
export URL START END PLAYER INPUT OUT FORCE INFO DRY_RUN SOURCE_NOTE
AUDIO_PYTHON ?= $(if $(wildcard .venv-audio/bin/python),.venv-audio/bin/python,python3)

.PHONY: audio-clip audio-setup
audio-clip:
	@"$(AUDIO_PYTHON)" scripts/python/clip_audio.py

audio-setup:
	python3 -m venv .venv-audio
	.venv-audio/bin/python -m pip install --upgrade -r scripts/python/audio-requirements.txt
