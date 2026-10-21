package dev.voidmc.hearth.sim.roads;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * The roads' commands (operator level 2), under {@code /hearth roads}:
 * <pre>
 *   /hearth roads stats                 the network, the live roads, road writing and the traffic, with tick timings
 *   /hearth roads near [radius]         the roads near you: class, how built, how long, how dangerous here
 *   /hearth roads journeys [radius]     who is on the roads near you and where they are bound
 *   /hearth roads send &lt;kind&gt;          a journey of that kind from the nearest settlement (to its nearest neighbour)
 *   /hearth roads band                  bandits take to the road nearest you
 *   /hearth roads build &lt;work&gt;         puts that much work into the nearest unfinished live road
 * </pre>
 */
public final class RoadCommands {
	private static final SimpleCommandExceptionType NO_ROADS = new SimpleCommandExceptionType(Component.literal("No roads in this dimension"));

	private RoadCommands() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("roads").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("stats").executes(RoadCommands::stats))
			.then(Commands.literal("near").executes(c -> near(c, 300))
				.then(Commands.argument("radius", IntegerArgumentType.integer(16, 4000)).executes(c -> near(c, IntegerArgumentType.getInteger(c, "radius")))))
			.then(Commands.literal("journeys").executes(c -> journeys(c, 600))
				.then(Commands.argument("radius", IntegerArgumentType.integer(16, 20000)).executes(c -> journeys(c, IntegerArgumentType.getInteger(c, "radius")))))
			.then(Commands.literal("send").then(Commands.argument("kind", StringArgumentType.word()).executes(RoadCommands::send)))
			.then(Commands.literal("band").executes(RoadCommands::band))
			.then(Commands.literal("build").then(Commands.argument("work", IntegerArgumentType.integer(1, 10_000_000)).executes(RoadCommands::build)))));
	}

	private static Roads.State state(CommandSourceStack source) throws CommandSyntaxException {
		Roads.State s = Roads.state(SimServer.world(source.getLevel()));
		if (s == null) {
			throw NO_ROADS.create();
		}
		return s;
	}

	private static void say(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
	}

	private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		say(c.getSource(), "network: " + s.net().describe());
		say(c.getSource(), "building: " + s.live().describe());
		say(c.getSource(), s.applier().describe());
		say(c.getSource(), "traffic: " + s.traffic().describe());
		for (String line : LivingWorldStats.lines()) {
			if (line.contains(" roads:") || line.contains(" traffic:") || line.contains(" travel:")) {
				say(c.getSource(), line);
			}
		}
		return 1;
	}

	private static int near(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		int n = 0;
		for (Way w : s.net().waysNear(at.getX(), at.getZ(), radius + 400)) {
			double[] on = RoadWorld.nearestOn(w, at.getX(), at.getZ());
			if (on == null || on[0] > (double) radius * radius) {
				continue;
			}
			RoadBook.LiveRoad lr = s.live().book().road(w.id());
			String built = w.live() ? lr == null ? "?" : String.format(Locale.ROOT, "%d/%d plots built", lr.fromA + lr.fromB, lr.plots())
				: w.edge().historic() ? "built in history" : "planned";
			say(c.getSource(), String.format(Locale.ROOT, "%s %s %s - %s, %.0f blocks, %d plots, %s, %.0f blocks away, danger here %.2f", w.kind() == dev.voidmc.hearth.api.RoadNetwork.RoadClass.KINGS_ROAD
					? "king's road" : w.spur() ? "spur" : "track", Long.toHexString(w.id()), w.nameA(), w.nameB(), w.length(), w.segments().size(), built,
				Math.sqrt(on[0]), s.traffic().danger(w.id(), on[1])));
			n++;
		}
		if (n == 0) {
			say(c.getSource(), "No roads within " + radius + " blocks (regions not worked out yet are worked out in the background: ask again).");
		}
		return n;
	}

	private static int journeys(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		long now = s.world().clock();
		List<Journey> js = s.traffic().near(at, radius);
		for (Journey j : js) {
			SimSettlement from = s.world().settlement(j.from());
			SimSettlement to = s.world().settlement(j.to());
			BlockPos p = s.traffic().position(j, now);
			say(c.getSource(), String.format(Locale.ROOT, "#%d %s %s -> %s: %.0f%% of %.0f blocks%s%s at %s, %d residents%s", j.id(), j.kind().name().toLowerCase(Locale.ROOT),
				from == null ? "?" : from.name(), to == null ? "?" : to.name(), 100 * j.along(now) / Math.max(1, j.length()), j.length(),
				j.returning(now) ? " (homeward)" : "", j.resting(now) ? " (resting)" : "", p.toShortString(), j.residents().size(),
				j.outcome().isEmpty() ? "" : ", bandits ahead (" + j.outcome() + ")"));
		}
		say(c.getSource(), js.size() + " journeys within " + radius + " blocks; " + s.traffic().describe());
		for (Band b : s.traffic().bands()) {
			say(c.getSource(), String.format(Locale.ROOT, "band #%d: %d strong, morale %.2f, camp at %s, on road %s", b.id(), b.strength(), b.morale(),
				b.camp().toShortString(), Long.toHexString(b.road())));
		}
		return js.size();
	}

	private static SimSettlement nearest(Roads.State s, CommandSourceStack source) {
		return s.world().nearest(BlockPos.containing(source.getPosition()), 100_000) instanceof SimSettlement ss ? ss : null;
	}

	private static int send(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		String name = StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT);
		TravelKind kind;
		try {
			kind = TravelKind.valueOf(name);
		} catch (IllegalArgumentException e) {
			c.getSource().sendFailure(Component.literal("No such kind; one of " + java.util.Arrays.toString(TravelKind.values())));
			return 0;
		}
		SimSettlement from = nearest(s, c.getSource());
		if (from == null) {
			c.getSource().sendFailure(Component.literal("No settlement"));
			return 0;
		}
		SimSettlement to = null;
		double best = Double.MAX_VALUE;
		for (SimSettlement o : s.world().settlements()) {
			double d = o.center().distSqr(from.center());
			if (o != from && d < best) {
				best = d;
				to = o;
			}
		}
		boolean round = kind == TravelKind.PATROL || kind == TravelKind.CARTER || kind == TravelKind.PILGRIMS || kind == TravelKind.MARKET_GOERS;
		List<java.util.UUID> people = kind == TravelKind.PEDLAR || kind == TravelKind.BANDITS ? List.of()
			: Traffic.pick(from, kind == TravelKind.PATROL ? 3 : 2, dev.voidmc.hearth.api.JobType.GUARD);
		long id = s.traffic().send(kind, from, kind == TravelKind.PATROL ? null : to, people, s.world().clock(), 0, round, kind == TravelKind.PATROL ? 300 : 0,
			1200, new CompoundTag());
		say(c.getSource(), id == 0 ? "Not sent (the roads are full, or there is no road)" : "Sent journey #" + id + " from " + from.name()
			+ (to == null || kind == TravelKind.PATROL ? "" : " to " + to.name()));
		return id == 0 ? 0 : 1;
	}

	private static int band(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		Way best = null;
		double bestD = Double.MAX_VALUE;
		for (Way w : s.net().waysNear(at.getX(), at.getZ(), 600)) {
			double[] on = RoadWorld.nearestOn(w, at.getX(), at.getZ());
			if (on != null && on[0] < bestD && !w.spur()) {
				bestD = on[0];
				best = w;
			}
		}
		if (best == null) {
			c.getSource().sendFailure(Component.literal("No road near"));
			return 0;
		}
		Band b = s.traffic().newBand(best, RandomSource.create(), s.world().day());
		s.traffic().book().bands.add(b);
		s.world().markDirty();
		say(c.getSource(), "Bandits (" + b.strength() + ") camp at " + b.camp().toShortString() + " on the road from " + best.nameA() + " to " + best.nameB());
		return 1;
	}

	private static int build(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Roads.State s = state(c.getSource());
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		RoadBook.LiveRoad best = null;
		double bestD = Double.MAX_VALUE;
		for (RoadBook.LiveRoad r : s.live().book().live()) {
			if (r.finished() || r.way == null) {
				continue;
			}
			double d = Math.min(Math.hypot(r.ends[0] - at.getX(), r.ends[1] - at.getZ()), Math.hypot(r.ends[2] - at.getX(), r.ends[3] - at.getZ()));
			if (d < bestD) {
				bestD = d;
				best = r;
			}
		}
		if (best == null) {
			c.getSource().sendFailure(Component.literal("No unfinished live road"));
			return 0;
		}
		int work = IntegerArgumentType.getInteger(c, "work");
		s.live().advance(best, true, work / 2.0, s.world().settlement(best.a));
		s.live().advance(best, false, work / 2.0, s.world().settlement(best.b));
		s.world().markDirty();
		say(c.getSource(), String.format(Locale.ROOT, "The road from %s to %s: %d of %d plots built", best.names.get(0), best.names.get(1), best.fromA + best.fromB,
			best.plots()));
		return 1;
	}
}
