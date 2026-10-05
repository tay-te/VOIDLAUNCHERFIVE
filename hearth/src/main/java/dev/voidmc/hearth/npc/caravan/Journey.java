package dev.voidmc.hearth.npc.caravan;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A caravan on the road between two settlements, as the npc module knows it: from the sim's {@code CARAVAN DEPARTED}
 * event until it arrives, is robbed or is lost. Where it is follows from the time since it set out at the configured
 * pace along the line between the two towns' centres; the sim alone decides when it arrives.
 *
 * @param cargo units by {@link dev.voidmc.hearth.api.Goods#ordinal()}
 */
public record Journey(long id, ResourceKey<Level> dimension, long fromId, long toId, String toName, BlockPos from, BlockPos to, int[] cargo,
	long departed, long simId, long arrives) {

	/** A journey of the npc module's own (the dev command): it moves at the configured pace. */
	public Journey(long id, ResourceKey<Level> dimension, long fromId, long toId, String toName, BlockPos from, BlockPos to, int[] cargo,
		long departed) {
		this(id, dimension, fromId, toId, toName, from, to, cargo, departed, 0, 0);
	}

	/** The length of the road, in blocks (straight line, horizontally). */
	public double length() {
		double dx = this.to.getX() - this.from.getX();
		double dz = this.to.getZ() - this.from.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** How far along the road it is, 0 to 1, at a pace of {@code speed} blocks per second. */
	public double progress(long gameTime, double speed) {
		if (this.arrives > this.departed) {
			// the sim's own caravan: where the sim says it is, so the party meets the books (HearthWorld.CaravanView)
			return Mth.clamp((gameTime - this.departed) / (double) (this.arrives - this.departed), 0.0, 1.0);
		}
		double len = length();
		if (len < 1.0) {
			return 1.0;
		}
		double travelled = Math.max(0, gameTime - this.departed) / 20.0 * speed;
		return Mth.clamp(travelled / len, 0.0, 1.0);
	}

	/** The point on the road at a fraction of the way (y is the start's; callers find the ground). */
	public BlockPos at(double t) {
		t = Mth.clamp(t, 0.0, 1.0);
		return BlockPos.containing(Mth.lerp(t, this.from.getX(), this.to.getX()) + 0.5, this.from.getY(), Mth.lerp(t, this.from.getZ(), this.to.getZ()) + 0.5);
	}

	/** Total units carried. */
	public int units() {
		int n = 0;
		for (int c : this.cargo) {
			n += Math.max(0, c);
		}
		return n;
	}
}
