# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/); versioning: [SemVer](https://semver.org/).

## [0.1.0] - 2026-07-18

Initial release — a cloud-native RAG engine on Quarkus with no ML dependencies.

### Added
- **Quarkus 3.8 / Java 21 engine** with `POST /api/ingest`, `POST /api/query`,
  `DELETE /api/documents/{docId}`, and `GET /api/stats`; native-image build via
  GraalVM/Mandrel.
- **From-scratch semantic chunker**: topic-shift boundaries at local minima of
  adjacent-sentence lexical cohesion (Otsuka-Ochiai over term sets), with a hard
  word cap and markdown stripping.
- **From-scratch hashing embedder**: feature hashing with signed buckets,
  sublinear term frequency, L2 normalisation, and a stable `MessageDigest`-based
  hash so vectors survive restarts and machine moves. Includes pgvector literal
  encoding/decoding.
- **Two vector stores behind one interface**: exact in-memory cosine search
  (default) and Postgres + pgvector using the `<=>` cosine operator over an HNSW
  index, selected by `quarkrag.store`.
- **Two synthesizers behind one interface**: a grounded extractive synthesizer
  that can only emit sentences present in retrieved chunks (default), and an
  OpenAI client that pins the model to context and falls back to extractive on
  any error.
- **Next.js chat UI**: document ingest, question box, and an expandable
  retrieved-context inspector showing chunk ids and similarity scores.
- 18 JUnit tests, JVM and native Dockerfiles, docker-compose with an optional
  pgvector profile, GitHub Actions CI, Makefile, MIT license.
