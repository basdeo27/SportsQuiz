.PHONY: db-up db-down db-logs dev-backend

db-up:
	docker run -d --name sports-quiz-dynamodb -p 8000:8000 amazon/dynamodb-local

db-down:
	docker rm -f sports-quiz-dynamodb

db-logs:
	docker logs -f sports-quiz-dynamodb

dev-backend: db-up
	./gradlew bootRun
