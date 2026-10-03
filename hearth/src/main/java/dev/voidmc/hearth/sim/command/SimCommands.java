package dev.voidmc.hearth.sim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.PlotFacts;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sim module's commands, under {@code /hearth} (operator level 2):
 * <pre>
 *   /hearth settlement list | info [id] | tp &lt;id&gt; | found [culture] | simulate &lt;id&gt; &lt;days&gt; | adopt
 *   /hearth atlas [radius]      a PNG map of regions, historic sites and live towns round you
 *   /hearth plot info           the plot you stand on
 *   /hearth sim stats           clock, applier and world generation timings
 * </pre>
 */
public final class SimCommands {
	private static final SimpleCommandExceptionType NO_WORLD = new SimpleCommandExceptionType(Component.literal("Hearth does not run in this dimension"));
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.literal("No such settlement"));

	private SimCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("settlement").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(SimCommands::list))
				.then(Commands.literal("info").executes(c -> info(c, null))
					.then(Commands.argument("id", LongArgumentType.longArg()).executes(c -> info(c, LongArgumentType.getLong(c, "id")))))
				.then(Commands.literal("tp").then(Commands.argument("id", LongArgumentType.longArg()).executes(SimCommands::tp)))
				.then(Commands.literal("found").executes(c -> found(c, null))
					.then(Commands.argument("culture", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Culture.values()).map(Culture::getSerializedName), b))
						.executes(c -> found(c, StringArgumentType.getString(c, "culture")))))
				.then(Commands.literal("simulate").then(Commands.argument("id", LongArgumentType.longArg())
					.then(Commands.argument("days", IntegerArgumentType.integer(1, 10000)).executes(SimCommands::simulate))))
				.then(Commands.literal("adopt").executes(SimCommands::adopt)))
			.then(Commands.literal("atlas").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> atlas(c, 2048))
				.then(Commands.argument("radius", IntegerArgumentType.integer(256, 16384)).executes(c -> atlas(c, IntegerArgumentType.getInteger(c, "radius")))))
			.then(Commands.literal("plot").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("info").executes(SimCommands::plotInfo)))
			.then(Commands.literal("sim").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("stats").executes(SimCommands::stats))));
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

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		Vec3 at = c.getSource().getPosition();
		List<SimSettlement> all = new ArrayList<>(w.settlements());
		all.sort(Comparator.comparingDouble(s -> s.center().distToCenterSqr(at)));
		say(c.getSource(), all.size() + " settlements, " + w.index().adoptedCount() + " historic regions adopted");
		for (SimSettlement s : all) {
			say(c.getSource(), String.format(Locale.ROOT, "  #%d %s — %s %s, %d souls, %d/%d plots built, %d m away%s", s.id(), s.name(),
				s.culture().getSerializedName(), s.tier().getSerializedName(), s.population(), s.plotCounts().getOrDefault(PlotState.BUILT, 0),
				s.plots().size(), (int) Math.sqrt(s.center().distToCenterSqr(at)), s.embodied() ? " (embodied)" : ""));
		}
		return all.size();
	}

	private static SimSettlement pick(CommandContext<CommandSourceStack> c, SimWorld w, @Nullable Long id) throws CommandSyntaxException {
		SimSettlement s;
		if (id != null) {
			s = w.settlement(id);
		} else {
			Vec3 p = c.getSource().getPosition();
			s = w.settlementAt((int) p.x, (int) p.z, 64);
			if (s == null) {
				s = w.nearest(net.minecraft.core.BlockPos.containing(p), 100_000);
			}
		}
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return s;
	}

	private static int info(CommandContext<CommandSourceStack> c, @Nullable Long id) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, id);
		CommandSourceStack out = c.getSource();
		int[] ages = SimWorld.ages(s);
		say(out, String.format(Locale.ROOT, "#%d %s — %s %s at %s, founded day %d%s", s.id(), s.name(), s.culture().getSerializedName(),
			s.tier().getSerializedName(), s.center().toShortString(), s.foundedDay(), Atlas.isHistoric(s.id()) ? " (historic)" : ""));
		say(out, String.format(Locale.ROOT, "  %d souls (%d children, %d adults, %d elders), %d beds, %d embodied; treasury %d bits", s.population(),
			ages[0], ages[1], ages[2], s.beds(), s.embodiedResidentCount(), s.treasury()));
		Map<JobType, Integer> jobs = new EnumMap<>(JobType.class);
		for (Resident r : s.residents()) {
			if (r.job != JobType.NONE) {
				jobs.merge(r.job, 1, Integer::sum);
			}
		}
		say(out, "  trades: " + jobs);
		StringBuilder stock = new StringBuilder("  stores (cap " + s.stockpile().capacity(Goods.FOOD) + "):");
		for (Goods g : Goods.VALUES) {
			stock.append(' ').append(g.getSerializedName()).append(' ').append(s.stockpile().get(g));
		}
		say(out, stock.toString());
		say(out, "  plots: " + s.plotCounts() + "; sim at day " + Math.floorDiv(s.simTick(), SimRules.DAY) + " (world day " + w.day() + ", "
			+ (w.simNow(s) - s.simTick()) / 1000 + " h behind)" + (s.embodied() ? ", embodied" : ""));
		for (SimPlot p : s.plots()) {
			if (p.isActive()) {
				PlotFacts f = s.facts(p);
				say(out, String.format(Locale.ROOT, "  building #%d %s: stage %d/%d (%s) step %d/%d%s — %s", p.id(), p.spec().archetype().getPath(),
					p.stage(), f.stageCount(), p.stage() < f.stageCount() ? "" + f.stepCount()[p.stage()] + " steps" : "-", p.step(),
					p.stage() < f.stageCount() ? f.stepCount()[p.stage()] : 0, p.stalledOn() != null ? ", short of " + p.stalledOn().getSerializedName()
						: "", p.reason()));
			}
		}
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, LongArgumentType.getLong(c, "id"));
		var player = c.getSource().getPlayerOrException();
		int y = w.probe().groundY(s.center().getX(), s.center().getZ()) + 6;
		player.teleportTo(c.getSource().getLevel(), s.center().getX() + 0.5, y, s.center().getZ() + 0.5, java.util.Set.of(), player.getYRot(),
			player.getXRot(), true);
		say(c.getSource(), "To " + s.name());
		return 1;
	}

	private static int found(CommandContext<CommandSourceStack> c, @Nullable String cultureName) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		Culture culture = null;
		if (cultureName != null) {
			for (Culture k : Culture.values()) {
				if (k.getSerializedName().equals(cultureName)) {
					culture = k;
				}
			}
		}
		net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
		String why = w.whyNotFound(at, 200);
		if (why != null) {
			c.getSource().sendFailure(Component.literal("Cannot found a settlement here: " + why));
			return 0;
		}
		SimSettlement s = w.found(at, culture, null);
		say(c.getSource(), "Founded " + s.name() + " (#" + s.id() + ", " + s.culture().getSerializedName() + ") with " + s.population() + " settlers and "
			+ s.plots().size() + " plots planned");
		return 1;
	}

	private static int simulate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimSettlement s = pick(c, w, LongArgumentType.getLong(c, "id"));
		int days = IntegerArgumentType.getInteger(c, "days");
		int before = s.population();
		int builtBefore = s.plotCounts().getOrDefault(PlotState.BUILT, 0);
		long nanos = w.simulate(s, days);
		say(c.getSource(), String.format(Locale.ROOT, "%s: %d days in %.1f ms — %d → %d souls, %d → %d buildings, %s", s.name(), days, nanos / 1e6, before,
			s.population(), builtBefore, s.plotCounts().getOrDefault(PlotState.BUILT, 0), s.tier().getSerializedName()));
		return 1;
	}

	private static int adopt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		Vec3 p = c.getSource().getPosition();
		RegionPlan plan = w.atlas().plan(Atlas.region((int) p.x), Atlas.region((int) p.z));
		if (!plan.settled()) {
			c.getSource().sendFailure(Component.literal("Nobody settled this region"));
			return 0;
		}
		SimSettlement s = w.adopt(plan);
		say(c.getSource(), s == null ? "Could not adopt" : "Adopted " + s.name() + " (#" + s.id() + ") at " + s.center().toShortString());
		return 1;
	}

	private static int atlas(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		int x = (int) source.getPosition().x;
		int z = (int) source.getPosition().z;
		int step = Math.max(2, radius / 384);
		List<AtlasMap.LiveTown> live = new ArrayList<>();
		for (SimSettlement s : w.settlements()) {
			live.add(AtlasMap.LiveTown.of(s));
		}
		source.sendSuccess(() -> Component.literal("Charting the settlements of " + radius * 2 + " blocks square…"), false);
		Thread t = new Thread(() -> {
			try {
				AtlasMap.Result r = AtlasMap.render(w.atlas(), w.probe().rough(), level.getServer().getWorldPath(LevelResource.ROOT).resolve("hearth_atlas"), x, z,
					radius, step, live);
				level.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
					"Atlas: " + r.sites() + " settlements in " + r.regions() + " regions, " + r.millis() + " ms → " + r.image()), false));
			} catch (Exception e) {
				level.getServer().execute(() -> source.sendFailure(Component.literal("Atlas failed: " + e)));
			}
		}, "hearth-atlas-map");
		t.setDaemon(true);
		t.start();
		return 1;
	}

	private static int plotInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		Vec3 p = c.getSource().getPosition();
		int x = (int) Math.floor(p.x);
		int z = (int) Math.floor(p.z);
		for (var ref : w.spatial().refs(net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4))) {
			SimSettlement s = ref.settlement();
			SimPlot plot = s.plot(ref.plotId());
			if (plot == null) {
				continue;
			}
			var o = plot.spec().origin();
			if (x >= o.getX() && x < o.getX() + plot.spec().sizeX() && z >= o.getZ() && z < o.getZ() + plot.spec().sizeZ()) {
				PlotFacts f = s.facts(plot);
				say(c.getSource(), String.format(Locale.ROOT, "%s plot #%d: %s (%s), %dx%d facing %s, floor y %d, tier %d", s.name(), plot.id(),
					plot.spec().archetype(), plot.spec().style(), plot.spec().sizeX(), plot.spec().sizeZ(), plot.spec().facing().getSerializedName(),
					plot.spec().floorY(), plot.spec().tier()));
				say(c.getSource(), String.format(Locale.ROOT, "  %s, stage %d/%d step %d, applicable %s, %d beds%s%s", plot.state().getSerializedName(),
					plot.stage(), f.stageCount(), plot.step(), Cursor.toString(s.applicable(plot)), f.beds(),
					plot.owner() != null ? ", owned by " + plot.owner() : "", plot.reason().isEmpty() ? "" : " — " + plot.reason()));
				return 1;
			}
		}
		c.getSource().sendFailure(Component.literal("No plot here"));
		return 0;
	}

	private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = world(c.getSource());
		SimServer s = SimServer.current();
		if (s == null) {
			throw NO_WORLD.create();
		}
		for (String line : Report.lines(s, w)) {
			say(c.getSource(), line);
		}
		return 1;
	}
}
