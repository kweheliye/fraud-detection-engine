.DEFAULT_GOAL := help
.PHONY: help run stop engine engine-stop ml ml-stop ml-test ml-train test build clean

DOCKER_CHECK = @docker info >/dev/null 2>&1 || (echo "Docker is not running. Start Docker Desktop and try again." && exit 1)

help: ## Show the commands
	@printf "\n  EVERYTHING\n"
	@printf "    make run          Start Kafka, Postgres, the ML sidecar and the app (ML rule on)\n"
	@printf "    make stop         Stop all of it\n"
	@printf "\n  SPRING BOOT ENGINE ONLY (no ML)\n"
	@printf "    make engine       Start Kafka, Postgres and the app\n"
	@printf "    make engine-stop  Stop Kafka and Postgres\n"
	@printf "\n  ML SIDECAR ONLY\n"
	@printf "    make ml           Start the sidecar on http://localhost:8000\n"
	@printf "    make ml-stop      Stop the sidecar\n"
	@printf "    make ml-test      Run the sidecar tests (needs Python 3.10+)\n"
	@printf "    make ml-train     Train a new model: make ml-train VERSION=v3\n"
	@printf "\n  BUILD\n"
	@printf "    make test         Run the Java tests\n"
	@printf "    make build        Build the jar into target/\n"
	@printf "    make clean        Delete build output\n\n"

# ---------------------------------------------------------------- everything

run: ## Start everything, end to end (Ctrl+C stops the app; `make stop` stops the rest)
	$(DOCKER_CHECK)
	docker compose --profile ml up -d --build --wait kafka postgres ml-sidecar
	./mvnw spring-boot:run -Dspring-boot.run.arguments=--fraud.rules.ml.enabled=true

stop: ## Stop everything, end to end
	docker compose --profile ui --profile ml down

# ------------------------------------------------- Spring Boot engine only

engine: ## Start Kafka, Postgres and the app, without ML
	$(DOCKER_CHECK)
	docker compose up -d --wait kafka postgres
	./mvnw spring-boot:run

engine-stop: ## Stop Kafka and Postgres
	docker compose down

# -------------------------------------------------------- ML sidecar only

ml: ## Build and start the ML sidecar on http://localhost:8000
	$(DOCKER_CHECK)
	docker compose --profile ml up -d --build --wait ml-sidecar

ml-stop: ## Stop the ML sidecar
	docker compose --profile ml rm -sf ml-sidecar

ml-test: ## Run the ML sidecar tests
	cd ml-sidecar && python3 -m venv .venv && .venv/bin/pip install -q -r requirements-dev.txt && .venv/bin/python -m pytest -q

ml-train: ## Train a new model version: make ml-train VERSION=v3
	cd ml-sidecar && python3 -m venv .venv && .venv/bin/pip install -q -r requirements-dev.txt && .venv/bin/python -m training.train --version $(or $(VERSION),v2)

# ------------------------------------------------------------------ build

test: ## Run the Java tests
	./mvnw test

build: ## Build the jar into target/
	./mvnw -DskipTests package

clean: ## Delete build output
	./mvnw clean