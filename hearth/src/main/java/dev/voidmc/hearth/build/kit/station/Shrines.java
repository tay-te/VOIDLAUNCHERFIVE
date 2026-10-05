package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Small places of devotion that stand in the open: a wayside shrine by a path, the way each culture raises one. A
 * pillar shrine of the Bohemian roads (a stone shaft, a lamp in its niche under a little roof), a carved post of the
 * north and the forest lands with a lamp on its arm, a stone lantern of the eastern gardens, a mud niche of the dry
 * lands with candles in it, a cairn of the steppe and the tundra with a pole. Each faces {@code face} (toward where folk
 * stand to pray), registers an ALTAR there, and has flowers set at its foot. It takes its own cell and the praying cell
 * in front, which stays clear.
 */
public final class Shrines {
	private Shrines() {
	}

	/** The kind of wayside shrine a culture raises. */
	public enum Kind {
		PILLAR, POST, LANTERN, NICHE, CAIRN
	}

	public static Kind kind(Draft d) {
		Culture c = d.style().culture() == null ? Culture.TEMPERATE : d.style().culture();
		return switch (c) {
			case TEMPERATE, ALPINE, MEDITERRANEAN, MARITIME, MOORLAND, WISTERIA -> Kind.PILLAR;
			case NORTHERN, BAYOU, TROPICAL -> Kind.POST;
			case KARST -> Kind.LANTERN;
			case DESERT, MESA -> Kind.NICHE;
			case STEPPE, TUNDRA -> Kind.CAIRN;
		};
	}

	/**
	 * A wayside shrine at (x, y, z) on its standing level, facing {@code face}; answers whether it stood (its cell and the
	 * praying cell in front must be free ground, and it must fit under the plot's height).
	 */
	public static boolean wayside(Draft d, int x, int y, int z, Direction face) {
		int fx = x + Dirs.dx(face);
		int fz = z + Dirs.dz(face);
		if (y + 4 >= d.maxHeight() || !inPlot(d, x, z) || !inPlot(d, fx, fz) || !Spots.standable(d, x, y, z) || !Spots.standable(d, fx, y, fz)
			|| Spots.claimed(d, x, y, z) || Spots.claimed(d, fx, y, fz) || !d.isEmpty(x, y + 2, z) || !d.isEmpty(x, y + 3, z)) {
			return false;
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat dressed = d.mat(Role.WALL_ACCENT).has(Form.WALL) ? d.mat(Role.WALL_ACCENT) : d.mat(Role.PLINTH).has(Form.WALL) ? d.mat(Role.PLINTH) : stone;
		Mat wood = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Direction side = Dirs.right(face);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			switch (kind(d)) {
				case PILLAR -> {
					d.set(x, y, z, dressed.full());
					d.set(x, y + 1, z, dressed.has(Form.WALL) ? dressed.wall() : dressed.full());
					d.set(x, y + 2, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
					d.set(x, y + 3, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : dressed.slab(SlabType.BOTTOM));
					if (roof.has(Form.STAIRS)) {
						for (Direction dir : new Direction[]{face, face.getOpposite()}) {
							int ox = x + Dirs.dx(dir);
							int oz = z + Dirs.dz(dir);
							if (inPlot(d, ox, oz) && d.isEmpty(ox, y + 3, oz)) {
								d.set(ox, y + 3, oz, roof.stairs(dir.getOpposite(), Half.BOTTOM));
							}
						}
					}
				}
				case POST -> {
					for (int k = 0; k < 3; k++) {
						d.set(x, y + k, z, wood.log(Direction.Axis.Y));
					}
					d.set(x, y + 3, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : wood.slab(SlabType.BOTTOM));
					int ax = x + Dirs.dx(side);
					int az = z + Dirs.dz(side);
					if (inPlot(d, ax, az) && d.isEmpty(ax, y + 2, az) && d.isEmpty(ax, y + 1, az)) {
						d.set(ax, y + 2, az, wood.fence());
					}
				}
				case LANTERN -> {
					d.set(x, y, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
					d.set(x, y + 2, z, (dressed.has(Form.SLAB) ? dressed : stone).slab(SlabType.BOTTOM));
				}
				case NICHE -> {
					Mat wall = d.mat(Role.WALL);
					d.set(x, y, z, wall.full());
					Mat cope = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : wall;
					d.set(x, y + 2, z, cope.has(Form.SLAB) ? cope.slab(SlabType.BOTTOM) : wall.full());
				}
				case CAIRN -> {
					d.set(x, y, z, stone.full());
					d.set(x, y + 1, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
					d.set(x, y + 2, z, wood.fence());
				}
			}
		}
		// its light
		switch (kind(d)) {
			case PILLAR -> {
				// the lamp hung under the little roof's front, over the head of one praying
				if (d.get(fx, y + 3, fz) != null && d.isEmpty(fx, y + 2, fz)) {
					Lighting.chandelier(d, fx, fz, y + 3, y + 2);
				}
			}
			case POST -> {
				int ax = x + Dirs.dx(side);
				int az = z + Dirs.dz(side);
				if (inPlot(d, ax, az) && d.get(ax, y + 2, az) != null && d.isEmpty(ax, y + 1, az)) {
					Lighting.chandelier(d, ax, az, y + 2, y + 1);
				}
			}
			case LANTERN -> Lighting.lamp(d, x, y + 1, z);
			case NICHE -> Lighting.candles(d, x, y + 1, z, 3);
			case CAIRN -> Lighting.lamp(d, x, y + 3, z);
		}
		for (Direction dir : new Direction[]{side, side.getOpposite()}) {
			int px = x + Dirs.dx(dir);
			int pz = z + Dirs.dz(dir);
			if (inPlot(d, px, pz) && Spots.standable(d, px, y, pz) && !Spots.claimed(d, px, y, pz) && d.isEmpty(px, y + 1, pz)) {
				Garden.flower(d, px, y, pz);
			}
		}
		d.poi(PoiType.ALTAR, fx, y, fz, face.getOpposite());
		return true;
	}

	/** Whether the cell is on the plot. */
	static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** A lit candle state (for callers that set their own). */
	static net.minecraft.world.level.block.state.BlockState candle() {
		return States.lit(Blocks.CANDLE, true);
	}
}
