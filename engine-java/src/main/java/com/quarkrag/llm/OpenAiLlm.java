package com.quarkrag.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quarkrag.store.VectorStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Optional real LLM backend, used when {@code quarkrag.llm=openai}.
 *
 * <p>Deliberately written against the raw HTTP API with the JDK client instead
 * of pulling in an SDK: fewer dependencies, no reflection, and it stays
 * GraalVM-native-friendly.
 *
 * <p>The prompt pins the model to the retrieved context and instructs it to say
 * so when the answer is absent — the cheapest available guard against
 * hallucination in a RAG system.
 */
public class OpenAiLlm implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final LlmClient fallback = new ExtractiveLlm();

    public OpenAiLlm(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public String answer(String question, List<VectorStore.Hit> contexts) {
        try {
            StringBuilder context = new StringBuilder();
            for (VectorStore.Hit hit : contexts) {
                context.append("[").append(hit.id()).append("] ").append(hit.text()).append("\n\n");
            }

            String prompt = """
                    Answer the question using only the context below. \
                    If the context does not contain the answer, say so plainly.

                    Context:
                    %s
                    Question: %s
                    Answer:""".formatted(context, question);

            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0);
            ArrayNode messages = body.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return fallback.answer(question, contexts);
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? fallback.answer(question, contexts) : content.asText().trim();
        } catch (Exception e) {
            // never fail the request because the model is unreachable
            return fallback.answer(question, contexts);
        }
    }

    @Override
    public String id() {
        return model;
    }
}
