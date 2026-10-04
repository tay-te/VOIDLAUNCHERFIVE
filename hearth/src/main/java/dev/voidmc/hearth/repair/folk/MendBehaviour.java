package dev.voidmc.hearth.repair.folk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.Damage;
import dev.voidmc.hearth.repair.DamageRules;
import dev.voidmc.hearth.repair.PlotLocator;
import dev.voidmc.hearth.repair.RepairConfig;
import dev.voidmc.hearth.repair.RepairLines;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairQueue;
import dev.voidmc.hearth.repair.RepairRuntime;
import dev.voidmc.hearth.repair.RepairServiceImpl;
import dev.voidmc.hearth.repair.Restorer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builders mend (docs/LIVING_WORLD.md §2): an embodied BUILDER in working hours, with damage in the settlement's queue,
 * leaves the routine (priority 150, ahead of new construction, which is part of the routine) to put it back. They claim
 * the block nearest them through the {@link dev.voidmc.hearth.api.RepairService}, walk to it (up a scaffold if it is
 * high), say what they think of it ("some fool smashed the chapel window"), place exactly the blueprint's block, and take
 * the next one close by until nothing is left near.
 */
public final class MendBehaviour implements FolkBehaviour {
	public static final Identifier ID = RepairModule.id("mend");

	private static final class Job {
		@Nullable Damage claim;
		@Nullable BlockPos stand;
		@Nullable Column column;
		@Nullable BlockPos noScaffold;
		int walkTicks;
		int cooldown;
		int blocks;
	}

	private final Map<UUID, Job> jobs = new HashMap<>();
	/** When each builder last remarked on the damage (game time). */
	private final Map<UUID, Long> spoke = new HashMap<>();
	/** Per settlement: the tick its open count was taken, and the count (wants() is asked often and must be cheap). */
	private final Map<Long, long[]> openCache = new HashMap<>();

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return RepairModule.config().builders().priority();
	}

	@Override
	public boolean wants(FolkHandle f) {
		RepairConfig cfg = RepairModule.config();
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null || !cfg.builders().enabled() || !cfg.tracking().enabled() || f.resident().job != JobType.BUILDER
			|| f.resident().age == AgeStage.CHILD || !f.awake()) {
			return false;
		}
		Settlement s = f.settlement();
		if (!s.embodied() || !(f.entity().level() instanceof ServerLevel level) || !Reach.between(level, cfg.builders().workStart(), cfg.builders().workEnd())) {
			return false;
		}
		return open(rt, s) > 0;
	}

	private int open(RepairRuntime rt, Settlement s) {
		long now = rt.now();
		long[] c = this.openCache.get(s.id());
		if (c == null || now - c[0] >= 20) {
			RepairQueue q = s.data(RepairModule.QUEUE);
			c = new long[]{now, q.isEmpty() ? 0 : q.openCount(now, RepairModule.config().queue().claimTicks())};
			this.openCache.put(s.id(), c);
		}
		return (int) c[1];
	}

	@Override
	public void start(FolkHandle f) {
		this.jobs.put(f.resident().id, new Job());
	}

	@Override
	public boolean tick(FolkHandle f) {
		RepairRuntime rt = RepairRuntime.current();
		Job job = this.jobs.get(f.resident().id);
		if (rt == null || job == null || !(f.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		Settlement s = f.settlement();
		HearthWorld world = HearthWorld.of(level);
		if (world == null) {
			return false;
		}
		RepairConfig cfg = RepairModule.config();
		RepairQueue q = s.data(RepairModule.QUEUE);
		job.cooldown--;

		Column col = job.column;
		if (col != null) {
			if (col.done()) {
				if (col.gaveUp() && job.claim != null) {
					job.noScaffold = job.claim.blockPos();
				}
				job.column = null;
			} else if (col.busy()) {
				col.tick(f, level, q);
				return true;
			}
		}

		Damage d = job.claim;
		if (d != null && (q.get(d.pos) != d || !f.resident().id.equals(d.claimedBy()))) {
			// mended by someone else meanwhile, or the claim lapsed
			d = null;
			job.claim = null;
		}
		if (d == null) {
			if (job.column != null && job.column.hasBlocks()) {
				job.column.dismantle();
				return true;
			}
			double range = job.blocks == 0 ? cfg.builders().radius() : cfg.builders().chainRadius();
			d = RepairServiceImpl.claimFor(rt, world, s, f.resident().id, f.entity().blockPosition(), range);
			if (d == null) {
				return false;
			}
			job.claim = d;
			job.stand = null;
			job.walkTicks = 0;
			speak(f, s, d, level);
		}
		BlockPos pos = d.blockPos();
		BlockState expected = d.expected();
		if (expected == null || !level.isLoaded(pos)) {
			d.release();
			job.claim = null;
			return true;
		}
		long now = rt.now();
		d.touch(now);
		boolean litter = d.has(Damage.LITTER);
		BlockState current = level.getBlockState(pos);
		if (litter ? current.isAir() || current.canBeReplaced() : DamageRules.sameBlock(expected, current)) {
			RepairServiceImpl.complete(rt, world, s, d.pos, true, false);
			job.claim = null;
			return true;
		}
		f.hold(litter ? new ItemStack(Items.IRON_PICKAXE) : new ItemStack(expected.getBlock().asItem()));
		PathfinderMob folk = f.entity();

		if (Reach.canReach(folk, pos) && !Reach.inside(folk, pos, expected)) {
			f.stop();
			f.lookAt(pos);
			if (job.cooldown <= 0) {
				place(rt, f, level, world, s, d, current);
				job.cooldown = cfg.builders().stepTicks() - cfg.builders().stepTicks() / 4 + folk.getRandom().nextInt(Math.max(1, cfg.builders().stepTicks() / 2));
				job.blocks++;
				job.claim = null;
			}
			return true;
		}
		if (job.column != null && job.column.atTop()) {
			// the next block is out of reach of this column: down and away
			job.column.dismantle();
			return true;
		}
		double above = pos.getY() - folk.getY();
		PlotView plot = s.plot(d.plot);
		if (above > 3.0 && cfg.builders().scaffolding() && plot != null && !pos.equals(job.noScaffold) && job.column == null) {
			Column c = Column.plan(level, folk, plot.spec(), pos);
			if (c != null) {
				job.column = c;
				return true;
			}
		}
		if (job.stand == null) {
			job.stand = Reach.standFor(level, pos, folk);
		}
		BlockPos goal = job.stand != null ? job.stand : pos;
		boolean path = true;
		if (job.walkTicks % 40 == 0 && !f.arrived()) {
			path = f.moveTo(goal, 0.7);
		}
		job.walkTicks++;
		if (!path || f.arrived() && !Reach.canReach(folk, pos) || job.walkTicks > 400) {
			// out of reach from anywhere one can stand: after a fair try, place it from close by (the builder stretches),
			// or leave it for later; a block that has beaten them three times is put in from as near as they got (never
			// leave a town with a hole over one awkward block, as builders do for construction)
			double d2 = folk.distanceToSqr(Vec3.atCenterOf(pos));
			int missed = d2 < 8 * 8 ? 0 : d.missed();
			if ((d2 < 8 * 8 || missed >= 3 && d2 < 24 * 24) && job.cooldown <= 0) {
				f.lookAt(pos);
				place(rt, f, level, world, s, d, current);
				job.cooldown = cfg.builders().stepTicks();
				job.blocks++;
			} else {
				d.release();
				d.defer(now + cfg.queue().deferTicks() / Math.max(1, 4 - missed));
			}
			job.claim = null;
		}
		return true;
	}

	private static void place(RepairRuntime rt, FolkHandle f, ServerLevel level, HearthWorld world, Settlement s, Damage d, BlockState before) {
		f.swing();
		boolean litter = d.has(Damage.LITTER);
		Restorer.Result r = Restorer.restore(rt, level, s, PlotLocator.services(world), d, true);
		switch (r) {
			case PLACED -> {
				if (litter) {
					Restorer.breakEffect(level, d.blockPos(), before);
				}
				RepairServiceImpl.complete(rt, world, s, d.pos, true, true);
			}
			case SAME -> RepairServiceImpl.complete(rt, world, s, d.pos, true, false);
			case REFUSED, GONE -> {
				s.data(RepairModule.QUEUE).remove(d.pos);
				s.markDirty();
			}
			case DEFERRED -> {
				d.release();
				d.defer(rt.now() + RepairModule.config().queue().deferTicks());
			}
		}
	}

	private void speak(FolkHandle f, Settlement s, Damage d, ServerLevel level) {
		long now = f.gameTime();
		Long last = this.spoke.get(f.resident().id);
		if (last != null && now - last < RepairModule.config().builders().speakCooldownTicks()) {
			return;
		}
		PlotView plot = s.plot(d.plot);
		BlockState expected = d.expected();
		if (plot == null || expected == null) {
			return;
		}
		this.spoke.put(f.resident().id, now);
		f.say(d.has(Damage.LITTER) ? RepairLines.litterLine(plot.spec(), level.getRandom())
			: RepairLines.line(d.cause, plot.spec(), expected, d.blockPos().getY(), level.getRandom()), null);
	}

	@Override
	public void stop(FolkHandle f) {
		Job job = this.jobs.remove(f.resident().id);
		if (job == null) {
			return;
		}
		if (job.claim != null) {
			job.claim.release();
		}
		if (job.column != null && f.entity().level() instanceof ServerLevel level) {
			job.column.removeAll(f, level);
		}
		f.hold(ItemStack.EMPTY);
	}

	/** What a builder is about, for the status command and the self-test log. */
	public String describe(UUID folk) {
		Job job = this.jobs.get(folk);
		if (job == null) {
			return "not mending";
		}
		Damage d = job.claim;
		return (d == null ? "between blocks" : "on " + d.blockPos().toShortString() + " (" + (d.expected() == null ? "?" : d.expected().getBlock().getName().getString())
			+ "), walked " + job.walkTicks + " ticks, stand " + (job.stand == null ? "none" : job.stand.toShortString())) + (job.column == null ? ""
			: job.column.atTop() ? ", on a scaffold" : ", scaffolding") + ", " + job.blocks + " done";
	}

	/** Builders mending now (for status). */
	public int working() {
		return this.jobs.size();
	}
}
