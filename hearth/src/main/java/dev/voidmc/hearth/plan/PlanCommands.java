package dev.voidmc.hearth.plan;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /hearth plan info}: what the planner knows about the nearest settlement — its layout and signature, the land
 * it read, its roads, districts and the projects it has decided on but not handed out yet.
 */
final class PlanCommands {
	private PlanCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth")
			.then(Commands.literal("plan")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("info").executes(PlanCommands::info))));
	}

	private static int info(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		HearthWorld world = HearthWorld.of(src.getLevel());
		if (world == null) {
			src.sendFailure(Component.translatable("hearth.plan.command.no_world"));
			return 0;
		}
		Settlement s = world.nearest(BlockPos.containing(src.getPosition()), 512);
		if (s == null) {
			src.sendFailure(Component.translatable("hearth.plan.command.none_near"));
			return 0;
		}
		PlanState st = s.data(PlanModule.STATE);
		src.sendSuccess(() -> Component.translatable("hearth.plan.command.header", s.name(), Component.translatable(s.culture().translationKey()),
			Component.translatable(st.layout.translationKey()), Component.translatable(st.signature.translationKey())), false);
		int built = st.graph.builtLength();
		int edges = 0;
		int stairs = 0;
		int bridges = 0;
		for (RoadEdge e : st.graph.edges()) {
			if (!e.emits()) {
				continue;
			}
			edges++;
			for (int r = 0; r < e.runCount(); r++) {
				if (e.runType(r) == RoadEdge.STAIRS) {
					stairs++;
				} else if (e.runType(r) == RoadEdge.BRIDGE || e.runType(r) == RoadEdge.BOARDWALK) {
					bridges++;
				}
			}
		}
		int fe = edges;
		int fs = stairs;
		int fb = bridges;
		src.sendSuccess(() -> Component.translatable("hearth.plan.command.roads", fe, built, fs, fb), false);
		if (!st.districts.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (District d : st.districts) {
				sb.append(d.kind().getSerializedName()).append(' ');
			}
			src.sendSuccess(() -> Component.translatable("hearth.plan.command.districts", sb.toString().trim()), false);
		}
		// the living world: who lives where, the land worked, the lord, the deep
		int[] folk = new int[dev.voidmc.hearth.api.Station.values().length];
		for (dev.voidmc.hearth.api.Resident r : s.residents()) {
			folk[dev.voidmc.hearth.plan.society.Stations.of(r, s.tier()).ordinal()]++;
		}
		StringBuilder stations = new StringBuilder();
		for (dev.voidmc.hearth.api.Station station : dev.voidmc.hearth.api.Station.values()) {
			if (folk[station.ordinal()] > 0) {
				stations.append(station.getSerializedName()).append(' ').append(folk[station.ordinal()]).append("  ");
			}
		}
		src.sendSuccess(() -> Component.translatable("hearth.plan.command.stations", stations.toString().trim()), false);
		int worked = 0;
		for (var p : s.plots()) {
			if (dev.voidmc.hearth.plan.resource.Extraction.kind(p.spec().archetype()) != null) {
				worked++;
			}
		}
		int workings = worked;
		int capacity = dev.voidmc.hearth.plan.resource.Territory.estimateCapacity(s.sites(), PlanModule.planner().config().living());
		src.sendSuccess(() -> Component.translatable("hearth.plan.command.land", s.sites().size(), workings, capacity > 0 ? capacity : "-"), false);
		if (st.castleHill != null) {
			src.sendSuccess(() -> Component.translatable("hearth.plan.command.castle", st.castleHill[0], st.castleHill[1]), false);
		}
		if (st.deep != null) {
			int deepPlots = 0;
			for (var p : s.plots()) {
				if (dev.voidmc.hearth.plan.deep.LevelView.level(p.spec()) == 1) {
					deepPlots++;
				}
			}
			int dp = deepPlots;
			src.sendSuccess(() -> Component.translatable("hearth.plan.command.deep", st.deep.heartX, st.deep.heartY, st.deep.heartZ, dp), false);
		}
		src.sendSuccess(() -> Component.translatable("hearth.plan.command.queue", st.queue.size()), false);
		int n = 0;
		for (QueuedProject q : st.queue) {
			if (n++ >= 5) {
				break;
			}
			src.sendSuccess(() -> Component.literal(" - " + q.reason() + " (" + q.spec().archetype() + ")"), false);
		}
		return 1;
	}
}
