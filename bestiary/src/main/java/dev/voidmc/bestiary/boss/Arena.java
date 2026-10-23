package dev.voidmc.bestiary.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A boss's arena (BESTIARY.md §8.2): a cylinder around a centre (a radius and a height band), the spot the boss returns
 * to on a reset, and an optional mound (the Gallows Knight's Hanging hits whoever stands on it). Saved with the boss.
 *
 * @param centre      the arena's centre (floor level)
 * @param radius      horizontal radius in blocks
 * @param below       blocks below the centre's floor that still count (pits, stairs down)
 * @param above       blocks above it that still count (pillars, a mound, a jump)
 * @param home        where the boss stands when the fight starts and returns to on a reset
 * @param homeYaw     the way it faces there
 * @param mound       the mound's centre, if the arena has one
 * @param moundRadius the mound's radius
 */
public record Arena(BlockPos centre, int radius, int below, int above, BlockPos home, float homeYaw, Optional<BlockPos> mound, int moundRadius) {
	public static final Codec<Arena> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("centre").forGetter(Arena::centre),
		Codec.INT.fieldOf("radius").forGetter(Arena::radius),
		Codec.INT.optionalFieldOf("below", 4).forGetter(Arena::below),
		Codec.INT.optionalFieldOf("above", 10).forGetter(Arena::above),
		BlockPos.CODEC.fieldOf("home").forGetter(Arena::home),
		Codec.FLOAT.optionalFieldOf("home_yaw", 0F).forGetter(Arena::homeYaw),
		BlockPos.CODEC.optionalFieldOf("mound").forGetter(Arena::mound),
		Codec.INT.optionalFieldOf("mound_radius", 3).forGetter(Arena::moundRadius)).apply(i, Arena::new));

	/** An arena of {@code radius} around {@code centre}, the boss at home there, no mound. */
	public static Arena around(BlockPos centre, int radius, float yaw) {
		return new Arena(centre, radius, 4, 10, centre, yaw, Optional.empty(), 3);
	}

	public boolean contains(Vec3 p) {
		return BossMath.inCylinder(p.x, p.y, p.z, this.centre.getX() + 0.5, this.centre.getY(), this.centre.getZ() + 0.5, this.radius, this.below, this.above);
	}

	public boolean contains(Entity e) {
		return this.contains(e.position());
	}

	/** Horizontal distance from the centre. */
	public double distance(Vec3 p) {
		double dx = p.x - (this.centre.getX() + 0.5), dz = p.z - (this.centre.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Whether {@code p} stands on the mound (its disc, a block below to three above the mound's top). */
	public boolean onMound(Vec3 p) {
		if (this.mound.isEmpty()) {
			return false;
		}
		BlockPos m = this.mound.get();
		return BossMath.inCylinder(p.x, p.y, p.z, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, this.moundRadius + 0.5, 1, 3);
	}

	public Vec3 homeVec() {
		return Vec3.atBottomCenterOf(this.home);
	}
}
