package com.careerai.compatibility_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/** Faux serveur Ollama en mémoire (127.0.0.1, port aléatoire) : renvoie des réponses scénarisées. */
class FakeOllama implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Reply(int status, String body, long delayMs) { }

    private final HttpServer server;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    final List<String> requests = new CopyOnWriteArrayList<>();

    FakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/chat", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply r = replies.poll();
            if (r == null) r = new Reply(500, "{\"error\":\"aucune réponse prévue\"}", 0);
            if (r.delayMs() > 0) {
                try {
                    Thread.sleep(r.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(r.status(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException ignored) {
                // le client a pu abandonner (test de timeout)
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    FakeOllama reply(int status, String body) {
        replies.add(new Reply(status, body, 0));
        return this;
    }

    FakeOllama replyAfter(long delayMs, String body) {
        replies.add(new Reply(200, body, delayMs));
        return this;
    }

    /** Message assistant simple (texte libre, sans appel d'outil). */
    FakeOllama replyContent(String content) {
        return reply(200, json(Map.of("message", Map.of("role", "assistant", "content", content))));
    }

    /** Message assistant qui appelle un outil ; arguments = objet ou chaîne JSON. */
    FakeOllama replyToolCall(String toolName, Object arguments) {
        Map<String, Object> call = Map.of("function", Map.of("name", toolName, "arguments", arguments));
        return reply(200, json(Map.of("message",
                Map.of("role", "assistant", "content", "", "tool_calls", List.of(call)))));
    }

    static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
