package dev.voidmc.hearth.repair.folk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.Inspector;
import dev.voidmc.hearth.repair.RepairConfig;
import dev.voidmc.hearth.repair.RepairLines;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Inspection rounds (docs/LIVING_WORLD.md §2): now and then (one round per settlement every {@code interval_ticks}) an
 * embodied builder with nothing to mend walks round a built plot — corner to corner, then in at the door — while
 * {@link Inspector} compares a few hundred of its blueprint cells with the world each second. What it finds goes into the
 * repair queue, and then the mending behaviour takes over.
 */
public final class InspectBehaviour implements FolkBehaviour {
	public static final Identifier ID = RepairModule.id("inspect");

	private static final class Walk {
		final Inspector.Round round;
		final List<BlockPos> waypoints;
		int waypoint;
		int ticks;
		int legTicks;

		Walk(Inspector.Round round, List<BlockPos> waypoints) {
			this.round = round;
			this.waypoints = waypoints;
		}
	}

	private final Inspector.Schedule schedule = new Inspector.Schedule();
	private final Map<UUID, Walk> walks = new HashMap<>();

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return RepairModule.config().inspection().priority();
	}

	@Override
	public boolean wants(FolkHandle f) {
		RepairConfig cfg = RepairModule.config();
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null || !cfg.inspection().enabled() || !cfg.tracking().enabled() || f.resident().job != JobType.BUILDER
			|| f.resident().age == AgeStage.CHILD || !f.awake()) {
			return false;
		}
		Settlement s = f.settlement();
		if (!s.embodied() || !(f.entity().level() instanceof ServerLevel level)
			|| !Reach.between(level, cfg.builders().workStart(), cfg.builders().workEnd())) {
			return false;
		}
		return this.schedule.isInspector(s.id(), f.resident().id) || this.schedule.due(s.id(), rt.now());
	}

	@Override
	public void start(FolkHandle f) {
		RepairRuntime rt = RepairRuntime.current();
		Settlement s = f.settlement();
		if (rt == null || !(f.entity().level() instanceof ServerLevel level)) {
			return;
		}
		HearthWorld world = HearthWorld.of(level);
		long now = rt.now();
		PlotView plot = world == null ? null : Inspector.choose(s, f.entity().blockPosition(), 96, this.schedule.lastSeen(s.id()), now);
		Inspector.Round round = plot == null ? null : Inspector.begin(rt, world, s, plot);
		if (plot == null || round == null) {
			// nothing to look at near: try again next interval
			this.schedule.finish(s.id(), f.resident().id, now, RepairModule.config().inspection().intervalTicks());
			return;
		}
		this.schedule.take(s.id(), f.resident().id);
		this.schedule.lastSeen(s.id()).put(plot.id(), now);
		this.walks.put(f.resident().id, new Walk(round, waypoints(plot.spec())));
	}

	/** Round the outside of the footprint, corner by corner. */
	private static List<BlockPos> waypoints(PlotSpec spec) {
		BlockPos o = spec.origin();
		int x0 = o.getX() - 1;
		int z0 = o.getZ() - 1;
		int x1 = o.getX() + spec.sizeX();
		int z1 = o.getZ() + spec.sizeZ();
		List<BlockPos> out = new ArrayList<>();
		out.add(new BlockPos(x0, o.getY(), z0));
		out.add(new BlockPos(x1, o.getY(), z0));
		out.add(new BlockPos(x1, o.getY(), z1));
		out.add(new BlockPos(x0, o.getY(), z1));
		out.add(o.offset(spec.sizeX() / 2, 0, spec.sizeZ() / 2));
		return out;
	}

	@Override
	public boolean tick(FolkHandle f) {
		RepairRuntime rt = RepairRuntime.current();
		Walk w = this.walks.get(f.resident().id);
		if (rt == null || w == null || !(f.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		HearthWorld world = HearthWorld.of(level);
		Settlement s = f.settlement();
		if (world == null) {
			return false;
		}
		RepairConfig.Inspection cfg = RepairModule.config().inspection();
		w.ticks++;
		Inspector.step(rt, level, world, s, w.round, cfg.positionsPerTick());

		// walk the round, looking at the walls as one goes
		BlockPos goal = w.waypoints.get(w.waypoint % w.waypoints.size());
		if (w.legTicks % 40 == 0) {
			f.moveTo(goal, 0.55);
		}
		w.legTicks++;
		if (w.legTicks > 10 && f.arrived() || w.legTicks > 200) {
			w.waypoint++;
			w.legTicks = 0;
		}
		if (w.ticks % 30 == 0) {
			PlotView plot = s.plot(w.round.plotId);
			if (plot != null) {
				f.lookAt(plot.spec().origin().offset(plot.spec().sizeX() / 2, 2, plot.spec().sizeZ() / 2));
			}
		}
		// a round is a walk round the building: the comparison runs ahead of the feet, the round ends at the last corner
		if ((!w.round.done() || w.waypoint < w.waypoints.size()) && w.ticks < cfg.maxTicks()) {
			return true;
		}
		int added = Inspector.commit(rt, level, world, s, w.round);
		if (added > 0) {
			PlotView plot = s.plot(w.round.plotId);
			if (plot != null) {
				f.say(Component.translatable("hearth.repair.say.inspect_found", Component.literal(RepairLines.building(plot.spec()))), null);
			}
		}
		return false;
	}

	@Override
	public void stop(FolkHandle f) {
		Walk w = this.walks.remove(f.resident().id);
		RepairRuntime rt = RepairRuntime.current();
		if (rt != null) {
			RepairConfig.Inspection cfg = RepairModule.config().inspection();
			boolean interrupted = w != null && !w.round.done() && w.ticks < cfg.maxTicks();
			if (interrupted) {
				// called away (to mend what it found, say): the plot is looked at again soon, from the start
				this.schedule.lastSeen(f.settlement().id()).remove(w.round.plotId);
			}
			this.schedule.finish(f.settlement().id(), f.resident().id, rt.now(), interrupted ? 200 : cfg.intervalTicks());
		}
		if (w != null) {
			f.stop();
			// called away mid-round (or the round ended): what was seen so far still counts
			if (rt != null && !w.round.committed() && w.round.checked() > 0 && f.entity().level() instanceof ServerLevel level) {
				HearthWorld world = HearthWorld.of(level);
				if (world != null) {
					Inspector.commit(rt, level, world, f.settlement(), w.round);
				}
			}
		}
		f.hold(ItemStack.EMPTY);
	}

	/** Which plots were looked at when, and when each settlement is due (the self-test steers it). */
	public Inspector.Schedule schedule() {
		return this.schedule;
	}

	public int inspecting() {
		return this.walks.size();
	}
}
