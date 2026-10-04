package dev.voidmc.hearth.law.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.law.justice.Warrant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * One settlement's law, saved with it ({@code hearth:law/state}): warrants per player, suspicion, crimes waiting for a
 * witness to report them, crimes nobody saw (discovered later), and locks that were picked or broken.
 */
public final class LawState {
	public static final Codec<LawState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Warrant.CODEC.listOf().optionalFieldOf("warrants", List.of()).forGetter(s -> List.copyOf(s.warrants.values())),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("suspicion", Map.of()).forGetter(s -> s.suspicion),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("seen", Map.of()).forGetter(s -> s.seen),
		PendingReport.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(s -> s.pending),
		Unsolved.CODEC.listOf().optionalFieldOf("unsolved", List.of()).forGetter(s -> s.unsolved),
		LockOverride.CODEC.listOf().optionalFieldOf("locks", List.of()).forGetter(s -> List.copyOf(s.locks.values())),
		Codec.INT.optionalFieldOf("next_id", 1).forGetter(s -> s.nextId)
	).apply(i, (w, su, se, p, u, l, n) -> {
		LawState s = new LawState();
		w.forEach(x -> s.warrants.put(x.player, x));
		s.suspicion.putAll(su);
		s.seen.putAll(se);
		s.pending.addAll(p);
		s.unsolved.addAll(u);
		l.forEach(x -> s.locks.put(x.pos(), x));
		s.nextId = n;
		return s;
	}));

	private final Map<UUID, Warrant> warrants = new LinkedHashMap<>();
	private final Map<UUID, Integer> suspicion = new HashMap<>();
	private final Map<UUID, Long> seen = new HashMap<>();
	private final List<PendingReport> pending = new ArrayList<>();
	private final List<Unsolved> unsolved = new ArrayList<>();
	private final Map<Long, LockOverride> locks = new HashMap<>();
	private int nextId = 1;

	public Map<UUID, Warrant> warrants() {
		return this.warrants;
	}

	public @Nullable Warrant warrant(UUID player) {
		return this.warrants.get(player);
	}

	public Warrant warrantFor(UUID player, long day) {
		return this.warrants.computeIfAbsent(player, p -> new Warrant(p, day));
	}

	/** Drops settled warrants so the saved state stays small. */
	public void prune() {
		this.warrants.values().removeIf(w -> !w.active());
		this.suspicion.values().removeIf(v -> v <= 0);
	}

	public int suspicion(UUID player) {
		return this.suspicion.getOrDefault(player, 0);
	}

	public void addSuspicion(UUID player, int delta) {
		int v = Math.clamp(suspicion(player) + delta, 0, 100);
		if (v == 0) {
			this.suspicion.remove(player);
		} else {
			this.suspicion.put(player, v);
		}
	}

	public Map<UUID, Integer> suspicionMap() {
		return this.suspicion;
	}

	/** The last sim day the player was seen in town (for crimes discovered later), or a very old day. */
	public long seenDay(UUID player) {
		return this.seen.getOrDefault(player, Long.MIN_VALUE / 2);
	}

	public void markSeen(UUID player, long day) {
		this.seen.put(player, day);
	}

	public Map<UUID, Long> seenMap() {
		return this.seen;
	}

	public List<PendingReport> pending() {
		return this.pending;
	}

	public List<Unsolved> unsolved() {
		return this.unsolved;
	}

	public Map<Long, LockOverride> locks() {
		return this.locks;
	}

	public int nextId() {
		return this.nextId++;
	}

	public boolean isEmpty() {
		return this.warrants.isEmpty() && this.suspicion.isEmpty() && this.pending.isEmpty() && this.unsolved.isEmpty() && this.locks.isEmpty();
	}

	/**
	 * A crime a witness saw and is on the way to report.
	 *
	 * @param witness  the resident who saw it
	 * @param deadline the game time by which it is reported even if the witness never reaches a guard
	 */
	public record PendingReport(int id, UUID witness, Crime crime, long deadline) {
		public static final Codec<PendingReport> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(PendingReport::id),
			UUIDUtil.CODEC.fieldOf("witness").forGetter(PendingReport::witness),
			Codecs.CRIME.fieldOf("crime").forGetter(PendingReport::crime),
			Codec.LONG.fieldOf("deadline").forGetter(PendingReport::deadline)
		).apply(i, PendingReport::new));
	}

	/**
	 * A crime nobody saw; it may be discovered (a missing purse, a body, a forced lock) and cast suspicion on whoever was
	 * about.
	 *
	 * @param day the sim day it happened
	 */
	public record Unsolved(Crime crime, long day) {
		public static final Codec<Unsolved> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codecs.CRIME.fieldOf("crime").forGetter(Unsolved::crime),
			Codec.LONG.fieldOf("day").forGetter(Unsolved::day)
		).apply(i, Unsolved::new));
	}

	/**
	 * A lock that is not as the settlement left it.
	 *
	 * @param pos    the block (packed)
	 * @param until  game time it stays open (picked locks are re-locked by their owners in the morning)
	 * @param broken forced: open until mended
	 */
	public record LockOverride(long pos, long until, boolean broken) {
		public static final Codec<LockOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(LockOverride::pos),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(LockOverride::until),
			Codec.BOOL.optionalFieldOf("broken", false).forGetter(LockOverride::broken)
		).apply(i, LockOverride::new));
	}
}
