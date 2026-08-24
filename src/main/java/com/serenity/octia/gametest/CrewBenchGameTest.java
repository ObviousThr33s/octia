package com.serenity.octia.gametest;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.serenity.octia.crew.ClericBench;
import com.serenity.octia.crew.Crew;
import com.serenity.octia.crew.CrewPlayer;
import com.serenity.octia.crew.SeatName;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The network side of the crew, declared and then held to it.
 *
 * <p><b>What was wrong before this file existed.</b> Every summon in
 * {@link CrewGameTest} passes {@code ""} as the model, which is the offline
 * tender, and {@link ClericBench} files every failure into
 * {@link ClericBench#lastError()} rather than throwing — deliberately, because
 * its callers are tick loops. Both choices are right on their own. Together they
 * meant the suite went green identically whether a bench was answering or
 * nothing was listening at all, and <em>nothing anywhere asserted what a crew
 * member does once a model is actually attached</em>. The seam that carries the
 * whole crew was the one seam with no coverage.
 *
 * <p><b>Absence is not allowed to be quiet.</b> There is no skip in this file.
 * The network side is <em>declared</em> by one system property and then checked
 * against reality, and any disagreement is a failure:
 *
 * <ul>
 *   <li>{@code -Doctia.crew.network=required} — the default. A bench must
 *       answer and a cleric must come back with words. Nothing listening is a
 *       red test, not a quiet one.</li>
 *   <li>{@code -Doctia.crew.network=absent} — the opposite claim, and equally
 *       binding. The bench must report itself unreachable, its error must name
 *       every candidate it tried, and the crew must still work through the
 *       tender. A bench that answers here is <em>also</em> a red test, because
 *       the run was declared offline and was not.</li>
 * </ul>
 *
 * <p>Any other value is refused outright. A typo must not read as "off".
 *
 * <p><b>One switch, one verdict.</b> Both tests read the same property, so the
 * network side passes together or fails together. There is no arrangement in
 * which half of it is checked and the other half silently isn't — that state is
 * exactly what this file was written to end.
 *
 * <p><b>Why this file waits on a clock and not on ticks.</b> The first version
 * polled across game ticks and failed for a reason worth recording: a game test
 * server ticks <em>flat out</em>, with none of the fifty-millisecond sleep a
 * real server takes. The whole suite runs in about seven seconds, so a budget of
 * 1600 ticks expired in roughly one second of real time while an HTTP request
 * was still in flight. Ticks are the wrong currency for anything bounded by a
 * socket. Every wait here is therefore a real deadline on the calling thread —
 * blocking, which would be a bug in a tick loop and is the only honest thing in
 * a test. No ticks elapse while it blocks, so the game test timeout is not
 * consumed either.
 *
 * <p><b>On CI.</b> A GitHub runner has no bench, so under the default this file
 * goes red there until either an endpoint is provided or the workflow declares
 * {@code -Doctia.crew.network=absent}. That is the intended behaviour and not an
 * oversight: a green CI that proves nothing about the crew is what we had.
 */
public class CrewBenchGameTest implements FabricGameTest {

    /** Declares whether a bench is supposed to be reachable during this run. */
    private static final String NETWORK = "octia.crew.network";

    private static final String REQUIRED = "required";
    private static final String ABSENT = "absent";

    /** {@link ClericBench}'s {@code lastError()} before any probe has reported. */
    private static final String NOT_PROBED = "not probed yet";

    /** Legal, unmistakable, and not a name {@link CrewGameTest} already uses. */
    private static final String SEAT = "Benchcleric";

    /** A floor to stand on. The empty structure does not come with one. */
    private static final int FLOOR = 6;

    /** Three seconds per candidate, and the default list has three. */
    private static final long PROBE_WAIT_MS = 20_000L;

    /** {@code ClericBench} allows a model sixty seconds; leave it room to use them. */
    private static final long ANSWER_WAIT_MS = 90_000L;

    /**
     * A question whose answer is worthless and whose <em>shape</em> is the whole
     * point. We are not grading the model. We are proving that a request left
     * this mod, crossed a socket, and came back as text.
     */
    private static final String SYSTEM = "You are a crew member aboard a ship. Answer in under ten words.";
    private static final String USER = "Say that you are aboard.";

    /** The bench is reachable exactly as declared — no more, no less. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBenchIsExactlyAsReachableAsDeclared(GameTestHelper helper) {
        boolean required = networkRequired();
        ClericBench bench = crew(helper.getLevel().getServer()).bench();

        bench.probe();
        awaitVerdict(bench);

        ClericBench.Reach reach = bench.reach();
        List<String> tried = bench.candidates();

        if (required && reach == null) {
            throw noBenchAnswered(bench);
        }
        if (!required && reach != null) {
            throw new AssertionError("the run declared " + NETWORK + "=" + ABSENT
                    + " but a bench answered at " + reach.describe()
                    + ". An offline run that is quietly online proves nothing about the tender,"
                    + " which is the only thing " + ABSENT + " exists to check.");
        }

        if (required) {
            if (reach.models().isEmpty()) {
                throw new AssertionError("the bench at " + reach.base()
                        + " answered but listed no clerics. An endpoint serving nothing cannot be"
                        + " bound to a seat, and every summon would fall through to the tender"
                        + " while looking served.");
            }
        } else {
            String said = bench.lastError();
            if (said == null || said.isBlank()) {
                throw new AssertionError("the bench is unreachable and said nothing about why."
                        + " lastError() is what the /crew bench command prints; blank there means"
                        + " a player is told the crew is offline with no way to find out.");
            }
            for (String candidate : tried) {
                String trimmed = candidate.endsWith("/")
                        ? candidate.substring(0, candidate.length() - 1)
                        : candidate;
                if (!said.contains(trimmed)) {
                    throw new AssertionError("the unreachable bench did not name " + trimmed
                            + " among what it tried. It said: " + said
                            + ". An error that hides which endpoint was missed sends whoever reads"
                            + " it to the wrong machine.");
                }
            }
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> Under {@code required}, a cleric bound to a
     * real model has to come back with words — the one thing the suite has never
     * checked. Under {@code absent}, the same seat has to come up anyway, which
     * is the tender doing its job.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aClericAnswersInWordsOrTheTenderCoversForIt(GameTestHelper helper) {
        floor(helper);
        boolean required = networkRequired();
        MinecraftServer server = helper.getLevel().getServer();
        Crew crew = crew(server);
        ClericBench bench = crew.bench();

        bench.probe();
        awaitVerdict(bench);

        // Not a ternary any more, and the extra lines are the whole point: the
        // required branch has to check for null before it dereferences. See
        // noBenchAnswered.
        String model;
        if (required) {
            ClericBench.Reach reach = bench.reach();
            if (reach == null) {
                throw noBenchAnswered(bench);
            }
            model = chatCleric(reach);
        } else {
            model = "a-model-that-is-not-there";
        }
        CrewPlayer body = crew.summon(SEAT, model, helper.getLevel(), stand(helper, 2, 2), 0.0F);
        try {
            if (body == null) {
                throw new AssertionError("summon refused a legal name that nobody was using");
            }
            if (server.getPlayerList().getPlayerByName(SEAT) != body) {
                throw new AssertionError(SEAT + " was summoned with the model " + model
                        + " but the player list cannot find it. Binding a cleric to a seat must not"
                        + " change whether the body logs on.");
            }
            if (required) {
                assertAnswersInWords(bench, model);
            }
        } finally {
            if (body != null) {
                crew.logOff(body, "the test is over");
            }
        }
        helper.succeed();
    }

    // ---- shared --------------------------------------------------------------

    /**
     * Asks one cleric one question and insists on words back.
     *
     * <p>{@link ClericBench#ask} completes with null rather than throwing, so
     * this is the only place a silent bench can be caught at all.
     */
    private static void assertAnswersInWords(ClericBench bench, String model) {
        String said;
        try {
            said = bench.ask(model, SYSTEM, USER).get(ANSWER_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            throw new AssertionError("the cleric " + model + " did not answer within "
                    + (ANSWER_WAIT_MS / 1000) + "s. The bench said: " + bench.lastError()
                    + ". A crew member whose model never returns holds its seat and never acts.");
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting on " + model);
        } catch (ExecutionException broken) {
            throw new AssertionError("ask() completed exceptionally, which it documents that it"
                    + " never does: " + broken.getCause());
        }

        if (said == null) {
            throw new AssertionError("a cleric was asked a question and nothing came back."
                    + " ask() returns null rather than throwing, so this is the only place it can"
                    + " be caught. The bench said: " + bench.lastError());
        }
        if (said.isBlank()) {
            throw new AssertionError("the cleric answered with nothing but whitespace. A blank"
                    + " answer reaches Order parsing as an empty order and the crew member stands"
                    + " still looking served.");
        }
    }

    /**
     * Waits for a probe to have reached a verdict, judged on what can be
     * observed rather than on the future {@link ClericBench#probe()} returns.
     *
     * <p><b>Why not simply wait on that future.</b> {@code probe()} drops an
     * overlapping call and hands back a future that is <em>already complete</em>,
     * and {@link Crew} probes on its own poll every few seconds. So a completed
     * future proves only that some probe finished — possibly one that started
     * before this test did, possibly none at all. Waiting on it makes the test
     * pass or fail according to where that poll happened to be, which is the
     * definition of flaky.
     *
     * <p>This reads {@code lastError()}'s initial string, coupling the test to a
     * constant in {@code ClericBench}. That is deliberate: the alternative is
     * the race above, and a changed constant fails loudly here rather than
     * quietly passing.
     */
    private static void awaitVerdict(ClericBench bench) {
        long deadline = System.nanoTime() + PROBE_WAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (bench.reach() != null) {
                return;
            }
            String said = bench.lastError();
            if (said != null && !said.isBlank() && !NOT_PROBED.equals(said)) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for the bench to report");
            }
        }
        throw new AssertionError("no probe reported a verdict within " + (PROBE_WAIT_MS / 1000)
                + "s - lastError() still reads \"" + bench.lastError()
                + "\". The bench never finished looking, which is itself worth knowing.");
    }

    /**
     * Reads the declaration. An unrecognised value is refused rather than
     * treated as off — a typo that silently disables the network side would
     * rebuild the exact hole this file closes.
     */
    private static boolean networkRequired() {
        String declared = System.getProperty(NETWORK, REQUIRED).trim().toLowerCase(Locale.ROOT);
        if (REQUIRED.equals(declared)) {
            return true;
        }
        if (ABSENT.equals(declared)) {
            return false;
        }
        throw new AssertionError(NETWORK + " is set to \"" + declared + "\", which is neither \""
                + REQUIRED + "\" nor \"" + ABSENT + "\". Refusing to guess: a value that is not"
                + " understood must not read as \"do not check the network side\".");
    }

    /**
     * The one sentence for "declared {@code required}, and nothing answered".
     *
     * <p><b>Shared because it was not, and the asymmetry was the bug.</b>
     * {@link #theBenchIsExactlyAsReachableAsDeclared} guarded {@code reach ==
     * null} and said this. {@link #aClericAnswersInWordsOrTheTenderCoversForIt}
     * handed {@code bench.reach()} straight to {@link #chatCleric} instead, so
     * the same offline machine got a considered diagnostic from one test and
     * {@code Cannot invoke "ClericBench$Reach.models()" because "reach" is null}
     * from the other - for one cause, on one run, seconds apart.
     *
     * <p>Note where the fault was not: {@code reach()} answering null is
     * {@code ClericBench}'s documented contract, and that class never throws on
     * purpose because its caller is a tick loop. Every caller therefore owes it
     * a null check. One shared sentence is how this file stops owing it twice.
     */
    private static AssertionError noBenchAnswered(ClericBench bench) {
        return new AssertionError("the run declared " + NETWORK + "=" + REQUIRED
                + " and no bench answered. Tried " + String.join(", ", bench.candidates())
                + ". The bench said: " + bench.lastError()
                + ". Start an endpoint, or declare " + NETWORK + "=" + ABSENT
                + " and assert the offline contract instead.");
    }

    /**
     * A cleric that can hold a conversation. An embedding model will answer a
     * chat request with an error or a vector, so picking the first id on the
     * list would make this test fail for a reason that has nothing to do with
     * the seam it is guarding.
     *
     * <p>Takes a {@code Reach} that is already known non-null. The caller does
     * the checking - see {@link #noBenchAnswered}.
     */
    private static String chatCleric(ClericBench.Reach reach) {
        // The same failure its sibling reports, in the same words.
        //
        // A run that declares the network required and then meets no endpoint
        // arrives here with a null reach, and dereferencing it turned a stated
        // contract into a NullPointerException forty lines from the assertion
        // that explains it. Two tests failing for one cause should say the one
        // cause twice, not once clearly and once as a stack trace.
        if (reach == null) {
            throw new AssertionError("the run declared " + NETWORK + "=" + REQUIRED
                    + " and no bench answered. Start an endpoint, or declare "
                    + NETWORK + "=" + ABSENT + " and assert the offline contract instead."
                    + " tools/verify.ps1 declares " + ABSENT + " unless given -Bench.");
        }
        for (String model : reach.models()) {
            if (SeatName.isChatCleric(model)) {
                return model;
            }
        }
        throw new AssertionError("the bench at " + reach.base() + " serves "
                + reach.models().size() + " models and not one of them is a chat cleric: "
                + String.join(", ", reach.models()) + ". Nothing here can be bound to a seat.");
    }

    private static Crew crew(MinecraftServer server) {
        Crew crew = Crew.of(server);
        if (crew == null) {
            throw new AssertionError("no muster on a started server - Crew.bootstrap missed SERVER_STARTED");
        }
        return crew;
    }

    /** Feet on top of the floor block at this spot in the test structure. */
    private static Vec3 stand(GameTestHelper helper, int x, int z) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z)));
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < FLOOR; x++) {
            for (int z = 0; z < FLOOR; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
