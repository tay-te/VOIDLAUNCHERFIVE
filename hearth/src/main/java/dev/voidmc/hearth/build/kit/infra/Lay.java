package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Drawing a linear work in world coordinates on a {@link Draft}: infrastructure takes its geometry from world-space
 * params (PLANNING.md §7), so its archetypes place by world position and world direction and this maps both into the
 * plot's local frame (positions through {@link LocalFrame}, directions to the local labels the compiler turns back).
 * Heights are world y. Ground and water come from the draft's {@link Site}, in world y.
 */
public final class Lay {
	private final Draft d;
	private final LocalFrame f;
	private final int fy;

	public Lay(Draft d) {
		this.d = d;
		this.f = d.frame();
		this.fy = d.site().floorY();
	}

	public Draft draft() {
		return this.d;
	}

	public LocalFrame frame() {
		return this.f;
	}

	/** The world y of the spec's floor level (the plot's origin y). */
	public int floorY() {
		return this.fy;
	}

	public int lx(int wx, int wz) {
		return this.f.localX(wx, wz);
	}

	public int lz(int wx, int wz) {
		return this.f.localZ(wx, wz);
	}

	public int wx(int lx, int lz) {
		return this.f.worldX(lx, lz);
	}

	public int wz(int lx, int lz) {
		return this.f.worldZ(lx, lz);
	}

	/** The highest world y anything may occupy. */
	public int topY() {
		return this.fy + this.d.maxHeight() - 1;
	}

	/** Whether the plot may hold a block at this world position (inside the footprint, under the height). */
	public boolean allowed(int wx, int wy, int wz) {
		return this.d.allowed(lx(wx, wz), wy - this.fy, lz(wx, wz)) && lz(wx, wz) >= 0;
	}

	public void set(int wx, int wy, int wz, Paint p) {
		this.d.set(lx(wx, wz), wy - this.fy, lz(wx, wz), p);
	}

	public void set(int wx, int wy, int wz, @Nullable BlockState s) {
		if (s != null) {
			this.d.set(lx(wx, wz), wy - this.fy, lz(wx, wz), s);
		}
	}

	/** A block with its block entity data (a chest's contents). */
	public void set(int wx, int wy, int wz, @Nullable BlockState s, net.minecraft.nbt.CompoundTag tag) {
		if (s != null) {
			this.d.set(lx(wx, wz), wy - this.fy, lz(wx, wz), s, tag);
		}
	}

	public void setIfAbsent(int wx, int wy, int wz, Paint p) {
		this.d.setIfAbsent(lx(wx, wz), wy - this.fy, lz(wx, wz), p);
	}

	public void air(int wx, int wy, int wz) {
		this.d.air(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	/** Air in a given stage, only where nothing else is drawn. */
	public void airIfAbsent(int wx, int wy, int wz, StageKind stage) {
		if (absent(wx, wy, wz)) {
			try (var s = this.d.stage(stage)) {
				air(wx, wy, wz);
			}
		}
	}

	public void remove(int wx, int wy, int wz) {
		this.d.remove(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	public boolean absent(int wx, int wy, int wz) {
		return this.d.isAbsent(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	public boolean empty(int wx, int wy, int wz) {
		return this.d.isEmpty(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	public boolean solid(int wx, int wy, int wz) {
		return this.d.isSolid(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	public @Nullable BlockState get(int wx, int wy, int wz) {
		return this.d.get(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	/** The natural standing level (world y) at a column: one above the top of the natural ground. */
	public int ground(int wx, int wz) {
		return this.d.site().ground(lx(wx, wz), lz(wx, wz)) + this.fy;
	}

	/** The world y of the water's surface top (first air above it), or {@link Site#NONE}. */
	public int water(int wx, int wz) {
		int w = this.d.site().water(lx(wx, wz), lz(wx, wz));
		return w == Site.NONE ? Site.NONE : w + this.fy;
	}

	public boolean wet(int wx, int wz) {
		return this.d.site().isWater(lx(wx, wz), lz(wx, wz));
	}

	/** Where a person would stand on the natural land or water at a column (the water's surface where it is wet). */
	public int surface(int wx, int wz) {
		int w = water(wx, wz);
		return w == Site.NONE ? ground(wx, wz) : Math.max(w, ground(wx, wz));
	}

	/** The local direction label that points the given world direction (for block states). */
	public Direction local(Direction world) {
		return world.getAxis().isVertical() ? world : this.f.toLocal(world);
	}

	/** The local axis of a world axis. */
	public Direction.Axis local(Direction.Axis world) {
		if (world == Direction.Axis.Y) {
			return world;
		}
		return this.f.toLocal(world == Direction.Axis.X ? Direction.EAST : Direction.SOUTH).getAxis();
	}

	/** Registers an entrance at a world position. */
	public void entrance(int wx, int wy, int wz) {
		this.d.entrance(lx(wx, wz), wy - this.fy, lz(wx, wz));
	}

	/** A point of interest at a world position facing a world direction. */
	public void poi(dev.voidmc.hearth.api.PoiType type, int wx, int wy, int wz, Direction worldFacing) {
		this.d.poi(type, lx(wx, wz), wy - this.fy, lz(wx, wz), local(worldFacing));
	}

	public static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
