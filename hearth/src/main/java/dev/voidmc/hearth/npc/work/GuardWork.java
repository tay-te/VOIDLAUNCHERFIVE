package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The watch: guards walk a round of the town's guard posts and gates (or its edges, where it has none), stand a while
 * at each, and go for any monster that comes near — and to wherever the alarm was raised. The guard captain (the senior
 * guard, {@link Office#CAPTAIN}) drills at the guardhouse in the early morning, makes quick inspections of the posts,
 * and reports to the lord's hall (or the town hall) once in the morning.
 */
final class GuardWork implements WorkBehaviour {
	private int post;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		LivingEntity threat = brain.threat();
		if (threat != null && threat.isAlive()) {
			return new Fight(threat);
		}
		BlockPos alarm = brain.town.alarmAt();
		if (brain.town.alarmed(level.getGameTime()) && alarm != null) {
			return new Tasks.Sequence("answering the alarm", new Tasks.GoTo(alarm, 3.0, 0.8, 600),
				new Tasks.Linger(null, alarm, FolkPose.NONE, 200, sword(), shield(), null, "on guard"));
		}
		List<BlockPos> round = round(brain, level);
		if (brain.town.office(brain.settlement, brain.resident) == Office.CAPTAIN) {
			Task t = captain(brain, level, round);
			if (t != null) {
				return t;
			}
		}
		BlockPos target = round.get(this.post++ % round.size());
		return new Tasks.Linger(target, null, FolkPose.NONE, 300 + brain.random.nextInt(300), sword(), shield(), null, "keeping watch");
	}

	private int captainBout;
	private long reportedDay = Long.MIN_VALUE;

	/** The captain's day: drill at dawn, inspections, the morning report. Null to keep an ordinary watch. */
	private @Nullable Task captain(FolkBrain brain, ServerLevel level, List<BlockPos> round) {
		this.captainBout++;
		int minute = dev.voidmc.hearth.npc.NpcTime.minuteOfDay(level);
		long day = dev.voidmc.hearth.npc.NpcTime.today();
		if (minute < 12 * 60 && day != this.reportedDay) {
			Poi seat = OfficialWork.first(brain, level, PoiType.THRONE);
			if (seat != null) {
				this.reportedDay = day;
				return OfficialWork.attend(seat, sword(), "reporting to the lord");
			}
		}
		if (minute < 9 * 60 && this.captainBout % 2 == 1) {
			// drill: sword strokes at a post of the guardhouse, or the first post of the round
			BlockPos at = drillGround(brain, level, round);
			return new Tasks.Linger(at, null, FolkPose.HAMMER, 300, sword(), shield(),
				Tasks.every(14, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, null, true), "drilling");
		}
		if (this.captainBout % 3 == 0) {
			// a quick inspection of three posts in a row
			Task[] stops = new Task[Math.min(3, round.size())];
			for (int i = 0; i < stops.length; i++) {
				BlockPos p = round.get((this.post++) % round.size());
				stops[i] = new Tasks.Linger(p, null, FolkPose.TALK, 80, sword(), shield(), null, "inspecting the posts");
			}
			return new Tasks.Sequence("inspecting the posts", stops);
		}
		return null;
	}

	private static BlockPos drillGround(FolkBrain brain, ServerLevel level, List<BlockPos> round) {
		dev.voidmc.hearth.api.PlotView hall = OfficialWork.plot(brain, "civic/guardhouse");
		if (hall != null) {
			for (Poi p : brain.town.places.plot(level, brain.settlement, hall)) {
				if (p.type() == PoiType.GUARD_POST || p.type() == PoiType.JOB_SITE) {
					return p.pos();
				}
			}
		}
		return round.get(0);
	}

	private static ItemStack sword() {
		return new ItemStack(Items.IRON_SWORD);
	}

	private static ItemStack shield() {
		return new ItemStack(Items.SHIELD);
	}

	private static List<BlockPos> round(FolkBrain brain, ServerLevel level) {
		List<BlockPos> out = new ArrayList<>();
		for (Poi p : brain.town.places.all(level, brain.settlement, PoiType.GUARD_POST)) {
			out.add(p.pos());
		}
		for (Poi p : brain.town.places.all(level, brain.settlement, PoiType.GATE)) {
			out.add(p.pos());
		}
		if (out.isEmpty()) {
			// No posts: the four quarters of the town and its heart.
			BoundingBox b = brain.settlement.bounds();
			BlockPos c = brain.settlement.center();
			int rx = Math.min(b.getXSpan() / 2, 40);
			int rz = Math.min(b.getZSpan() / 2, 40);
			for (int[] d : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
				BlockPos p = c.offset(d[0] * rx, 0, d[1] * rz);
				if (level.isLoaded(p)) {
					out.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p));
				}
			}
			out.add(c);
		}
		return out;
	}

	/** Closes with a monster and strikes it until it is dead or gone. */
	static final class Fight implements Task {
		private final LivingEntity target;
		private int cooldown;
		private int repath;
		private int timeout = 20 * 40;

		Fight(LivingEntity target) {
			this.target = target;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			Folk me = brain.folk;
			this.timeout -= elapsed;
			if (!this.target.isAlive() || this.target.isRemoved() || this.timeout < 0 || me.distanceToSqr(this.target) > 32 * 32) {
				return Status.DONE;
			}
			brain.hold(sword(), shield());
			me.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
			this.cooldown -= elapsed;
			this.repath -= elapsed;
			double reach = me.getBbWidth() * 2.0F * me.getBbWidth() * 2.0F + this.target.getBbWidth() + 1.5;
			if (me.distanceToSqr(this.target) <= reach) {
				me.getNavigation().stop();
				if (this.cooldown <= 0) {
					me.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					me.doHurtTarget(level, this.target);
					this.cooldown = 20;
				}
			} else if (this.repath <= 0) {
				me.getNavigation().moveTo(this.target, 0.85);
				this.repath = 10;
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.getNavigation().stop();
		}

		@Override
		public boolean urgent() {
			return true;
		}

		@Override
		public String describe() {
			return "fighting " + this.target.getName().getString();
		}
	}
}
