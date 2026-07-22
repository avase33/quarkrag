package com.quarkrag.store;

import com.quarkrag.embed.HashingEmbedder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Postgres + pgvector store, activated with {@code quarkrag.store=pgvector}.
 *
 * <p>Retrieval uses pgvector's cosine distance operator {@code <=>} with an
 * HNSW index, so ranking happens inside the database and only the top-k rows
 * cross the wire. Score is reported as {@code 1 - distance} to match the
 * in-memory store's cosine similarity.
 *
 * <p>Uses the plain JDBC driver rather than a Quarkus datasource extension so
 * that the default in-memory profile needs no database configuration at all.
 */
public class PgVectorStore implements VectorStore {

    private final String url;
    private final String user;
    private final String password;
    private final int dim;

    public PgVectorStore(String url, String user, String password, int dim) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.dim = dim;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void initSchema() {
        try (Connection c = connect(); var st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS chunks (
                      id        text PRIMARY KEY,
                      doc_id    text NOT NULL,
                      content   text NOT NULL,
                      embedding vector(%d)
                    )""".formatted(dim));
            st.execute("CREATE INDEX IF NOT EXISTS chunks_doc_id_idx ON chunks (doc_id)");
            st.execute("""
                    CREATE INDEX IF NOT EXISTS chunks_embedding_idx
                      ON chunks USING hnsw (embedding vector_cosine_ops)""");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to initialise pgvector schema", e);
        }
    }

    @Override
    public void upsertDocument(String docId, List<Record> chunks) {
        String sql = """
                INSERT INTO chunks (id, doc_id, content, embedding)
                VALUES (?, ?, ?, ?::vector)
                ON CONFLICT (id) DO UPDATE
                  SET doc_id = EXCLUDED.doc_id,
                      content = EXCLUDED.content,
                      embedding = EXCLUDED.embedding""";
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement delete = c.prepareStatement("DELETE FROM chunks WHERE doc_id = ?")) {
                delete.setString(1, docId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = c.prepareStatement(sql)) {
                for (Record chunk : chunks) {
                    insert.setString(1, chunk.id());
                    insert.setString(2, chunk.docId());
                    insert.setString(3, chunk.text());
                    insert.setString(4, HashingEmbedder.toPgVector(chunk.embedding()));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("upsert failed for " + docId, e);
        }
    }

    @Override
    public int deleteDocument(String docId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM chunks WHERE doc_id = ?")) {
            ps.setString(1, docId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("delete failed for " + docId, e);
        }
    }

    @Override
    public List<Hit> search(float[] query, int k) {
        // <=> is cosine distance; 1 - distance gives similarity
        String sql = """
                SELECT id, doc_id, content, 1 - (embedding <=> ?::vector) AS score
                FROM chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?""";
        List<Hit> hits = new ArrayList<>();
        String literal = HashingEmbedder.toPgVector(query);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, literal);
            ps.setString(2, literal);
            ps.setInt(3, k);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new Hit(
                            rs.getString("id"),
                            rs.getString("doc_id"),
                            rs.getString("content"),
                            Math.round(rs.getDouble("score") * 10_000.0) / 10_000.0));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("search failed", e);
        }
        return hits;
    }

    @Override
    public long chunkCount() {
        return count("SELECT count(*) FROM chunks");
    }

    @Override
    public long documentCount() {
        return count("SELECT count(DISTINCT doc_id) FROM chunks");
    }

    private long count(String sql) {
        try (Connection c = connect(); var st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            return 0L;
        }
    }

    @Override
    public String name() {
        return "pgvector";
    }
}
