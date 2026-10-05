package dev.voidmc.hearth.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Everything needed to generate a building, and nothing else: a blueprint is a pure function of its spec and the
 * terrain probe ({@link BlueprintService#blueprint}). That is what lets a settlement store a town of a hundred buildings
 * as a hundred specs, rebuild any of them on any thread, and write them into chunks as the chunks generate.
 *
 * <p>Geometry, in world coordinates:
 * <ul>
 *   <li>The footprint is the rectangle {@code [origin.x, origin.x + sizeX) × [origin.z, origin.z + sizeZ)}.</li>
 *   <li>{@code origin.y} is the <b>floor level</b>: the y a person stands at on the ground floor (the floor block itself
 *   is at {@code origin.y - 1}). The planner sets it so floors meet the street; the blueprint reconciles it with the
 *   natural ground under the footprint (foundations down, earthwork, steps up to the door).</li>
 *   <li>{@code facing} is the horizontal direction the front (main entrance) faces: toward the street.</li>
 *   <li>Nothing may be placed above {@code origin.y + maxHeight} or outside the footprint, except what the archetype
 *   declares as overhang into the street (eaves, signs, balconies: at most one block, never below 3 above the street).</li>
 * </ul>
 *
 * @param archetype which generator, e.g. {@code hearth:house/cottage}
 * @param style     the palette and regional habits, e.g. {@code hearth:temperate} (build-module data)
 * @param seed      per-plot seed; same spec, same building
 * @param tier      wealth and finish, 0 (rough) to 4 (grand); the planner derives it from settlement tier and district
 * @param params    archetype-specific inputs the planner chose: storeys, party walls, a road's polyline, ...
 *                  (each archetype documents its keys)
 */
public record PlotSpec(Identifier archetype, Identifier style, long seed, BlockPos origin, int sizeX, int sizeZ,
	Direction facing, int maxHeight, int tier, CompoundTag params) {

	public static final Codec<PlotSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.fieldOf("archetype").forGetter(PlotSpec::archetype),
		Identifier.CODEC.fieldOf("style").forGetter(PlotSpec::style),
		Codec.LONG.fieldOf("seed").forGetter(PlotSpec::seed),
		BlockPos.CODEC.fieldOf("origin").forGetter(PlotSpec::origin),
		Codec.INT.fieldOf("size_x").forGetter(PlotSpec::sizeX),
		Codec.INT.fieldOf("size_z").forGetter(PlotSpec::sizeZ),
		Direction.CODEC.fieldOf("facing").forGetter(PlotSpec::facing),
		Codec.INT.fieldOf("max_height").forGetter(PlotSpec::maxHeight),
		Codec.INT.fieldOf("tier").forGetter(PlotSpec::tier),
		CompoundTag.CODEC.optionalFieldOf("params", new CompoundTag()).forGetter(PlotSpec::params)
	).apply(i, PlotSpec::new));

	public PlotSpec {
		if (facing.getAxis().isVertical()) {
			throw new IllegalArgumentException("a plot faces a horizontal direction, not " + facing);
		}
		if (sizeX <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("empty plot footprint " + sizeX + "x" + sizeZ);
		}
		params = params.copy();
	}

	/** The footprint's width along the street (perpendicular to {@link #facing}). */
	public int frontage() {
		return this.facing.getAxis() == Direction.Axis.Z ? this.sizeX : this.sizeZ;
	}

	/** The footprint's depth away from the street (along {@link #facing}). */
	public int depth() {
		return this.facing.getAxis() == Direction.Axis.Z ? this.sizeZ : this.sizeX;
	}

	public int floorY() {
		return this.origin.getY();
	}

	/** Whether a param is set (without copying them all, as {@link #params} does: the planner reads every plot's often). */
	public boolean hasParam(String key) {
		return this.params.contains(key);
	}

	/** One int param, or {@code fallback}, without copying the params. */
	public int paramInt(String key, int fallback) {
		return this.params.getIntOr(key, fallback);
	}

	/** One string param, or {@code fallback}, without copying the params. */
	public String paramString(String key, String fallback) {
		return this.params.getStringOr(key, fallback);
	}

	/** One int-array param (a copy of it), without copying the rest. */
	public java.util.Optional<int[]> paramInts(String key) {
		return this.params.getIntArray(key).map(int[]::clone);
	}

	/** The params, as a copy (the record keeps its own so a spec never changes once made). */
	@Override
	public CompoundTag params() {
		return this.params.copy();
	}
}
