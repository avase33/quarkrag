package com.quarkrag.store;

import com.quarkrag.embed.HashingEmbedder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Chooses the vector store at startup from {@code quarkrag.store}.
 *
 * <p>Defaults to the in-memory store so a fresh clone runs with no database.
 */
@ApplicationScoped
public class VectorStoreProducer {

    private static final Logger LOG = Logger.getLogger(VectorStoreProducer.class);

    @ConfigProperty(name = "quarkrag.store", defaultValue = "memory")
    String storeKind;

    @ConfigProperty(name = "quarkrag.pg.url")
    Optional<String> pgUrl;

    @ConfigProperty(name = "quarkrag.pg.user", defaultValue = "postgres")
    String pgUser;

    @ConfigProperty(name = "quarkrag.pg.password", defaultValue = "postgres")
    String pgPassword;

    @Produces
    @ApplicationScoped
    public VectorStore vectorStore(HashingEmbedder embedder) {
        if ("pgvector".equalsIgnoreCase(storeKind)) {
            String url = pgUrl.orElseThrow(() -> new IllegalStateException(
                    "quarkrag.store=pgvector requires quarkrag.pg.url"));
            LOG.infof("vector store: pgvector at %s (dim=%d)", url, embedder.dimensions());
            return new PgVectorStore(url, pgUser, pgPassword, embedder.dimensions());
        }
        LOG.infof("vector store: in-memory (dim=%d)", embedder.dimensions());
        return new InMemoryVectorStore();
    }
}
