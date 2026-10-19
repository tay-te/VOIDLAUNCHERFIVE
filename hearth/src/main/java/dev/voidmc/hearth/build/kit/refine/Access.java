package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Doors;
import dev.voidmc.hearth.build.kit.Guards;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.kit.noble.Mason;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The access pass every blueprint goes through when it compiles, after the archetype has drawn and before the sealed
 * voids are filled and the light pass runs (the audit's rules S1, S3 and S4, made once for every family): doors that can
 * be used ({@link Doors#finish}: nothing in a doorway, no door into a wall or over a drop, no chest under a solid block),
 * head room over every flight ({@link dev.voidmc.hearth.build.kit.Stairs#finish}) and guards on the edges folk could
 * step off ({@link Guards#finish}). An archetype that does these itself loses
 * nothing: the pass only touches what is still wrong.
 */
public final class Access {
	private Access() {
	}

	/** What the pass did: doors seen, props cleared, doors bricked up, chests made barrels, guards set. */
	public record Report(int doors, int cleared, int bricked, int lids, int guards) {
	}

	public static Report finish(Draft d) {
		// -Dhearth.access.off=true: the drawing as the archetype left it (to measure what the pass does)
		if (Boolean.getBoolean("hearth.access.off")) {
			return new Report(0, 0, 0, 0, 0);
		}
		// -Dhearth.access.skip=doors,stairs,lofts,guards: leave steps out (to find which one did something)
		String skip = System.getProperty("hearth.access.skip", "");
		// what already stood apart before the steps ran (a hung lantern, a test's floating piece, a drawing's own choice):
		// never the steps' doing, so never theirs to take away
		boolean dropOrphans = !skip.contains("loose");
		LongOpenHashSet attached = dropOrphans ? attached(d) : null;
		Doors.Report r = skip.contains("doors") ? new Doors.Report(0, 0, 0, 0) : Doors.finish(d);
		if (!skip.contains("stairs")) {
			dev.voidmc.hearth.build.kit.Stairs.finish(d);
		}
		if (!skip.contains("lofts") && !Boolean.getBoolean("hearth.lofts.off")) {
			Lofts.finish(d);
		}
		int guards = skip.contains("guards") ? 0 : Guards.finish(d);
		// the few blocks a step above left hanging (head room cut out of a floor leaving a pair of boards alone): taken away,
		// as every seat's own last stroke does (requests/w7-landmark.md §10)
		if (dropOrphans) {
			dropOrphans(d, attached, 6);
		}
		return new Report(r.doors(), r.cleared(), r.bricked(), r.lids(), guards);
	}

	/** Every drafted block joined to the ground right now. */
	static LongOpenHashSet attached(Draft d) {
		LongOpenHashSet loose = new LongOpenHashSet(Mason.loose(d));
		LongOpenHashSet out = new LongOpenHashSet();
		for (var e : d.cells().long2ObjectEntrySet()) {
			if (!e.getValue().state().isAir() && !loose.contains(e.getLongKey())) {
				out.add(e.getLongKey());
			}
		}
		return out;
	}

	/**
	 * Takes away the blocks the steps orphaned: joined to the ground before them ({@code attached}), loose after, and
	 * not a fixture that hangs or clings by design (lights, chains, signs, banners, ladders, vines, bells, torches,
	 * buttons). Nothing at all when more than {@code most} were orphaned: that is a step's fault to show, not to hide.
	 */
	static void dropOrphans(Draft d, LongOpenHashSet attached, int most) {
		LongArrayList orphans = new LongArrayList();
		for (long k : Mason.loose(d)) {
			if (attached.contains(k) && !byDesign(d.get(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)))) {
				orphans.add(k);
			}
		}
		if (orphans.isEmpty() || orphans.size() > most) {
			return;
		}
		for (long k : orphans) {
			d.set(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k), Blocks.AIR.defaultBlockState());
		}
	}

	/** Blocks that hang from or cling to what holds them: never taken for a stray piece of floor. */
	static boolean byDesign(BlockState s) {
		Block b = s.getBlock();
		return Spaces.hangs(s) || b instanceof LanternBlock || b instanceof ChainBlock || b instanceof SignBlock
			|| b instanceof AbstractBannerBlock || b instanceof BaseTorchBlock || b instanceof LadderBlock || b instanceof VineBlock
			|| b instanceof BellBlock || b instanceof FaceAttachedHorizontalDirectionalBlock || b instanceof CandleBlock
			|| b instanceof TripWireHookBlock || b instanceof net.minecraft.world.level.block.HangingRootsBlock;
	}
}
