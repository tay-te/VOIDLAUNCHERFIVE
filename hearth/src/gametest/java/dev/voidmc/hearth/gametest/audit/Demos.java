package dev.voidmc.hearth.gametest.audit;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The case an archetype is measured on: blueprintPreview's spec on the synthetic land, except for the two pieces of
 * infrastructure whose bare-plot demo measured nothing real (AUDIT.md §2 "Known limits"): the <b>dock</b> on its water
 * terrain is set where the shore crosses its plot, the road end dry and the pier end over water, as the planner sets
 * one; the <b>walkway</b> is given what a town gives it, two house fronts (solid to its deck, just outside the plot's
 * front and back) and its ends at their doors.
 */
final class Demos {
	private Demos() {
	}

	/** A spec and the land it is measured on. */
	record Case(PlotSpec spec, TerrainProbe probe) {
	}

	static Case of(ArchetypeInfo info, Style style, long seed, int index, TerrainProbe probe, String terrain) {
		PlotSpec spec = BlueprintPreview.spec(info, style, seed, index, probe, new CompoundTag());
		String path = info.id().getPath();
		if (path.equals("infra/dock") && (terrain.equals("coast") || terrain.equals("river"))) {
			PlotSpec shore = shore(spec, probe);
			return new Case(shore != null ? shore : spec, probe);
		}
		if (path.equals("infra/walkway")) {
			return walkway(spec, probe);
		}
		return new Case(spec, probe);
	}

	/** The dock moved and turned until its front (the road) is dry and its back (the pier head) is over water. */
	private static @Nullable PlotSpec shore(PlotSpec spec, TerrainProbe probe) {
		Direction[] turns = {spec.facing(), spec.facing().getClockWise(), spec.facing().getOpposite(), spec.facing().getCounterClockWise()};
		for (int r = 0; r <= 96; r += 2) {
			for (Direction f : turns) {
				for (int sign : new int[]{1, -1}) {
					int dx = f.getAxis() == Direction.Axis.X ? sign * r : 0;
					int dz = f.getAxis() == Direction.Axis.Z ? sign * r : 0;
					boolean zAxis = f.getAxis() == Direction.Axis.Z;
					int frontage = zAxis ? spec.sizeX() : spec.sizeZ();
					int depth = zAxis ? spec.sizeZ() : spec.sizeX();
					int sizeX = zAxis ? frontage : depth;
					int sizeZ = zAxis ? depth : frontage;
					BlockPos origin = new BlockPos(-sizeX / 2 + dx, 0, -sizeZ / 2 + dz);
					PlotSpec probeSpec = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), origin, sizeX, sizeZ, f, spec.maxHeight(), spec.tier(),
						spec.params());
					LocalFrame lf = new LocalFrame(probeSpec);
					int cx = frontage / 2;
					int sx = lf.worldX(cx, -1);
					int sz = lf.worldZ(cx, -1);
					int bx = lf.worldX(cx, depth - 1);
					int bz = lf.worldZ(cx, depth - 1);
					if (probe.isWater(sx, sz) || !probe.isWater(bx, bz) || probe.isWater(lf.worldX(cx, 0), lf.worldZ(cx, 0))) {
						continue;
					}
					int floor = probe.groundY(sx, sz);
					return new PlotSpec(spec.archetype(), spec.style(), spec.seed(), new BlockPos(origin.getX(), floor, origin.getZ()), sizeX, sizeZ, f,
						spec.maxHeight(), spec.tier(), spec.params());
				}
			}
		}
		return null;
	}

	/** The walkway between two house fronts: solid to its deck just outside the plot's front and back, its ends at their doors. */
	private static Case walkway(PlotSpec spec, TerrainProbe probe) {
		LocalFrame lf = new LocalFrame(spec);
		int deck = Math.min(spec.floorY() + 8, spec.floorY() + spec.maxHeight() - 5);
		int cx = lf.frontage() / 2;
		CompoundTag params = spec.params().copy();
		params.putIntArray("points", new int[]{lf.worldX(cx, lf.depth() - 1), deck, lf.worldZ(cx, lf.depth() - 1), lf.worldX(cx, 0), deck, lf.worldZ(cx, 0)});
		params.putInt("deck_y", deck);
		params.putInt("street_y", spec.floorY());
		PlotSpec s = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), spec.maxHeight(),
			spec.tier(), params);
		return new Case(s, new Fronts(probe, lf, deck));
	}

	/** The land with two house fronts on it: solid up to the deck in the rows just outside a plot's front and back. */
	private record Fronts(TerrainProbe base, LocalFrame lf, int deck) implements TerrainProbe {
		private boolean front(int x, int z) {
			int lx = this.lf.localX(x, z);
			int lz = this.lf.localZ(x, z);
			return lx >= -1 && lx <= this.lf.frontage() && (lz == -1 || lz == this.lf.depth());
		}

		@Override
		public long seed() {
			return this.base.seed();
		}

		@Override
		public int minY() {
			return this.base.minY();
		}

		@Override
		public int maxY() {
			return this.base.maxY();
		}

		@Override
		public int seaLevel() {
			return this.base.seaLevel();
		}

		@Override
		public int groundY(int x, int z) {
			return front(x, z) ? Math.max(this.deck, this.base.groundY(x, z)) : this.base.groundY(x, z);
		}

		@Override
		public int waterY(int x, int z) {
			return front(x, z) ? NO_WATER : this.base.waterY(x, z);
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return this.base.biome(x, z);
		}

		@Override
		public float temperature(int x, int z) {
			return this.base.temperature(x, z);
		}

		@Override
		public @Nullable UndergroundProbe underground() {
			return this.base.underground();
		}
	}
}
