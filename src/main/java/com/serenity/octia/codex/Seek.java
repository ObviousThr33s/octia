package com.serenity.octia.codex;

/**
 * A call: {@code SEEK KEG |ALL|}.
 *
 * <p>{@code SEEK} is the project's call verb and the only one. Not "find",
 * not "get", not "resolve". It denotes a directed request to a named target
 * that may or may not answer - which is why this type carries no result and
 * promises none.
 *
 * <p>The scope is optional. Absent, the call is unaddressed; present, it names
 * who the call is for.
 */
public record Seek(String target, Scope scope) {

    public Seek {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("SEEK needs a target");
        }
        target = target.trim().toUpperCase();
    }

    /** An unaddressed call. */
    public static Seek of(String target) {
        return new Seek(target, null);
    }

    /** A call addressed to a scope. */
    public static Seek of(String target, Scope scope) {
        return new Seek(target, scope);
    }

    /** Parses {@code SEEK KEG |ALL|} or {@code SEEK KEG}. */
    public static Seek parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("no seek line given");
        }
        String body = line.trim();
        if (!body.toUpperCase().startsWith("SEEK")) {
            throw new IllegalArgumentException(
                    "not a SEEK line: '" + line + "'. SEEK is the call verb; no synonym is valid.");
        }
        body = body.substring(4).trim();

        int pipe = body.indexOf('|');
        if (pipe < 0) {
            return new Seek(body, null);
        }
        String target = body.substring(0, pipe).trim();
        Scope scope = Scope.parse(body.substring(pipe));
        return new Seek(target, scope);
    }

    public boolean isAddressed() {
        return scope != null;
    }

    public String toNotation() {
        return scope == null ? "SEEK " + target : "SEEK " + target + " " + scope.toNotation();
    }

    @Override
    public String toString() {
        return toNotation();
    }
}
