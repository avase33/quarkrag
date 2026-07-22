package com.quarkrag.api;

import com.quarkrag.rag.RagService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/** The public API (see {@code proto/protocol.md}). */
@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RagResource {

    @Inject
    RagService rag;

    public record IngestRequest(String docId, String text) {
    }

    public record QueryRequest(String question, Integer k) {
    }

    @POST
    @Path("/ingest")
    public Response ingest(IngestRequest request) {
        if (request == null || request.docId() == null || request.docId().isBlank()
                || request.text() == null || request.text().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "docId and text are required"))
                    .build();
        }
        return Response.ok(rag.ingest(request.docId(), request.text())).build();
    }

    @POST
    @Path("/query")
    public Response query(QueryRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "question is required"))
                    .build();
        }
        int k = request.k() == null ? 4 : request.k();
        return Response.ok(rag.query(request.question(), k)).build();
    }

    @DELETE
    @Path("/documents/{docId}")
    public Map<String, Object> delete(@PathParam("docId") String docId) {
        return Map.of("docId", docId, "removed", rag.delete(docId));
    }

    @GET
    @Path("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "documents", rag.store().documentCount(),
                "chunks", rag.store().chunkCount(),
                "embeddingDim", rag.embedder().dimensions(),
                "store", rag.store().name(),
                "llm", rag.llm().id());
    }
}
