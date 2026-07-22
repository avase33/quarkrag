.PHONY: dev test build native up infra down build-web

# Quarkus live-coding mode on :8080 (no database needed)
dev:
	cd engine-java && mvn quarkus:dev

test:
	cd engine-java && mvn -B test

build:
	cd engine-java && mvn -B package

# Native binary via GraalVM. Takes minutes and needs ~4 GB RAM.
native:
	cd engine-java && mvn -B package -Dnative

build-web:
	cd chat-ts && npm install && npm run build

up:
	docker compose up --build

# Adds Postgres + pgvector
infra:
	docker compose --profile infra up --build

down:
	docker compose down
