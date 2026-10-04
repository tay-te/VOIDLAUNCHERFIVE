package dev.voidmc.hearth.standing.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.standing.StandingConfig;
import dev.voidmc.hearth.standing.logic.Curves;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * One resident's memories of players ("helped me", "stole from me", "saved my son"): the personal layer of reputation
 * (docs/LIVING_WORLD.md §3). Kept in {@code Resident.extra("standing")} under {@code mem}, bounded per resident, so the
 * sim persists it with the person and it dies with them.
 *
 * <p>A memory fades with a half-life (strong ones slower); an opinion is the sum of what is left of a player's memories,
 * coloured by the town's standing. The same kind of deed by the same player within a day or so is one memory (the
 * stronger), so a crime reported twice — once by the victim, again when a witness confirms it — is not counted twice.
 */
public final class Memories {
	public static final String KEY = "standing";
	private static final String MEM = "mem";
	private static final Codec<List<Memory>> LIST = Memory.CODEC.listOf();

	private final List<Memory> list;

	private Memories(List<Memory> list) {
		this.list = list;
	}

	/**
	 * @param kind  the deed kind ({@code law:theft}, {@code standing:flattered})
	 * @param weight signed strength when it happened, −100…100
	 * @param day   the sim day it happened
	 */
	public record Memory(UUID player, String kind, int weight, long day) {
		public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("p").forGetter(Memory::player),
			Codec.STRING.fieldOf("k").forGetter(Memory::kind),
			Codec.INT.fieldOf("w").forGetter(Memory::weight),
			Codec.LONG.fieldOf("d").forGetter(Memory::day)
		).apply(i, Memory::new));

		/** What is left of it on {@code today}. */
		public double value(long today, StandingConfig.Opinion cfg) {
			double halfLife = Math.abs(this.weight) >= cfg.strongThreshold() ? cfg.halfLifeDays() * cfg.strongMultiplier() : cfg.halfLifeDays();
			return this.weight * Curves.fade(today - this.day, halfLife);
		}
	}

	/** Reads a resident's memories (without creating their compound if they have none). */
	public static Memories of(Resident resident) {
		CompoundTag tag = resident.extra.getCompoundOrEmpty(KEY);
		return new Memories(new ArrayList<>(tag.read(MEM, LIST).orElse(List.of())));
	}

	public static Memories empty() {
		return new Memories(new ArrayList<>());
	}

	/** Writes the memories back into the resident's record (the caller marks the settlement dirty). */
	public void save(Resident resident) {
		CompoundTag tag = resident.extra(KEY);
		if (this.list.isEmpty()) {
			tag.remove(MEM);
		} else {
			tag.store(MEM, LIST, this.list);
		}
	}

	public List<Memory> all() {
		return List.copyOf(this.list);
	}

	public int size() {
		return this.list.size();
	}

	/**
	 * Remembers something. A memory of the same kind by the same player within {@code mergeDays} is replaced by the
	 * stronger of the two; past the limit the faintest memory (now) is forgotten. Returns the memory that was kept.
	 */
	public Memory add(Memory m, StandingConfig.Opinion cfg) {
		for (int i = 0; i < this.list.size(); i++) {
			Memory old = this.list.get(i);
			if (old.player.equals(m.player) && old.kind.equals(m.kind) && Math.abs(m.day - old.day) <= cfg.mergeDays()) {
				Memory kept = Math.abs(m.weight) >= Math.abs(old.weight) ? m : new Memory(old.player, old.kind, old.weight, Math.max(old.day, m.day));
				this.list.set(i, kept);
				return kept;
			}
		}
		this.list.add(m);
		forget(m.day, cfg, m);
		return m;
	}

	/** Drops memories that have faded to nothing, then the faintest until within the limit. */
	public void forget(long today, StandingConfig.Opinion cfg) {
		forget(today, cfg, null);
	}

	/**
	 * As {@link #forget(long, StandingConfig.Opinion)}, sparing {@code fresh}: what just happened is vivid, so a new small
	 * memory pushes out the faintest old one rather than being forgotten at once among strong ones.
	 */
	private void forget(long today, StandingConfig.Opinion cfg, @Nullable Memory fresh) {
		this.list.removeIf(x -> x != fresh && Math.abs(x.value(today, cfg)) < 0.5);
		while (this.list.size() > cfg.memories()) {
			int weakest = -1;
			double least = Double.MAX_VALUE;
			for (int i = 0; i < this.list.size(); i++) {
				if (this.list.get(i) == fresh) {
					continue;
				}
				double v = Math.abs(this.list.get(i).value(today, cfg));
				if (v < least) {
					least = v;
					weakest = i;
				}
			}
			this.list.remove(weakest);
		}
	}

	/** The sum of what is left of the player's memories, unclamped. */
	public double feeling(UUID player, long today, StandingConfig.Opinion cfg) {
		double sum = 0;
		for (Memory m : this.list) {
			if (m.player.equals(player)) {
				sum += m.value(today, cfg);
			}
		}
		return sum;
	}

	/** Opinion −100…100: memories plus the share of the town's standing that colours how this person sees the player. */
	public int opinion(UUID player, long today, int townStanding, StandingConfig.Opinion cfg) {
		return Curves.clampInt(Curves.round(feeling(player, today, cfg) + townStanding * cfg.townInfluence()), -100, 100);
	}

	/** The player's strongest memory with this person (what they would bring up), or null. */
	public @Nullable Memory strongest(UUID player, long today, StandingConfig.Opinion cfg) {
		Memory best = null;
		double most = 0;
		for (Memory m : this.list) {
			double v = Math.abs(m.value(today, cfg));
			if (m.player.equals(player) && v > most) {
				most = v;
				best = m;
			}
		}
		return best;
	}

	/** Whether the player did this kind of thing to this person since {@code sinceDay}. */
	public boolean has(UUID player, String kind, long sinceDay) {
		for (Memory m : this.list) {
			if (m.player.equals(player) && m.kind.equals(kind) && m.day >= sinceDay) {
				return true;
			}
		}
		return false;
	}
}
