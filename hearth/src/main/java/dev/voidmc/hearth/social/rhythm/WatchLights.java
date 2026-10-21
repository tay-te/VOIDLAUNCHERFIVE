package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The night watch's torches give real light (docs/LIVING_WORLD_2.md §2.3): an invisible {@code minecraft:light} block at
 * each watchman's head, moved as he walks, placed only in air and taken away behind him. Every one is written in the
 * settlement's {@code hearth:social/lights} before it is placed, so a crash leaves none: they are cleared on the next
 * embodiment. At most {@code max_per_town} a town (fewer as the server slows), and at most {@code changes_per_second}
 * block changes a town a second.
 */
public final class WatchLights {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Watchman → the light he carries now. */
	private static final Map<UUID, Held> HELD = new HashMap<>();
	/** Settlement → block changes this second, and which second. */
	private static final Map<Long, long[]> BUDGET = new HashMap<>();

	record Held(long settlement, BlockPos pos) {
	}

	private WatchLights() {
	}

	/** How many lights a town may have lit now. */
	static int cap() {
		RhythmConfig.Lights cfg = Rhythm.config().lights();
		double mspt = Rhythm.server() == null ? 0 : LoadCaps.mspt(Rhythm.server());
		return LoadCaps.cap(cfg.maxPerTown(), 0, mspt, LoadCaps.highMspt());
	}

	static int lit(long settlement) {
		int n = 0;
		for (Held h : HELD.values()) {
			if (h.settlement == settlement) {
				n++;
			}
		}
		return n;
	}

	private static boolean spend(ServerLevel level, long settlement, int changes) {
		long second = level.getGameTime() / 20;
		long[] b = BUDGET.computeIfAbsent(settlement, k -> new long[2]);
		if (b[0] != second) {
			b[0] = second;
			b[1] = 0;
		}
		if (b[1] + changes > Rhythm.config().lights().changesPerSecond()) {
			return false;
		}
		b[1] += changes;
		return true;
	}

	/** Carries a watchman's light to {@code head} (his eye block); returns whether he has one lit there. */
	public static boolean carry(ServerLevel level, Settlement s, UUID watchman, BlockPos head) {
		Held h = HELD.get(watchman);
		if (h != null && h.pos.equals(head)) {
			return true;
		}
		boolean canLight = level.isLoaded(head) && level.getBlockState(head).isAir();
		if (h == null && (!canLight || lit(s.id()) >= cap())) {
			return false;
		}
		if (!spend(level, s.id(), (h != null ? 1 : 0) + (canLight ? 1 : 0))) {
			return h != null;
		}
		if (h != null) {
			remove(level, s, h.pos);
			HELD.remove(watchman);
		}
		if (canLight) {
			RhythmBook.Lights book = s.data(RhythmBook.LIGHTS);
			book.positions.add(head.asLong());
			s.markDirty();
			dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, head, light(), FLAGS);
			HELD.put(watchman, new Held(s.id(), head.immutable()));
			return true;
		}
		return false;
	}

	/** Puts a watchman's light out (his round is over, he folded). */
	public static void douse(ServerLevel level, Settlement s, UUID watchman) {
		Held h = HELD.remove(watchman);
		if (h != null) {
			remove(level, s, h.pos);
		}
	}

	static BlockState light() {
		return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Rhythm.config().lights().level());
	}

	private static void remove(ServerLevel level, Settlement s, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, pos, Blocks.AIR.defaultBlockState(), FLAGS);
		}
		RhythmBook.Lights book = s.data(RhythmBook.LIGHTS);
		if (book.positions.remove(Long.valueOf(pos.asLong()))) {
			s.markDirty();
		}
	}

	/**
	 * Puts out every light the town's book lists (on embodiment, when it folds, at shutdown): those in loaded chunks go now,
	 * the rest stay in the book until next time. Returns how many were removed.
	 */
	public static int clearTown(ServerLevel level, Settlement s) {
		for (Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			if (it.next().getValue().settlement == s.id()) {
				it.remove();
			}
		}
		RhythmBook.Lights book = s.data(RhythmBook.LIGHTS);
		if (book.positions.isEmpty()) {
			return 0;
		}
		int removed = 0;
		for (Iterator<Long> it = book.positions.iterator(); it.hasNext();) {
			BlockPos p = BlockPos.of(it.next());
			if (!level.isLoaded(p)) {
				continue;
			}
			if (level.getBlockState(p).is(Blocks.LIGHT)) {
				dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, p, Blocks.AIR.defaultBlockState(), FLAGS);
			}
			it.remove();
			removed++;
		}
		if (removed > 0) {
			s.markDirty();
		}
		return removed;
	}

	/** Lights held by watchmen no longer walking (folded without a stop): out. */
	static void sweep(ServerLevel level, Settlement s, java.util.Set<UUID> walking) {
		for (Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Held> e = it.next();
			if (e.getValue().settlement == s.id() && !walking.contains(e.getKey())) {
				remove(level, s, e.getValue().pos);
				it.remove();
			}
		}
	}

	/** Lights standing in a town (book), for tests and the harness. */
	public static int standing(Settlement s) {
		return s.data(RhythmBook.LIGHTS).positions.size();
	}

	static void clear() {
		HELD.clear();
		BUDGET.clear();
	}
}
