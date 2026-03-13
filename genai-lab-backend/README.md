# GenAI Lab — Backend

Developer workspace for experimenting with Generative AI tools.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop (or Docker Engine + Docker Compose)
- An OpenAI API key

---

## Quick Start

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env and set your OPENAI_API_KEY and change passwords
```

### 2. Start full stack (all services + app in Docker)

```bash
docker-compose up -d
```

Wait about 60 seconds for all services to become healthy, then check:

```bash
docker-compose ps
```

All services should show `healthy` or `running`.

### 3. Access the services

| Service | URL | Credentials |
|---|---|---|
| Spring Boot App | http://localhost:8080 | — |
| Actuator Health | http://localhost:8080/actuator/health | — |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Prometheus | http://localhost:9090 | — |

---

## Development Mode (recommended)

Run infrastructure in Docker, app in your IDE for fast iteration.

```bash
# Start only infrastructure (postgres, minio, prometheus)
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Run the app from your IDE or command line
# Set environment variables from .env before running:
export $(cat .env | grep -v '#' | xargs)
cd genai-lab-app
mvn spring-boot:run
```

---

## Build

```bash
# Build all modules
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build and run locally
mvn clean package -DskipTests
java -jar genai-lab-app/target/genai-lab-app-*.jar
```

---

## Infrastructure

### Stop everything
```bash
docker-compose down
```

### Stop and DELETE all data (⚠️ irreversible)
```bash
docker-compose down -v
```

### View logs
```bash
docker-compose logs -f          # all services
docker-compose logs -f postgres # specific service
docker-compose logs -f app      # application logs
```

### Rebuild the app image after code changes
```bash
docker-compose build app
docker-compose up -d app
```

---

## Module Structure

```
genai-lab-backend/
├── genai-lab-app/        Spring Boot entry point, application.yml
├── genai-lab-api/        REST controllers, DTOs, request/response mapping
├── genai-lab-common/     Shared utilities, exceptions, base classes
├── genai-lab-security/   JWT auth, user management
├── genai-lab-ai/         AI provider abstraction + OpenAI implementation
├── genai-lab-chat/       Conversation and message domain
├── genai-lab-document/   Document upload, extraction, chunking
├── genai-lab-rag/        RAG pipeline — embedding, vector search, retrieval
├── genai-lab-storage/    File storage abstraction (Local + MinIO)
└── genai-lab-metrics/    Micrometer custom metrics
```
