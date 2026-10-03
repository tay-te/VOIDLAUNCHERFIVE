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
