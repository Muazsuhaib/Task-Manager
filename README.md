# Task Manager API

A personal task manager REST API built with Java 17 and Spring Boot, featuring AI-powered task creation via Claude.

## Requirements

- Java 17
- Maven (or use the included `./mvnw` wrapper)
- An Anthropic API key (only required for the `/tasks/suggest` endpoint)

## Setup and Running

### 1. Clone the repository

```bash
git clone <your-repo-url>
cd task-manager
```

### 2. Set your Anthropic API key (optional)

```bash
export ANTHROPIC_API_KEY=your_key_here
```

### 3. Start the server

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

## Using the UI

Open `http://localhost:8080` in your browser to create, edit, delete tasks and use the AI Suggest feature.

## Running the Tests

```bash
./mvnw test
```

All tests run without an API key — the Anthropic client is mocked.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/tasks` | Create a new task |
| `GET` | `/tasks` | List all tasks |
| `GET` | `/tasks/{id}` | Get a single task |
| `PUT` | `/tasks/{id}` | Update a task |
| `DELETE` | `/tasks/{id}` | Delete a task |
| `POST` | `/tasks/suggest` | AI-powered task suggestion |

## AI-Powered Endpoint: POST /tasks/suggest

Accepts a plain-language description and returns a structured task object parsed by Claude.

### Example request

```bash
curl -X POST http://localhost:8080/tasks/suggest \
  -H "Content-Type: application/json" \
  -d '{"description": "remind me to submit the quarterly report before Friday"}'
```

### Example response

```json
{
  "title": "Submit quarterly report",
  "description": "Compile and submit the quarterly report before the Friday deadline",
  "dueDate": "2025-05-23",
  "priority": "HIGH",
  "status": "TODO"
}
```

Claude infers title, description, dueDate, priority, and status from plain English. If ANTHROPIC_API_KEY is not set, this endpoint returns 500. All other endpoints are unaffected.

## Database Inspector

H2 console available at `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:taskdb`
- Username: `sa`
- Password: _(leave blank)_
