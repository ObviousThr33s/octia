package com.serenity.octia.crew;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One cleric, tending one body.
 *
 * <p>This is the piece that decides <em>when</em> to ask, holds the single
 * question that may be in flight, and hands the answer back to the tick loop.
 * The asking itself belongs to {@link ClericBench}; the interpreting belongs to
 * {@link Order}.
 *
 * <p><b>One question at a time, and no queue.</b> A 14B cleric on a shared GPU
 * can take ten seconds to answer. If the tick loop asked every four seconds
 * regardless, a slow cleric would accumulate questions it can never catch up
 * on, and the crew member would act on the world as it was a minute ago. So a
 * cleric with a question outstanding is simply not asked again, and the poll
 * interval is a floor rather than a schedule.
 *
 * <p><b>An unserved cleric is not an error.</b> A crew member whose model is
 * not on the bench — because nothing is serving, or because the endpoint went
 * away — still has a cleric object; {@link #isServed()} is simply false and the
 * driver falls through to {@link Tender}. That is the cleric law in code: pull
 * the wire and the crew still walks.
 */
public final class Cleric {

    private final ClericBench bench;

    /**
     * Not final: a crew member mustered while nothing was serving starts with no
     * model at all, and picks one up when the bench comes back. Volatile because
     * {@link #isServed()} is read from the tick and written from the same thread
     * but read again by the status command, and a stale empty string there would
     * report a working crew member as offline.
     */
    private volatile String model;

    private final AtomicBoolean asking = new AtomicBoolean();
    private final AtomicReference<String> reply = new AtomicReference<>();

    /** Server tick before which no question is asked. */
    private long nextAsk;

    private int answered;
    private int unanswered;
    private volatile String lastSaid = "";

    Cleric(ClericBench bench, String model) {
        this.bench = bench;
        this.model = model == null ? "" : model;
    }

    /** The model id as the endpoint names it, or empty if this crew member has none. */
    public String model() {
        return model;
    }

    /** Gives an unbound crew member a model, once the bench has one to give. */
    void bind(String model) {
        this.model = model == null ? "" : model;
    }

    /** True if there is a reachable endpoint and a model to ask. */
    public boolean isServed() {
        return !model.isEmpty() && bench.reach() != null;
    }

    public boolean isAsking() {
        return asking.get();
    }

    public int answered() {
        return answered;
    }

    public int unanswered() {
        return unanswered;
    }

    /** The last thing this cleric actually said, for {@code /octia crew}. */
    public String lastSaid() {
        return lastSaid;
    }

    /** True when this cleric is idle, served, and due another question. */
    boolean isDue(long tick) {
        return isServed() && !asking.get() && tick >= nextAsk;
    }

    /**
     * Sends one question. Returns immediately; the answer arrives in
     * {@link #takeReply()} whenever it arrives, or never.
     *
     * @param cooldownTicks the floor before the next question, applied now so a
     *                      cleric that never answers still stops being asked
     */
    void ask(String system, String situation, long tick, int cooldownTicks) {
        if (!asking.compareAndSet(false, true)) {
            return;
        }
        nextAsk = tick + cooldownTicks;
        bench.ask(model, system, situation).whenComplete((said, error) -> {
            if (said != null && !said.isBlank()) {
                reply.set(said);
            }
            asking.set(false);
        });
    }

    /**
     * Takes the pending answer, if one has landed.
     *
     * <p>Called from the server thread only, which is what makes the rest of the
     * crew free of locks: the answer crosses threads exactly here, in one
     * reference, and everything downstream of it touches the world on the tick.
     */
    String takeReply() {
        String said = reply.getAndSet(null);
        if (said == null) {
            return null;
        }
        answered++;
        lastSaid = said.length() > 60 ? said.substring(0, 60) + "..." : said;
        return said;
    }

    /** Noted when an answer arrived but said nothing the vocabulary recognised. */
    void countUnanswered() {
        unanswered++;
    }
}
