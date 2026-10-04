package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.RepairService;
import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The {@link RepairService}: builders (ours, or another module's) claim damaged blocks one at a time and report them
 * done. A claim reserves the block for {@code claim_ticks}; only blocks whose goods are in store are handed out (repairs
 * cost goods like construction); a block put back takes its goods from the stockpile.
 */
public final class RepairServiceImpl implements RepairService {
	@Override
	public @Nullable RepairTask claim(Settlement settlement, UUID builder, BlockPos near) {
		RepairRuntime rt = RepairRuntime.current();
		HearthWorld world = rt == null ? null : rt.worldOf(settlement);
		if (rt == null || world == null || !RepairModule.config().tracking().enabled()) {
			return null;
		}
		Damage d = claimFor(rt, world, settlement, builder, near, Double.MAX_VALUE);
		return d == null ? null : new RepairTask(settlement.id(), d.plot, d.stage, d.index, d.blockPos(), d.claimedAt());
	}

	/**
	 * Claims the best open block for a builder within {@code maxDistance}: its blueprint block known, its goods in store
	 * (else it is deferred and the next is tried), and its step index looked up. Damage a player did that nobody saw is
	 * reported now, found by the builder.
	 */
	public static @Nullable Damage claimFor(RepairRuntime rt, HearthWorld world, Settlement settlement, UUID builder, BlockPos near, double maxDistance) {
		RepairConfig cfg = RepairModule.config();
		RepairQueue q = settlement.data(RepairModule.QUEUE);
		if (q.isEmpty()) {
			return null;
		}
		long now = rt.now();
		PlotLocator.Source source = PlotLocator.services(world);
		Damage d = q.claim(builder, near, maxDistance, now, cfg.queue().claimTicks(), e -> usable(rt, settlement, source, q, e, now));
		if (d != null) {
			rt.vandalism.discovered(world.level(), settlement, List.of(d));
		}
		return d;
	}

	private static boolean usable(RepairRuntime rt, Settlement s, PlotLocator.Source source, RepairQueue q, Damage d, long now) {
		RepairConfig cfg = RepairModule.config();
		BlockState expected = Restorer.expected(rt, s, source, d);
		if (expected == null) {
			q.remove(d.pos);
			s.markDirty();
			return false;
		}
		if (!d.has(Damage.LITTER) && cfg.queue().chargeGoods() && !RepairBill.affordable(s.stockpile(), expected)) {
			d.defer(now + cfg.queue().deferTicks());
			return false;
		}
		if (d.index < 0 && !d.has(Damage.LITTER)) {
			PlotView plot = s.plot(d.plot);
			Blueprint bp = plot == null ? null : source.blueprint(s, plot);
			d.index = bp == null ? -1 : PlotLocator.resolveIndex(bp, d.stage, d.blockPos());
			if (d.index < 0) {
				q.remove(d.pos);
				s.markDirty();
				return false;
			}
		}
		return true;
	}

	@Override
	public void done(RepairTask task, boolean restored) {
		RepairRuntime rt = RepairRuntime.current();
		RepairRuntime.Located at = rt == null ? null : rt.find(task.settlementId());
		if (at == null) {
			return;
		}
		complete(rt, at.world(), at.settlement(), task.pos().asLong(), restored, true);
	}

	/**
	 * Finishes a claimed block: put back (its goods taken when {@code charge}) and gone from the queue, or returned to it.
	 */
	public static void complete(RepairRuntime rt, HearthWorld world, Settlement s, long pos, boolean restored, boolean charge) {
		RepairQueue q = s.data(RepairModule.QUEUE);
		Damage d = q.get(pos);
		if (d == null) {
			return;
		}
		if (!restored) {
			d.claimedBy = null;
			return;
		}
		BlockState expected = d.expected();
		if (charge && expected != null && !d.has(Damage.LITTER) && RepairModule.config().queue().chargeGoods()) {
			RepairBill.charge(s.stockpile(), expected);
		}
		q.remove(pos);
		s.markDirty();
		rt.abstracts.mendedOne(world, s, d.plot);
	}

	@Override
	public int damaged(Settlement settlement) {
		return settlement.data(RepairModule.QUEUE).size();
	}
}
