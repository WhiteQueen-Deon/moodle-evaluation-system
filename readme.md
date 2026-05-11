# Moodle Evaluation System

A middleware service built with Spring Boot that connects Moodle LMS to an automated code evaluation pipeline. Instead of grading assignments manually inside Moodle, this service acts as a bridge: it fetches student submissions, hands them off to an evaluator, and writes the results back to Moodle automatically.

## Architecture

The system consists of two services:

**MoodleFacade** — the core service. It communicates with Moodle's REST API to retrieve ungraded submissions, stores them in a PostgreSQL database, exposes endpoints for evaluators to claim and return results, and pushes final grades and feedback back into Moodle.

**EvaluatorMock** — a lightweight mock service used for local testing. It polls the facade for pending submissions and posts back a fixed score, simulating what a real evaluator would do.

```
Moodle LMS  ──►  MoodleFacade  ──►  Evaluator
                      │
                 PostgreSQL
```

## Submission Lifecycle

```
PENDING  ──►  PROCESSING  ──►  COMPLETED
   ▲               │
   └───────────────┘
   (timeout reset by scheduler)
```

A scheduled monitor periodically checks for submissions stuck in `PROCESSING` and resets them to `PENDING` so they can be retried.

## Tech Stack

- **Java 25 / Spring Boot 3.5**
- **Spring Data JPA + PostgreSQL** — submission state persistence
- **WebFlux (WebClient)** — non-blocking HTTP calls to Moodle API
- **Spring Scheduler** — timeout monitor, periodic sync
- **Docker Compose** — full stack orchestration (Moodle, MariaDB, facade, evaluator)

## Getting Started

### Prerequisites
- Docker and Docker Compose

### Steps

```bash
# 1. Start everything
docker compose up

# 2. Open Moodle at https://localhost:8080
#    Default credentials: admin / admin123

# 3. Generate a Web Services token:
#    Site Administration → Server → Web Services → Overview

# 4. Paste the token into docker-compose.yml:
#    moodle-facade → environment → MOODLE_TOKEN

# 5. Set your assignment ID:
#    evaluator-mock → environment → moodle_assignment_id

# 6. Apply the changes
docker compose restart moodle-facade evaluator-mock
```

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/pending` | Claim the next pending submission |
| `POST` | `/{submissionId}/result` | Submit evaluation result (score + feedback) |
| `GET` | `/moodle/pull?assignmentId={id}` | Sync ungraded submissions from Moodle |
| `GET` | `/moodle/submissions/{id}/file` | Download a submitted file by index |

## Project Structure

```
MoodleFacade/
├── Controller/        # REST endpoints
├── service/
│   ├── MoodlePullService.java   # Fetches submissions from Moodle
│   ├── MoodlePushService.java   # Writes grades back to Moodle
│   └── SubmissionService.java   # Submission state management
├── domain/            # JPA entities
├── repository/        # Spring Data repositories
└── Scheduler/         # Timeout monitor

EvaluatorMock/
└── service/
    └── EvaluatorWorker.java     # Polls facade and posts mock results
```