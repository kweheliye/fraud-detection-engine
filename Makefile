.DEFAULT_GOAL := help
.PHONY: help run stop test build clean

help: ## Show the commands
	@printf "\n  make run     Start everything (Kafka, Postgres, app)\n"
	@printf "  make stop    Stop Kafka and Postgres\n"
	@printf "  make test    Run the tests\n"
	@printf "  make build   Build the jar\n"
	@printf "  make clean   Delete build output\n\n"

run: ## Start Kafka + Postgres, then the app (Ctrl+C stops the app)
	@docker info >/dev/null 2>&1 || (echo "Docker is not running. Start Docker Desktop and try again." && exit 1)
	docker compose up -d --wait kafka postgres
	./mvnw spring-boot:run

stop: ## Stop Kafka and Postgres
	docker compose --profile ui down

test: ## Run the tests
	./mvnw test

build: ## Build the jar into target/
	./mvnw -DskipTests package

clean: ## Delete build output
	./mvnw clean
