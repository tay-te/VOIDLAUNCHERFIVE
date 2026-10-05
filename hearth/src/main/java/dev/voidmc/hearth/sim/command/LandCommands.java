package dev.voidmc.hearth.sim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.land.LandState;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.trade.Caravans;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The land and trade commands (operator level 2), under {@code /hearth}:
 * <pre>
 *   /hearth settlement sites [id]     the territory's resource sites: kind, material, where, how much is left, who works it
 *   /hearth settlement economy [id]   prosperity, capacity, trade, the trades at work, stations, what is in store
 *   /hearth settlement survey [id]    survey the territory again (off the server thread)
 *   /hearth caravans                  caravans on the road
 *   /hearth underground               the realm under you and those near (Expanse Earth worlds)
 * </pre>
 */
public final class LandCommands {
	private static final SimpleCommandExceptionType NO_WORLD = new SimpleCommandExceptionType(Component.literal("Hearth does not run in this dimension"));
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.literal("No such settlement"));

	private LandCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("settlement").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("sites").executes(c -> sites(c, null))
					.then(Commands.argument("id", LongArgumentType.longArg()).executes(c -> sites(c, LongArgumentType.getLong(c, "id")))))
				.then(Commands.literal("economy").executes(c -> economy(c, null))
					.then(Commands.argument("id", LongArgumentType.longArg()).executes(c -> economy(c, LongArgumentType.getLong(c, "id")))))
				.then(Commands.literal("survey").executes(c -> survey(c, null))
					.then(Commands.argument("id", LongArgumentType.longArg()).executes(c -> survey(c, LongArgumentType.getLong(c, "id"))))))
			.then(Commands.literal("caravans").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(LandCommands::caravans))
			.then(Commands.literal("underground").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(LandCommands::underground)));
	}

	private static SimWorld world(CommandSourceStack source) throws CommandSyntaxException {
		SimWorld w = SimServer.world(source.getLevel());
		if (w == null) {
			throw NO_WORLD.create();
		}
		return w;
	}

	private static void say(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
	}

	private static SimSettlement pick(CommandContext<CommandSourceStack> c, SimWorld w, @Nullable Long id) throws CommandSyntaxException {
		SimSettlement s;
		if (id != null) {
			s = w.settlement(id);
		} else {
			Vec3 p = c.getSource().getPosition();
			s = w.settlementAt((int) p.x, (int) p.z, 64);
			if (s == null) {
				s = w.nearest(BlockPos.containing(p), 100_000);
			}
		}
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return s;
	}

	private static int sites(CommandContext<CommandSourceStack> c, @Nullable Long id) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, id);
		LandState land = s.land();
		say(c.getSource(), String.format(Locale.ROOT, "%s: territory %d blocks, %d sites%s", s.name(), land.territory(), land.sites().size(),
			land.isSurveying() ? " (surveying...)" : ""));
		for (SimSite site : land.sites()) {
			say(c.getSource(), String.format(Locale.ROOT, "  #%d %s%s at %d, %d (%d m): %.0f of %.0f left (%.0f%%), %s, density %.2f, %.1f worker-days yesterday%s%s",
				site.id(), site.kind().getSerializedName(), site.material() == null ? "" : " of " + site.material(), site.center().getX(),
				site.center().getZ(), (int) site.distanceTo(s.center()), site.remaining(), site.capacity(), site.fill() * 100,
				site.renewable() ? String.format(Locale.ROOT, "regrows %.1f a day", site.regen()) : "does not regrow", site.density(),
				site.workedYesterday(), site.plot() >= 0 ? ", worked by plot #" + site.plot() : "",
				site.fellingDebt() >= 1 ? String.format(Locale.ROOT, ", %.0f logs to fell in the world", site.fellingDebt()) : ""));
		}
		return land.sites().size();
	}

	private static int economy(CommandContext<CommandSourceStack> c, @Nullable Long id) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, id);
		LandState land = s.land();
		CommandSourceStack out = c.getSource();
		say(out, String.format(Locale.ROOT, "%s: prosperity %.2f (%s%s), feeds %s of %d souls; trade %.0f bits in, %.0f out (last ten days or so); treasury %d",
			s.name(), land.prosperity(), land.mood() > 0 ? "rising" : land.mood() < 0 ? "declining" : "steady", land.why().isEmpty() ? "" : ": " + land.why(),
			land.capacity() == Integer.MAX_VALUE ? "any number" : Integer.toString(land.capacity()), s.population(), land.exports(), land.imports(),
			s.treasury()));
		Map<JobType, Integer> jobs = new EnumMap<>(JobType.class);
		Map<Station, Integer> stations = new EnumMap<>(Station.class);
		for (Resident r : s.residents()) {
			jobs.merge(r.job, 1, Integer::sum);
			stations.merge(r.station, 1, Integer::sum);
		}
		say(out, "  trades: " + jobs);
		say(out, "  stations: " + stations);
		StringBuilder raw = new StringBuilder("  stores:");
		for (Goods.Kind kind : Goods.Kind.values()) {
			raw.append(" [").append(kind.name().toLowerCase(Locale.ROOT)).append(']');
			for (Goods g : Goods.VALUES) {
				if (g.kind() == kind && s.stockpile().get(g) > 0) {
					raw.append(' ').append(g.getSerializedName()).append(' ').append(s.stockpile().get(g)).append('/').append(s.stockpile().capacity(g));
				}
			}
		}
		say(out, raw.toString());
		if (!land.deserted().isEmpty()) {
			say(out, "  houses left empty: " + land.deserted());
		}
		int from = 0;
		int to = 0;
		for (Caravans.Caravan k : w.caravanTrade().active()) {
			from += k.from() == s.id() ? 1 : 0;
			to += k.to() == s.id() ? 1 : 0;
		}
		say(out, String.format(Locale.ROOT, "  caravans: %d on their way out, %d coming in", from, to));
		return 1;
	}

	private static int survey(CommandContext<CommandSourceStack> c, @Nullable Long id) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, id);
		w.survey(s);
		say(c.getSource(), "Surveying the land of " + s.name() + " (see /hearth settlement sites in a moment)");
		return 1;
	}

	private static int caravans(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		long now = w.clock();
		say(c.getSource(), w.caravanTrade().describe());
		for (Caravans.Caravan k : w.caravanTrade().active()) {
			SimSettlement from = w.settlement(k.from());
			SimSettlement to = w.settlement(k.to());
			StringBuilder cargo = new StringBuilder();
			for (int g = 0; g < k.cargo().length && g < Goods.VALUES.length; g++) {
				if (k.cargo()[g] > 0) {
					cargo.append(Goods.VALUES[g].getSerializedName()).append(' ').append(k.cargo()[g]).append(' ');
				}
			}
			BlockPos at = Caravans.positionAt(k, now);
			say(c.getSource(), String.format(Locale.ROOT, "  #%d %s -> %s: %s(%d bits), at %d, %d, arrives in %.1f days", k.id(),
				from == null ? "?" : from.name(), to == null ? "?" : to.name(), cargo, k.value(), at.getX(), at.getZ(),
				(k.arriveTick() - now) / (double) SimRules.DAY));
		}
		return w.caravanTrade().active().size();
	}

	private static int underground(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		UndergroundProbe u = w.underground();
		if (u == null) {
			say(c.getSource(), "This world has no cavern model (not an Expanse Earth world)");
			return 0;
		}
		BlockPos p = BlockPos.containing(c.getSource().getPosition());
		UndergroundProbe.Realm here = u.realm(p.getX(), p.getZ());
		say(c.getSource(), here == null ? "No realm under you" : describe("Under you", here));
		int floor = u.floorY(p.getX(), p.getZ(), p.getY());
		if (floor != Integer.MIN_VALUE) {
			say(c.getSource(), String.format(Locale.ROOT, "  the open space nearest your height: floor y %d, ceiling y %d", floor,
				u.ceilingY(p.getX(), p.getZ(), floor)));
		}
		for (UndergroundProbe.Realm r : u.realmsNear(p.getX(), p.getZ(), 1500)) {
			say(c.getSource(), describe("  ", r));
		}
		return 1;
	}

	private static String describe(String prefix, UndergroundProbe.Realm r) {
		return String.format(Locale.ROOT, "%s realm %s at %d, %d: radius %d, floor y %d, vault y %d", prefix, r.character(), r.center().getX(),
			r.center().getZ(), r.radius(), r.floorY(), r.ceilingY());
	}
}
