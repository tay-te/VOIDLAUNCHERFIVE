package dev.voidmc.hearth.repair.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import dev.voidmc.hearth.repair.Damage;
import dev.voidmc.hearth.repair.FireWatch;
import dev.voidmc.hearth.repair.Inspector;
import dev.voidmc.hearth.repair.OwnerRepairs;
import dev.voidmc.hearth.repair.PlotLocator;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairQueue;
import dev.voidmc.hearth.repair.RepairRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hearth repair ...}: the state of a settlement's repairs, a forced inspection, mending everything now, the
 * module's timings, the dev self-test (game masters); and, for every player, the bill for mending their own property and
 * paying it.
 */
public final class RepairCommands {
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.translatable("hearth.repair.command.no_settlement"));
	private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("hearth.repair.command.not_running"));
	private static final SimpleCommandExceptionType NO_PLOT = new SimpleCommandExceptionType(Component.translatable("hearth.repair.command.no_plot"));

	private RepairCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("repair")
				.then(Commands.literal("quote").executes(RepairCommands::quote))
				.then(Commands.literal("pay").executes(RepairCommands::pay))
				.then(Commands.literal("status")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(RepairCommands::status))
				.then(Commands.literal("list")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> list(c, 10))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, 200)).executes(c -> list(c, IntegerArgumentType.getInteger(c, "count")))))
				.then(Commands.literal("inspect")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(RepairCommands::inspect))
				.then(Commands.literal("mend")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> mend(c, false))
					.then(Commands.argument("free", BoolArgumentType.bool()).executes(c -> mend(c, BoolArgumentType.getBool(c, "free")))))
				.then(Commands.literal("stats")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(RepairCommands::stats))
				.then(Commands.literal("selftest")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(RepairCommands::selftest))));
	}

	private record Here(RepairRuntime rt, ServerLevel level, HearthWorld world, Settlement settlement, BlockPos pos) {
	}

	private static Here here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null) {
			throw NOT_RUNNING.create();
		}
		ServerLevel level = c.getSource().getLevel();
		HearthWorld world = HearthWorld.of(level);
		BlockPos pos = BlockPos.containing(c.getSource().getPosition());
		Settlement s = world == null ? null : world.settlementAt(pos);
		if (s == null && world != null) {
			s = world.nearest(pos, 512);
		}
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return new Here(rt, level, world, s, pos);
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Here h = here(c);
		RepairQueue q = h.settlement.data(RepairModule.QUEUE);
		long now = h.rt.now();
		int claimTicks = RepairModule.config().queue().claimTicks();
		Map<Cause, Integer> byCause = new EnumMap<>(Cause.class);
		int mended = 0;
		int payment = 0;
		int claimed = 0;
		int deferred = 0;
		int litter = 0;
		for (Damage d : q.all()) {
			byCause.merge(d.cause, 1, Integer::sum);
			mended += d.has(Damage.MENDED) ? 1 : 0;
			payment += d.awaitingPayment() ? 1 : 0;
			claimed += d.claimed(now, claimTicks) ? 1 : 0;
			deferred += d.deferred(now) ? 1 : 0;
			litter += d.has(Damage.LITTER) ? 1 : 0;
		}
		String fire = "";
		FireWatch.Episode e = h.rt.fires.episode(h.settlement.id());
		if (e != null) {
			fire = ", FIRE burning: " + e.flames() + " flames known, " + e.burned() + " blocks lost, brigade of " + e.brigadeSize();
		}
		String line = String.format(java.util.Locale.ROOT,
			"%s (#%d): %d damaged %s, %d litter; %d open, %d claimed, %d mended awaiting their chunk, %d held for owners' coin, %d waiting (goods or support); %d scaffold blocks up; behaviours %s%s",
			h.settlement.name(), h.settlement.id(), q.size(), byCause, litter, q.openCount(now, claimTicks), claimed, mended, payment, deferred,
			q.scaffoldCount(), RepairModule.behavioursRegistered() ? "registered (mending " + RepairModule.MEND.working() + ", inspecting "
				+ RepairModule.INSPECT.inspecting() + ", brigade " + RepairModule.BRIGADE.running() + ")" : "not registered (no NPC module)", fire);
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		return q.size();
	}

	private static int list(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
		Here h = here(c);
		RepairQueue q = h.settlement.data(RepairModule.QUEUE);
		List<Damage> all = new ArrayList<>(q.all());
		all.sort(Comparator.comparingDouble(d -> d.blockPos().distSqr(h.pos)));
		PlotLocator.Source source = PlotLocator.services(h.world);
		for (Damage d : all.subList(0, Math.min(count, all.size()))) {
			PlotView plot = h.settlement.plot(d.plot);
			var expected = plot == null ? null : h.rt.locator.cellOf(h.settlement, source, plot, d.pos);
			String line = String.format(java.util.Locale.ROOT, "%s %s plot %d (%s) stage %d: %s%s", d.blockPos().toShortString(), d.cause.name().toLowerCase(),
				d.plot, plot == null ? "?" : plot.spec().archetype().getPath(), d.stage, expected == null ? "?" : expected.getBlock().getName().getString(),
				d.has(Damage.LITTER) ? " (litter)" : d.has(Damage.MENDED) ? " (mended)" : d.awaitingPayment() ? " (owner to pay)" : "");
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return all.size();
	}

	private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Here h = here(c);
		PlotLocator.Hit hit = h.rt.locator.locate(h.settlement, PlotLocator.services(h.world), h.pos, h.rt.now());
		if (hit == null) {
			hit = h.rt.locator.locate(h.settlement, PlotLocator.services(h.world), h.pos.below(), h.rt.now());
		}
		if (hit == null) {
			throw NO_PLOT.create();
		}
		PlotView plot = hit.plot();
		int[] counts = new int[3];
		long t0 = System.nanoTime();
		int added = Inspector.inspectNow(h.rt, h.level, h.world, h.settlement, plot, counts);
		double ms = (System.nanoTime() - t0) / 1e6;
		String line = String.format(java.util.Locale.ROOT, "plot %d (%s): %d cells, %d checked, %d missing, %s in %.2f ms", plot.id(),
			plot.spec().archetype().getPath(), counts[0], counts[1], counts[2], added < 0 ? "taken as not yet written" : added + " queued", ms);
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		return Math.max(0, added);
	}

	private static int mend(CommandContext<CommandSourceStack> c, boolean free) throws CommandSyntaxException {
		Here h = here(c);
		int n = h.rt.abstracts.mendAll(h.world, h.settlement, free);
		c.getSource().sendSuccess(() -> Component.literal(h.settlement.name() + ": " + n + " blocks mended in the record; they go into the world as their chunks load"),
			true);
		return n;
	}

	private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null) {
			throw NOT_RUNNING.create();
		}
		for (String line : lines(rt)) {
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	public static List<String> lines(RepairRuntime rt) {
		return List.of("index: " + rt.locator.stats(), "damage: " + rt.tracker.stats(), "abstract: " + rt.abstracts.stats(), "fires: " + rt.fires.stats(),
			"law: " + rt.vandalism.stats());
	}

	private static int quote(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Here h = here(c);
		ServerPlayer player = c.getSource().getPlayerOrException();
		OwnerRepairs.Quote q = OwnerRepairs.quote(h.rt, h.world, h.settlement, player.getUUID());
		if (q.empty()) {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.repair.command.nothing_owed", h.settlement.name()), false);
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("hearth.repair.command.quote", q.blocks(), h.settlement.name(), price(q.bits())), false);
		return q.blocks();
	}

	private static int pay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Here h = here(c);
		ServerPlayer player = c.getSource().getPlayerOrException();
		OwnerRepairs.Quote q = OwnerRepairs.quote(h.rt, h.world, h.settlement, player.getUUID());
		if (q.empty()) {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.repair.command.nothing_owed", h.settlement.name()), false);
			return 0;
		}
		if (!OwnerRepairs.pay(h.rt, h.world, h.settlement, player)) {
			c.getSource().sendFailure(Component.translatable("hearth.repair.dialogue.cannot_pay"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("hearth.repair.command.paid", price(q.bits()), q.blocks()), false);
		return q.blocks();
	}

	private static Component price(long bits) {
		EconomyService e = HearthServices.economy();
		return e != null ? e.format(bits) : Component.literal(Long.toString(bits));
	}

	private static int selftest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Here h = here(c);
		@Nullable String why = SelfTest.start(h.rt, h.level, h.world, h.settlement);
		if (why != null) {
			c.getSource().sendFailure(Component.literal(why));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("repair self-test started in " + h.settlement.name() + "; see the log ([hearth repair])"), false);
		return 1;
	}
}
