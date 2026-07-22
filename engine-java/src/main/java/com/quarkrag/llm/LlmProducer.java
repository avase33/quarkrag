package com.quarkrag.llm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/** Selects the synthesizer from {@code quarkrag.llm}; defaults to extractive. */
@ApplicationScoped
public class LlmProducer {

    private static final Logger LOG = Logger.getLogger(LlmProducer.class);

    @ConfigProperty(name = "quarkrag.llm", defaultValue = "extractive")
    String kind;

    @ConfigProperty(name = "quarkrag.openai.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "quarkrag.openai.model", defaultValue = "gpt-4o-mini")
    String model;

    @ConfigProperty(name = "quarkrag.openai.base-url", defaultValue = "https://api.openai.com/v1")
    String baseUrl;

    @Produces
    @ApplicationScoped
    public LlmClient llmClient() {
        if ("openai".equalsIgnoreCase(kind) && apiKey.isPresent() && !apiKey.get().isBlank()) {
            LOG.infof("synthesizer: openai (%s)", model);
            return new OpenAiLlm(apiKey.get(), model, baseUrl);
        }
        if ("openai".equalsIgnoreCase(kind)) {
            LOG.warn("quarkrag.llm=openai but no API key configured; using extractive synthesis");
        } else {
            LOG.info("synthesizer: extractive (offline)");
        }
        return new ExtractiveLlm();
    }
}
