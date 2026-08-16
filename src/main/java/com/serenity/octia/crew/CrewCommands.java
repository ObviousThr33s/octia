package com.serenity.octia.crew;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /octia crew} — the whole surface a player touches.
 *
 * <p>Op-level, because seating a player is not something an ordinary guest on a
 * LAN world should be able to do. The practical consequence is worth knowing
 * before the first try: a singleplayer world opened to LAN <b>with cheats off</b>
 * has no ops at all, and none of this will run. Open it with cheats on.
 *
 * <p>{@code order} exists for the same reason a test harness does. It drives a
 * crew member by hand, with no model involved, which is how you tell a crew that
 * cannot walk from a cleric that is not answering — two failures that look
 * identical from across the room.
 */
final class CrewCommands {

    private CrewCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal(Octia.MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("crew")
                                .executes(CrewCommands::status)
                                .then(Commands.literal("muster")
                                        .executes(CrewCommands::muster))
                                .then(Commands.literal("bench")
                                        .executes(CrewCommands::bench))
                                .then(Commands.literal("summon")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> summon(context, ""))
                                                .then(Commands.argument("model", StringArgumentType.greedyString())
                                                        .executes(context -> summon(context,
                                                                StringArgumentType.getString(context, "model"))))))
                                // Brigadier matches the literal before the
                                // argument, so "all" is always the sweep. "all"
                                // is also a legal seat name (/octia crew summon
                                // all) and a crew member wearing it can never be
                                // reached by the branch below.
                                .then(Commands.literal("dismiss")
                                        .then(Commands.literal("all")
                                                .executes(CrewCommands::dismissAll))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(CrewCommands::dismiss)))
                                .then(Commands.literal("order")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("order", StringArgumentType.greedyString())
                                                        .executes(CrewCommands::order)))))));
    }

    // ---- status --------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }

        source.sendSuccess(() -> benchLine(crew), false);

        if (crew.aboard().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(Octia.tr("crew.none"))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.aboard"),
                crew.aboard().size(), crew.maxCrew()), false);

        for (CrewPlayer body : crew.aboard()) {
            Cleric cleric = body.cleric();
            String tending = cleric.model().isEmpty() ? "offline tender" : cleric.model();
            String tally = cleric.answered() + " answered, " + cleric.unanswered() + " ignored"
                    + (cleric.isAsking() ? ", asking" : "");
            source.sendSuccess(() -> Component.literal("  " + pad(body.seatName(), 17))
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(pad(tending, 34)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(pad(body.order().toString(), 20)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(tally).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return crew.aboard().size();
    }

    private static Component benchLine(Crew crew) {
        ClericBench.Reach reach = crew.bench().reach();
        if (reach != null) {
            return Component.translatable(Octia.tr("crew.bench.reached"),
                    Component.literal(reach.describe()).withStyle(ChatFormatting.GREEN));
        }
        return Component.translatable(Octia.tr("crew.bench.silent"),
                Component.literal(crew.bench().lastError()).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY);
    }

    // ---- bench ---------------------------------------------------------------

    /**
     * Asks the bench again, now.
     *
     * <p>The probe is asynchronous and the answer is reported when it lands, on
     * the server thread — the command returns first. That ordering is the point:
     * a command that blocked the server for three seconds per dead endpoint
     * would freeze the game for as long as the bench is down, which is precisely
     * when the game must not freeze.
     */
    private static int bench(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.bench.asking"),
                        String.join(", ", crew.bench().candidates()))
                .withStyle(ChatFormatting.GRAY), false);

        MinecraftServer server = source.getServer();
        crew.bench().probe().whenComplete((reach, error) ->
                server.execute(() -> source.sendSuccess(() -> benchLine(crew), false)));
        return 1;
    }

    // ---- summon / muster -----------------------------------------------------

    private static int summon(CommandContext<CommandSourceStack> context, String model) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        if (!SeatName.isLegal(name)) {
            source.sendFailure(Component.translatable(Octia.tr("crew.bad_name"), name, SeatName.MAX));
            return 0;
        }
        if (crew.aboard().size() >= crew.maxCrew()) {
            source.sendFailure(Component.translatable(Octia.tr("crew.full"), crew.maxCrew()));
            return 0;
        }

        CrewPlayer body = crew.summon(name, model, source.getLevel(),
                source.getPosition(), source.getRotation().y);
        if (body == null) {
            source.sendFailure(Component.translatable(Octia.tr("crew.taken"), name));
            return 0;
        }
        announceSeat(source.getServer(), body);
        return 1;
    }

    private static int muster(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 where = source.getPosition();
        List<String> seated = crew.muster(level, where, source.getRotation().y);

        if (seated.isEmpty()) {
            source.sendFailure(Component.translatable(Octia.tr("crew.nothing_to_muster")));
            return 0;
        }
        for (String name : seated) {
            CrewPlayer body = crew.find(name);
            if (body != null) {
                announceSeat(source.getServer(), body);
            }
        }
        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.mustered"), seated.size()), true);
        return seated.size();
    }

    /**
     * The line that makes a seating visible.
     *
     * <p>Vanilla has already said "X joined the game" by this point — that comes
     * out of {@code placeNewPlayer} like it does for anybody. This adds the part
     * vanilla cannot know: which cleric is behind the name, and where it is
     * being served from.
     */
    private static void announceSeat(MinecraftServer server, CrewPlayer body) {
        String model = body.cleric().model();
        Crew.announce(server, Component.translatable(
                        model.isEmpty() ? Octia.tr("crew.seated.tender") : Octia.tr("crew.seated.cleric"),
                        body.getDisplayName(),
                        Component.literal(model).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
    }

    // ---- dismiss -------------------------------------------------------------

    private static int dismiss(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        CrewPlayer body = crew.find(name);
        if (body == null) {
            source.sendFailure(Component.translatable(Octia.tr("crew.unknown"), name));
            return 0;
        }
        crew.logOff(body, "dismissed");
        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.dismissed"), name), true);
        return 1;
    }

    private static int dismissAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }
        int left = crew.dismissAll("dismissed");
        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.dismissed_all"), left), true);
        return left;
    }

    // ---- order ---------------------------------------------------------------

    private static int order(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Crew crew = crew(source);
        if (crew == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        CrewPlayer body = crew.find(name);
        if (body == null) {
            source.sendFailure(Component.translatable(Octia.tr("crew.unknown"), name));
            return 0;
        }

        Order parsed = Order.parse(StringArgumentType.getString(context, "order"));
        body.setOrder(parsed);
        source.sendSuccess(() -> Component.translatable(Octia.tr("crew.ordered"), name,
                Component.literal(parsed.toString()).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    // ---- shared --------------------------------------------------------------

    private static Crew crew(CommandSourceStack source) {
        Crew crew = Crew.of(source.getServer());
        if (crew == null) {
            source.sendFailure(Component.translatable(Octia.tr("crew.no_muster")));
        }
        return crew;
    }

    /** Left-aligned column padding for the status table. The clip leaves at least one space. */
    private static String pad(String text, int width) {
        String clipped = text.length() > width - 1 ? text.substring(0, width - 1) : text;
        return clipped + " ".repeat(width - clipped.length());
    }
}
