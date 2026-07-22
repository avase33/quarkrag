# quarkrag architecture

A RAG engine that answers questions over private documents. The bottleneck it
attacks: **RAG backends are usually heavy** — a JVM holding a model runtime,
slow to start, expensive to keep warm, impossible to scale to zero.

```
        browser (Next.js)
              │  POST /api/ingest   ·   POST /api/query
              ▼
┌──────────────────────────────────────────────────────┐
│ Quarkus engine (Java 21, GraalVM-native capable)     │
│                                                       │
│  ingest:  text ─▶ SemanticChunker ─▶ HashingEmbedder │
│                                          │            │
│                                          ▼            │
│                                    VectorStore        │
│                              memory | pgvector        │
│  query:   question ─▶ embed ─▶ search ─▶ LlmClient    │
│                                    extractive | openai│
└──────────────────────────────────────────────────────┘
```

## Why Quarkus and native compilation

Quarkus resolves dependency injection, configuration, and REST routing at
**build time** rather than at startup. That removes most of the classpath
scanning and reflection a traditional JVM framework does on boot, and it is
also what makes ahead-of-time compilation with GraalVM feasible.

The payoff is a binary that starts in milliseconds with a fraction of the
memory. Concretely, that changes what you can deploy: a service that boots in
5 ms can scale to zero and cold-start inside a request, so an internal RAG tool
used a few times an hour costs nothing while idle.

**The trade-off worth knowing:** native compilation is closed-world. Anything
reflective — JDBC drivers, dynamic proxies, JSON binding of unknown types —
must be registered at build time, and the build itself takes minutes and
several GB of RAM. That is precisely why this project uses the plain Postgres
driver with an explicit `--initialize-at-run-time` flag rather than a heavier
datasource layer, and why LangChain4j is not a dependency.

## Semantic chunking

Fixed-size chunking is the default in most RAG tutorials and it is the main
cause of poor retrieval. Splitting every 500 tokens routinely cuts a paragraph
in half, and a chunk that spans two subjects embeds to the *average* of both —
so it matches neither query well.

Instead, cohesion between adjacent sentences is measured as the Otsuka-Ochiai
coefficient over their content-term sets:

    c[i] = |terms(sᵢ) ∩ terms(sᵢ₊₁)| / sqrt(|terms(sᵢ)| · |terms(sᵢ₊₁)|)

A boundary goes where `c[i]` is a **local minimum and below the document mean**
— the signal behind TextTiling. A hard `max-words` cap bounds chunk size so one
long cohesive section cannot become a single enormous chunk.
`chunk/SemanticChunker.java`

## Embeddings without a model

The embedder is the hashing trick: hash each term into one of `dim` buckets,
use another bit of the same hash for a `±` sign so collisions cancel rather
than compound, weight by `1 + log(tf)`, then L2-normalise so cosine similarity
is a plain dot product.

It uses `MessageDigest` rather than `String.hashCode()` deliberately — Java's
string hash is stable in practice but not contractually, and a vector persisted
to Postgres must still match a query embedded on another machine months later.

This is not competitive with a trained sentence encoder on semantic paraphrase;
it matches on shared vocabulary. The point is that it needs no weights, no GPU,
and no network, which is what keeps the engine native-compilable and instant to
start. `HashingEmbedder.embed()` is one method — swapping in a real embedding
model means implementing it. `embed/HashingEmbedder.java`

## Retrieval

`VectorStore` has two implementations:

- **In-memory** — exact brute-force cosine over a `ConcurrentHashMap`. Correct
  and instant at small corpus sizes, where an ANN index would only add error.
- **pgvector** — ranking happens inside Postgres via the cosine operator `<=>`
  against an HNSW index, so only the top-k rows cross the wire. This is the one
  that scales past memory.

Both return the same `Hit` record, so nothing downstream knows which is active.

## Synthesis and grounding

The offline synthesizer ranks sentences from retrieved chunks by term overlap
with the question and joins the best three. It is **grounded by construction**:
every sentence in an answer exists verbatim in an indexed document, so
faithfulness is 1.0 and hallucination is structurally impossible. When nothing
overlaps, it says so rather than inventing an answer — a behaviour the test
suite asserts.

What it cannot do is paraphrase, aggregate across chunks, or reason. That is
the honest line between it and `quarkrag.llm=openai`, where the prompt pins the
model to the retrieved context and instructs it to admit absence. The OpenAI
client falls back to extractive synthesis on any error, so an unreachable model
degrades the answer instead of failing the request.

## Offline-first

Default configuration: in-memory store, extractive synthesis, embeddings
computed in-process. `mvn quarkus:dev` gives a working RAG engine with nothing
else installed. `docker compose --profile infra up` adds Postgres with pgvector,
and `quarkrag.store=pgvector` moves retrieval into the database — same code,
same API, same tests.
