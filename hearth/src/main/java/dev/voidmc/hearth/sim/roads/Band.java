package dev.voidmc.hearth.sim.roads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;

/**
 * A band of robbers holding a lonely stretch of road (docs/LIVING_WORLD_2.md §1.2, "Bandits"): the road it haunts, its
 * camp off the road in cover, the spot it lies in wait at, how many it is and how bold, and what it has taken.
 *
 * @param along   blocks from the road's {@code from} end to the ambush spot
 * @param morale  0–1: how ready it is to step out (a fight lost lowers it, plunder raises it)
 * @param loot    goods taken, by ordinal
 * @param coin    bits taken
 * @param since   the sim day it took to the road
 */
public record Band(long id, long road, BlockPos camp, double along, int strength, double morale, int[] loot, long coin, long since) {
	public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("id").forGetter(Band::id),
		Codec.LONG.fieldOf("road").forGetter(Band::road),
		BlockPos.CODEC.fieldOf("camp").forGetter(Band::camp),
		Codec.DOUBLE.fieldOf("along").forGetter(Band::along),
		Codec.INT.fieldOf("strength").forGetter(Band::strength),
		Codec.DOUBLE.optionalFieldOf("morale", 0.6).forGetter(Band::morale),
		Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).optionalFieldOf("loot", new int[0]).forGetter(Band::loot),
		Codec.LONG.optionalFieldOf("coin", 0L).forGetter(Band::coin),
		Codec.LONG.optionalFieldOf("since", 0L).forGetter(Band::since)
	).apply(i, Band::new));

	/** At most this many in a band. */
	public static final int MAX_STRENGTH = 7;
	/** Below this many a band breaks and its road is free. */
	public static final int BREAKS_BELOW = 2;

	public Band withStrength(int strength, double morale) {
		return new Band(this.id, this.road, this.camp, this.along, Math.max(0, Math.min(MAX_STRENGTH, strength)), Math.max(0, Math.min(1, morale)), this.loot,
			this.coin, this.since);
	}

	public Band plunder(int[] cargo, long bits) {
		int[] next = java.util.Arrays.copyOf(this.loot, Math.max(this.loot.length, cargo.length));
		for (int g = 0; g < cargo.length; g++) {
			next[g] += Math.max(0, cargo[g]);
		}
		return new Band(this.id, this.road, this.camp, this.along, this.strength, Math.min(1, this.morale + 0.1), next, this.coin + Math.max(0, bits),
			this.since);
	}

	public boolean broken() {
		return this.strength < BREAKS_BELOW;
	}
}
