package com.serenity.octia.crew;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.serenity.octia.Octia;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Where the crew is told how to find the bench.
 *
 * <p>{@code config/octia-crew.json}, written with its defaults the first time
 * the mod runs so that the file itself is the documentation. Read once at
 * server start; changing it takes a restart, which is the right trade for a
 * file nobody edits during play.
 *
 * <p><b>Parsed by hand rather than bound to a record.</b> Gson's record support
 * arrived recently enough that the version Minecraft happens to bundle is not
 * something to bet a crash on, and every field here has a sane default anyway —
 * so a half-written or hand-mangled config degrades to the defaults instead of
 * throwing on load. Which is the same principle as the rest of the crew: the
 * thing still runs when its inputs are missing.
 */
final class CrewConfig {

    /** Where a local model server usually is, in the order worth trying. */
    private static final List<String> DEFAULT_ENDPOINTS = List.of(
            "http://127.0.0.1:1234",    // LM Studio
            "http://127.0.0.1:11434",   // Ollama
            "http://127.0.0.1:8080");   // llama.cpp's own llama-server

    private static final int DEFAULT_POLL_SECONDS = 5;
    private static final int DEFAULT_MAX_CREW = 8;

    final List<String> endpoints;
    final List<String> roster;
    final int pollSeconds;
    final int maxCrew;

    private CrewConfig(List<String> endpoints, List<String> roster, int pollSeconds, int maxCrew) {
        this.endpoints = endpoints;
        this.roster = roster;
        this.pollSeconds = pollSeconds;
        this.maxCrew = maxCrew;
    }

    static CrewConfig defaults() {
        return new CrewConfig(DEFAULT_ENDPOINTS, List.of(), DEFAULT_POLL_SECONDS, DEFAULT_MAX_CREW);
    }

    /** Loads the config, writing the defaults first if there is no file yet. */
    static CrewConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("octia-crew.json");
        if (!Files.exists(path)) {
            CrewConfig fresh = defaults();
            fresh.write(path);
            return fresh;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return new CrewConfig(
                    strings(json, "endpoints", DEFAULT_ENDPOINTS),
                    strings(json, "roster", List.of()),
                    integer(json, "poll_seconds", DEFAULT_POLL_SECONDS),
                    integer(json, "max_crew", DEFAULT_MAX_CREW));
        } catch (Exception unreadable) {
            Octia.LOGGER.warn("Octia crew: {} could not be read ({}); using defaults.",
                    path, unreadable.getMessage());
            return defaults();
        }
    }

    private void write(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("_note", "Endpoints are tried in order; the first that answers is the bench. "
                + "roster is who to muster when nothing answers - names only, one seat each.");

        JsonArray ends = new JsonArray();
        endpoints.forEach(ends::add);
        json.add("endpoints", ends);

        // A literal empty array, not the field - and that is only correct
        // because write() is reached from exactly one place: load(), on the
        // object defaults() just built, whose roster is always empty. A second
        // caller would silently drop a configured roster.
        json.add("roster", new JsonArray());
        json.addProperty("poll_seconds", pollSeconds);
        json.addProperty("max_crew", maxCrew);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(json),
                    StandardCharsets.UTF_8);
            Octia.LOGGER.info("Octia crew: wrote {}", path);
        } catch (IOException failed) {
            Octia.LOGGER.warn("Octia crew: could not write {} ({}); running on defaults.",
                    path, failed.getMessage());
        }
    }

    /**
     * The non-empty strings under {@code key}, or {@code fallback}.
     *
     * <p><b>An empty list is not a way of saying "none".</b> {@code []}, and a
     * list of nothing but blanks, both fall back - so {@code "endpoints": []}
     * cannot be used to switch the bench off; it restores the three defaults.
     * Running deliberately without a bench means pointing endpoints at
     * something that does not answer. The {@code _note} written into the file
     * does not say this, and nothing else does either.
     *
     * <p>{@code getAsString} throws on a nested object or array, and that throw
     * is caught by {@link #load()} - which discards the WHOLE config. One
     * malformed endpoint entry also loses roster, poll_seconds and max_crew.
     */
    private static List<String> strings(JsonObject json, String key, List<String> fallback) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            String value = element.getAsString().trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    /**
     * Clamped rather than rejected: 0 or negative would mean "no crew" and "ask
     * every tick", and the nearest legal value beats refusing to start. Crew
     * clamps poll_seconds a second time, to a floor of 20 ticks.
     */
    private static int integer(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? Math.max(1, json.get(key).getAsInt()) : fallback;
        } catch (Exception notANumber) {
            return fallback;
        }
    }
}
