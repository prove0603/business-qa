# Business QA — Intelligent Q&A Assistant

RAG-powered intelligent Q&A assistant for business systems. Upload business documents, and the AI will answer questions based on them. Supports change detection to keep documents in sync with code.

## Features

- **Smart Q&A**: Ask questions in natural language, get AI-powered answers with source references
- **Document Management**: Upload and manage business documents (Markdown/Text), auto-vectorized for RAG
- **Module Organization**: Organize documents by business modules (COMMON auto-included, TASK user-selectable)
- **Change Detection**: Git-based code change detection with AI-generated document update suggestions
- **Streaming Chat**: Real-time SSE streaming responses with Markdown rendering

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, Spring AI 1.0.0 |
| AI | Spring AI ChatClient + DashScope (OpenAI-compatible) |
| Embedding | DashScope text-embedding-v3 (1024d) |
| Vector DB | PostgreSQL + pgvector (HNSW, cosine) |
| ORM | MyBatis-Plus 3.5.7 |
| Git | JGit 7.1.0 |
| Frontend | Vue 3, TypeScript, Element Plus, Vite 6 |

## Quick Start

### Prerequisites

- Java 21+
- Node.js 18+
- PostgreSQL 16+ with pgvector extension
- Maven 3.9+

### Database Setup

```bash
createdb business_qa
psql business_qa -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql business_qa -f business-qa-server/src/main/resources/db/schema.sql
```

### Backend

```bash
# Copy and configure credentials
cp business-qa-server/src/main/resources/application-dev.yml.example \
   business-qa-server/src/main/resources/application-dev.yml
# Edit application-dev.yml with your actual values

# Build and run
mvn clean package -DskipTests
java -jar business-qa-server/target/business-qa-server-1.0.0-SNAPSHOT.jar
```

Server runs at http://localhost:8091

### Frontend

```bash
cd business-qa-ui
npm install
npm run dev
```

Dev server runs at http://localhost:5174

### Production Build

```bash
cd business-qa-ui
npm run build  # outputs to business-qa-server/src/main/resources/static/
```

## Project Structure

```
business-qa/
├── pom.xml                          # Parent POM
├── business-qa-server/              # Spring Boot backend
│   ├── pom.xml
│   └── src/main/java/com/zhuangjie/qa/
│       ├── config/                  # Spring configurations
│       ├── controller/              # REST APIs
│       ├── chat/                    # Chat engine (Spring AI ChatClient)
│       ├── doc/                     # Document management + chunking
│       ├── change/                  # Git change detection + AI suggestions
│       ├── rag/                     # Embedding + VectorStore
│       └── db/                      # Entity, Mapper, DbService
├── business-qa-ui/                  # Vue 3 frontend
│   └── src/
│       ├── views/                   # Pages
│       ├── components/              # Reusable components
│       └── api/                     # API layer
└── .cursor/rules/                   # Cursor AI rules
```

## Configuration

Key environment variables / config:

| Variable | Description |
|----------|-------------|
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `AI_DASHSCOPE_API_KEY` | DashScope API key |
| `GIT_USERNAME` | Git credentials for repo cloning |
| `GIT_PASSWORD` | Git password/token |

## License

MIT
