package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Window shutters that close at night (docs/LIVING_WORLD_2.md §5.1, §5.3): in every embodied town, the shutters of its
 * built plots ({@link ShutterScan}, once per plot per embodiment, off the server thread, cached in memory) close at
 * 20:30 and open at 06:30, and close too while an outlaw or a raid is about, or in a thunderstorm. Only the {@code open}
 * property of a trapdoor still standing where the blueprint hung it changes, at most {@code per_tick} a town a tick
 * (shrinking as the server slows); a player's own plot is never touched. Nothing is saved: a town that goes abstract
 * keeps what its chunks last had, and the next embodiment sets them for the hour.
 */
public final class Shutters {
	/** Minutes of the day the shutters close and open. */
	static final int CLOSE_AT = 20 * 60 + 30;
	static final int OPEN_AT = 6 * 60 + 30;
	/** Ticks between looks at which towns are embodied and what their shutters should be. */
	static final int LOOK_TICKS = 20;
	/** A full sweep to set chunks that loaded since (and anything a player or builder changed) this often. */
	static final int SWEEP_TICKS = 20 * 30;

	private static final ExecutorService SCANS = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-shutter-scan");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final CrowdDirector director;
	private final Map<Long, Town> towns = new HashMap<>();
	private int tick;

	Shutters(CrowdDirector director) {
		this.director = director;
	}

	private static final class Town {
		final long id;
		final Map<Integer, long[]> plots = new ConcurrentHashMap<>();
		final Map<Integer, CompletableFuture<long[]>> scanning = new HashMap<>();
		boolean closed;
		boolean known;
		/** Where the sweep through every shutter has got to (plot by plot), or -1 when there is nothing to do. */
		Iterator<long[]> plotCursor;
		long[] current;
		int index;
		long lastSweep;

		Town(long id) {
			this.id = id;
		}
	}

	/** Whether a town's shutters are (to be) closed now. */
	public boolean closed(Settlement s, ServerLevel level) {
		long now = level.getGameTime();
		if (this.director.outlawAbout(s.id(), now) || level.isThundering()) {
			return true;
		}
		TownState town = NpcModule.embodiment().townIfKnown(s.id());
		if (town != null && town.alarmed(now)) {
			return true;
		}
		return night(NpcTime.minuteOfDay(level));
	}

	/** Shutter hours: from 20:30 to 06:30. */
	public static boolean night(int minute) {
		return minute >= CLOSE_AT || minute < OPEN_AT;
	}

	public void tick(MinecraftServer server) {
		this.tick++;
		if (!Crowds.config().crowds().enabled() || !Crowds.config().shutters().enabled()) {
			return;
		}
		long t0 = System.nanoTime();
		boolean worked = false;
		ServerLevel level = server.overworld();
		HearthWorld world = HearthWorld.of(level);
		if (world == null) {
			return;
		}
		if (this.tick % LOOK_TICKS == 0) {
			long l0 = System.nanoTime();
			look(level, world);
			CrowdStats.SHUTTER_LOOKS.add(System.nanoTime() - l0);
			t0 = System.nanoTime();
		}
		double mspt = LoadCaps.mspt(server);
		int cap = LoadCaps.cap(Crowds.config().shutters().perTick(), 4, mspt, LoadCaps.highMspt());
		for (Town t : this.towns.values()) {
			if (t.current != null || t.plotCursor != null) {
				worked = true;
				toggle(level, t, cap);
			}
		}
		if (worked) {
			CrowdStats.SHUTTERS.add(System.nanoTime() - t0);
		}
	}

	/** Which towns are embodied, their plots scanned, and whether their shutters should change. */
	private void look(ServerLevel level, HearthWorld world) {
		long now = level.getGameTime();
		this.towns.keySet().removeIf(id -> {
			Settlement s = world.settlement(id);
			return s == null || !s.embodied();
		});
		int submits = 2;
		for (Settlement s : world.settlements()) {
			if (!s.embodied() || s.dimension() != level.dimension()) {
				continue;
			}
			Town t = this.towns.computeIfAbsent(s.id(), Town::new);
			for (PlotView p : s.plots()) {
				if (p.state() != PlotState.BUILT || p.owner() != null || t.plots.containsKey(p.id())) {
					continue;
				}
				CompletableFuture<long[]> f = t.scanning.get(p.id());
				if (f != null) {
					if (f.isDone()) {
						t.scanning.remove(p.id());
						long[] found = f.getNow(new long[0]);
						t.plots.put(p.id(), found == null ? new long[0] : found);
						t.known = false; // set the new plot's shutters for the hour
					}
					continue;
				}
				if (submits > 0 && t.scanning.size() < 4 && BlueprintSource.ready(level, s, p)) {
					Blueprint bp = BlueprintSource.of(level, s, p);
					if (bp == null) {
						t.plots.put(p.id(), new long[0]);
						continue;
					}
					submits--;
					t.scanning.put(p.id(), CompletableFuture.supplyAsync(() -> {
						try {
							return ShutterScan.scan(bp, bp.stageCount());
						} catch (RuntimeException e) {
							Hearth.LOG.warn("shutter scan of plot {} failed", p.id(), e);
							return new long[0];
						}
					}, SCANS));
				}
			}
			boolean want = closed(s, level);
			if (!t.known || want != t.closed || now - t.lastSweep > SWEEP_TICKS) {
				t.known = true;
				t.closed = want;
				t.lastSweep = now;
				t.plotCursor = t.plots.values().iterator();
				t.current = null;
				t.index = 0;
			}
		}
	}

	/** Sets up to {@code cap} shutters of a town to what they should be. */
	private void toggle(ServerLevel level, Town t, int cap) {
		int changed = 0;
		int looked = 0;
		while (changed < cap && looked < cap * 8) {
			if (t.current == null || t.index >= t.current.length) {
				if (t.plotCursor == null || !t.plotCursor.hasNext()) {
					t.plotCursor = null;
					t.current = null;
					return;
				}
				t.current = t.plotCursor.next();
				t.index = 0;
				continue;
			}
			BlockPos pos = BlockPos.of(t.current[t.index++]);
			looked++;
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof TrapDoorBlock) || !state.hasProperty(TrapDoorBlock.OPEN)) {
				continue; // taken down, or a player's block now: never touched
			}
			boolean open = !t.closed;
			if (state.getValue(TrapDoorBlock.OPEN) != open) {
				dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, pos, state.setValue(TrapDoorBlock.OPEN, open), Block.UPDATE_CLIENTS);
				changed++;
				CrowdStats.SHUTTERS_TOGGLED.increment();
			}
		}
	}

	/** The town's shutters as they are now: how many are known, and how many are closed (for the tour and the command). */
	public int[] count(ServerLevel level, Settlement s) {
		Town t = this.towns.get(s.id());
		int known = 0;
		int closed = 0;
		if (t != null) {
			for (long[] plot : t.plots.values()) {
				for (long at : plot) {
					BlockPos pos = BlockPos.of(at);
					if (!level.isLoaded(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof TrapDoorBlock && state.hasProperty(TrapDoorBlock.OPEN)) {
						known++;
						if (!state.getValue(TrapDoorBlock.OPEN)) {
							closed++;
						}
					}
				}
			}
		}
		return new int[]{known, closed};
	}

	/** Every known shutter of a town (positions as {@link BlockPos#asLong}), for tours and tools. */
	public java.util.List<Long> positions(long settlementId) {
		java.util.List<Long> out = new java.util.ArrayList<>();
		Town t = this.towns.get(settlementId);
		if (t != null) {
			for (long[] plot : t.plots.values()) {
				for (long at : plot) {
					out.add(at);
				}
			}
		}
		return out;
	}

	/** Forgets a town's scans (it went abstract: the next embodiment scans afresh). */
	public void forget(long settlementId) {
		this.towns.remove(settlementId);
	}

	public void clear() {
		this.towns.clear();
	}

	public int scannedPlots() {
		int n = 0;
		for (Town t : this.towns.values()) {
			n += t.plots.size();
		}
		return n;
	}
}
