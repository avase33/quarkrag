# quarkrag 🧠

**A cloud-native RAG engine on Quarkus.** Documents are split at *topic shifts*,
embedded with a from-scratch hashing embedder, stored in memory or in Postgres
with pgvector, and answered by a grounded synthesizer — with no model weights,
no API key, and no database required to run it.

```
Next.js chat ──▶ Quarkus engine ──▶ semantic chunker ──▶ hashing embedder
                       │                                        │
                       │                                        ▼
                       │                            vector store (memory | pgvector)
                       ▼                                        │
                 synthesizer  ◀── top-k contexts ───────────────┘
              (extractive | OpenAI)
```

| Layer | Technology | Owns |
| --- | --- | --- |
| **Engine** | Java 21 · Quarkus 3.8 | REST API, DI, native-image compilation |
| **Chunking** | Pure Java | Topic-shift segmentation by lexical cohesion |
| **Embedding** | Pure Java | Feature hashing, signed buckets, L2 norm |
| **Store** | ConcurrentHashMap *(default)* · Postgres + pgvector | Vector search |
| **Synthesis** | Extractive *(default)* · OpenAI | Grounded answers |
| **UI** | TypeScript · Next.js | Ingest + chat + retrieved-context inspector |

## Quickstart

```bash
cd engine-java && mvn quarkus:dev      # :8080, live reload, no database
cd chat-ts && npm install && npm run dev   # :3000
```

Index and ask, straight from curl:

```bash
curl -XPOST localhost:8080/api/ingest -H 'content-type: application/json' \
  -d '{"docId":"hr/pto.md","text":"To request time off, open the HR portal and submit a leave request with your dates."}'

curl -XPOST localhost:8080/api/query -H 'content-type: application/json' \
  -d '{"question":"how do I request time off?","k":4}'
```

```json
{
  "answer": "To request time off, open the HR portal and submit a leave request with your dates.",
  "contexts": [{ "id": "hr/pto.md#0", "score": 0.7x, "text": "..." }],
  "model": "extractive",
  "tookMs": 1.2
}
```

## Native compilation

```bash
mvn package -Dnative        # or: make native
```

The JVM build starts in a few hundred milliseconds and holds ~120 MB resident.
The native binary starts in **single-digit milliseconds** at ~30 MB — the
difference between a service you keep warm and one you can scale to zero.
Record your own numbers in this table:

| build | startup | RSS | image |
| --- | --- | --- | --- |
| JVM | ~0.Xs | ~XXX MB | XXX MB |
| native | ~0.0Xs | ~XX MB | XX MB |

## Switching to pgvector

```bash
docker compose --profile infra up      # Postgres 16 + pgvector
```

```properties
quarkrag.store=pgvector
quarkrag.pg.url=jdbc:postgresql://localhost:5432/quarkrag
```

Retrieval then runs inside Postgres using the cosine operator `<=>` against an
HNSW index; only the top-k rows cross the wire. Nothing else in the code
changes — `VectorStore` is an interface with two implementations.

## The interesting engineering

- **Semantic chunking** — boundaries fall at local minima of adjacent-sentence
  lexical cohesion (Otsuka-Ochiai over term sets), capped by `max-words`. This
  matters: a chunk spanning two subjects embeds to the average of both and
  matches neither well. `chunk/SemanticChunker.java`
- **Hashing embedder** — the hashing trick with signed buckets and sublinear TF,
  built on `MessageDigest` rather than `String.hashCode()` so vectors are stable
  across JVMs and machines. A vector written to Postgres today must still match
  a query embedded tomorrow. `embed/HashingEmbedder.java`
- **Grounded-by-construction synthesis** — the offline synthesizer only emits
  sentences that exist verbatim in retrieved chunks, so it cannot hallucinate.
  The trade-off is fluency, and it's documented rather than hidden.
  `llm/ExtractiveLlm.java`
- **No datasource extension** — the pgvector store uses the plain JDBC driver,
  so the default build starts with zero database configuration.

## Testing

```bash
make test        # or: cd engine-java && mvn test
```

18 tests covering chunk boundaries at topic shifts, cohesive text staying
together, the word cap, embedding determinism and normalisation, pgvector
literal round-trips, retrieval hitting the right document, grounded answers,
graceful "I don't know", and upsert-not-duplicate semantics.

## Layout

```
proto/protocol.md   JSON contract
engine-java/        Quarkus engine (chunker, embedder, stores, RAG, REST)
chat-ts/            Next.js ingest + chat UI with context inspector
docs/ARCHITECTURE.md
```

## Version note

Built against **Quarkus 3.8 (LTS) / Java 21**. LangChain4j is intentionally not
a dependency — the chunking, embedding, and retrieval are the parts worth
showing, and writing them directly keeps the engine native-image friendly.

## License

MIT © 2026 Akhil Vase
