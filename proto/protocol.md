# quarkrag wire protocol

One JSON contract between the Next.js chat UI and the Quarkus engine.

## 1. Ingest a document — `POST /api/ingest`

```json
{ "docId": "handbook.md", "text": "# Onboarding\n\nWelcome to the team..." }
```

→

```json
{ "docId": "handbook.md", "chunks": 7, "embeddingDim": 256, "tookMs": 12.4 }
```

Chunking is **semantic**: boundaries fall at topic shifts (local minima of
adjacent-sentence lexical cohesion), capped by `quarkrag.chunk.max-words`.

## 2. Ask a question — `POST /api/query`

```json
{ "question": "how do I request time off?", "k": 4 }
```

→

```json
{
  "answer": "Open the HR portal and submit a leave request with your dates. ...",
  "contexts": [
    { "id": "handbook.md#3", "docId": "handbook.md", "text": "...", "score": 0.71 }
  ],
  "model": "extractive",
  "tookMs": 3.8
}
```

`model` is `extractive` offline, or the configured LLM id when
`quarkrag.llm=openai`.

## 3. Delete a document — `DELETE /api/documents/{docId}`

→ `{ "docId": "handbook.md", "removed": 7 }`

## 4. Corpus stats — `GET /api/stats`

```json
{ "documents": 3, "chunks": 21, "embeddingDim": 256, "store": "memory", "llm": "extractive" }
```

## Configuration

| property | default | meaning |
| --- | --- | --- |
| `quarkrag.store` | `memory` | `memory` or `pgvector` |
| `quarkrag.embedding.dim` | `256` | hashing-embedder dimensionality |
| `quarkrag.chunk.max-words` | `120` | hard cap per chunk |
| `quarkrag.llm` | `extractive` | `extractive` or `openai` |
| `quarkrag.pg.url` | — | JDBC URL when `store=pgvector` |

## pgvector schema

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS chunks (
  id      text PRIMARY KEY,
  doc_id  text NOT NULL,
  content text NOT NULL,
  embedding vector(256)
);
CREATE INDEX IF NOT EXISTS chunks_embedding_idx
  ON chunks USING hnsw (embedding vector_cosine_ops);
```

Retrieval uses the cosine distance operator `<=>`; score is `1 - distance`.

## Ports

| service | port |
| --- | --- |
| Quarkus engine | 8080 |
| Next.js chat | 3000 |
| Postgres + pgvector *(optional)* | 5432 |
