package com.serenity.octia.crew;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.serenity.octia.Octia;

/**
 * The bench, reached over HTTP.
 *
 * <p>The clerics are GGUF files on a disk. Nothing in a Minecraft mod can read
 * one — inference belongs to llama.cpp, LM Studio, Ollama, or whatever else is
 * serving that day. So this class knows exactly one thing: how to ask an
 * OpenAI-shaped or Ollama-shaped HTTP endpoint a question, and how to find out
 * which of those it is talking to.
 *
 * <p><b>Two dialects, because both are on the table.</b> LM Studio,
 * llama.cpp's own {@code llama-server}, Jan, and vLLM all speak
 * {@code /v1/chat/completions}. Ollama speaks {@code /api/chat} and answers a
 * different shape. Probing for {@code /v1/models} first and falling back to
 * {@code /api/tags} identifies the server without asking the player to declare
 * it, and the answer doubles as the roster of clerics that endpoint can serve.
 *
 * <p><b>Nothing here ever runs on the server thread.</b> Every call returns a
 * future completed on a small daemon pool, and every failure lands in
 * {@link #lastError()} rather than in an exception somebody has to catch mid
 * tick. A model that takes eleven seconds to answer is a normal Tuesday on a
 * 14B; a game that waits for it is a bug.
 */
public final class ClericBench {

    /** Long enough for a cold 14B on a shared GPU, short enough to notice a hang. */
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(60);

    /** A probe is either answered promptly or the endpoint is not there. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Ceiling on an order's length. An order is at most a handful of words, and
     * a small ceiling is also the cheapest guard against a model that decides to
     * write an essay — it stops generating rather than burning a GPU minute.
     */
    private static final int ANSWER_TOKENS = 48;

    /** Which shape of API is on the other end. */
    public enum Dialect {
        /** {@code POST /v1/chat/completions} — LM Studio, llama-server, Jan, vLLM. */
        OPENAI,
        /** {@code POST /api/chat} — Ollama. */
        OLLAMA
    }

    /** An endpoint that answered, and what it said it could serve. */
    public record Reach(String base, Dialect dialect, List<String> models) {

        public String describe() {
            return base + " (" + dialect.name().toLowerCase(java.util.Locale.ROOT)
                    + ", " + models.size() + " clerics)";
        }
    }

    private final List<String> candidates;
    private final HttpClient http;
    private final ExecutorService pool;
    private final Gson gson = new Gson();
    private final AtomicBoolean probing = new AtomicBoolean();

    private volatile Reach reach;
    private volatile String lastError = "not probed yet";

    ClericBench(List<String> candidates) {
        this.candidates = List.copyOf(candidates);
        // Daemon threads: a crew member left mid-question must never be the
        // reason the JVM will not exit when the world closes.
        this.pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread t = new Thread(runnable, "octia-cleric");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
                .executor(pool)
                .build();
    }

    /** The endpoint currently answering, or null if the bench is silent. */
    public Reach reach() {
        return reach;
    }

    public String lastError() {
        return lastError;
    }

    public List<String> candidates() {
        return candidates;
    }

    /**
     * Looks for an endpoint, in the configured order, and remembers the first
     * that answers. Safe to call repeatedly; overlapping probes are dropped.
     */
    public CompletableFuture<Reach> probe() {
        if (!probing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(reach);
        }
        return CompletableFuture.supplyAsync(this::probeAll, pool)
                .whenComplete((found, error) -> {
                    if (error != null) {
                        lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
                    }
                    probing.set(false);
                });
    }

    private Reach probeAll() {
        List<String> tried = new ArrayList<>();
        for (String base : candidates) {
            String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            Reach found = probeOne(trimmed);
            if (found != null) {
                reach = found;
                lastError = "";
                Octia.LOGGER.info("Octia crew: bench answering at {}", found.describe());
                return found;
            }
            tried.add(trimmed);
        }
        reach = null;
        lastError = "no endpoint answered (" + String.join(", ", tried) + ")";
        return null;
    }

    private Reach probeOne(String base) {
        List<String> openAi = models(base + "/v1/models", "data", "id");
        if (openAi != null) {
            return new Reach(base, Dialect.OPENAI, openAi);
        }
        List<String> ollama = models(base + "/api/tags", "models", "name");
        if (ollama != null) {
            return new Reach(base, Dialect.OLLAMA, ollama);
        }
        return null;
    }

    /** @return the ids listed at this URL, or null if it did not answer usefully */
    private List<String> models(String url, String arrayKey, String idKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!body.has(arrayKey) || !body.get(arrayKey).isJsonArray()) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            for (JsonElement element : body.getAsJsonArray(arrayKey)) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has(idKey)) {
                    ids.add(entry.get(idKey).getAsString());
                }
            }
            return ids;
        } catch (Exception failed) {
            // An endpoint that is not there is the normal case, not an incident.
            return null;
        }
    }

    /**
     * Asks one cleric one question.
     *
     * <p>The future completes with the model's raw text, or with null if the
     * bench could not be reached — never exceptionally, because the caller is a
     * tick loop and a tick loop has nowhere to put an exception.
     */
    public CompletableFuture<String> ask(String model, String system, String user) {
        Reach current = reach;
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(current.base() + path(current.dialect())))
                    .timeout(ANSWER_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            gson.toJson(body(current.dialect(), model, system, user))))
                    .build();
        } catch (Exception malformed) {
            lastError = "bad endpoint: " + malformed.getMessage();
            return CompletableFuture.completedFuture(null);
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        // A transport failure retires the endpoint outright:
                        // nothing answered, so the bench is presumed gone,
                        // isServed() goes false for every cleric, and the whole
                        // crew falls through to the Tender until Crew's reprobe
                        // finds it again - up to thirty seconds later. One
                        // timeout is enough. That is the deliberate trade: a
                        // crew that keeps walking beats a crew that keeps
                        // waiting.
                        lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
                        reach = null;
                        return null;
                    }
                    if (response.statusCode() != 200) {
                        // Deliberately not symmetrical. An HTTP error means
                        // something IS there and is unhappy - a model id it does
                        // not have, a context overflow - so the endpoint is kept
                        // and only this one question is lost.
                        lastError = "HTTP " + response.statusCode() + " from " + current.base();
                        return null;
                    }
                    try {
                        String said = content(current.dialect(), response.body());
                        lastError = "";
                        return said;
                    } catch (Exception unreadable) {
                        lastError = "unreadable answer: " + unreadable.getMessage();
                        return null;
                    }
                });
    }

    private static String path(Dialect dialect) {
        return dialect == Dialect.OLLAMA ? "/api/chat" : "/v1/chat/completions";
    }

    private static JsonObject body(Dialect dialect, String model, String system, String user) {
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("stream", false);
        body.addProperty("temperature", 0.7);

        if (dialect == Dialect.OLLAMA) {
            JsonObject options = new JsonObject();
            options.addProperty("num_predict", ANSWER_TOKENS);
            body.add("options", options);
        } else {
            body.addProperty("max_tokens", ANSWER_TOKENS);
        }
        return body;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String content(Dialect dialect, String json) {
        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        if (dialect == Dialect.OLLAMA) {
            return body.getAsJsonObject("message").get("content").getAsString();
        }
        return body.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    /** Lets go of the pool. The crew is gone by the time this is called. */
    void close() {
        pool.shutdownNow();
    }
}
