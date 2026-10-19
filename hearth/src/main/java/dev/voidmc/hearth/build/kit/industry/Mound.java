package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The charcoal burner's clamp (a Meiler): billets of wood stood on end round a centre pole in a dome, covered over with
 * earth, turf and green branches, and fired from the top, so that it smoulders for days. Burning, it is a low round hill
 * of earth and leaves breathing smoke from the vent at its crown; being built, a dome of wood with its covering going on
 * from one side; burnt out, a black heap of charcoal raked flat and cooling in its ash.
 */
public final class Mound {
	private Mound() {
	}

	public enum State {
		BURNING, BUILDING, RAKED
	}

	/**
	 * A clamp centred on (cx, cz), radius r, {@code height} courses at the crown, standing on level y. Returns {x, z, y}
	 * of its crown (where a burning clamp's vent smokes).
	 */
	public static int[] clamp(Draft d, double cx, double cz, double r, int height, int y, State state) {
		int x0 = (int) Math.floor(cx - r);
		int x1 = (int) Math.ceil(cx + r);
		int z0 = (int) Math.floor(cz - r);
		int z1 = (int) Math.ceil(cz + r);
		int ccx = (int) Math.round(cx);
		int ccz = (int) Math.round(cz);
		Mat wood = Works.logs(d);
		Mat leaves = d.mat(Role.TREE_LEAVES);
		Block[] cover = cover(d);
		int crown = y;
		// the ground round it trodden black with soot and cinders
		soot(d, cx, cz, r + 1.3, y);
		if (state == State.RAKED) {
			raked(d, cx, cz, r, y);
			return new int[]{ccx, ccz, y + 1};
		}
		// which side the covering goes on from, for a clamp being built
		double coverFrom = Noise.unit(d.seed() ^ 0xC0E7, ccx, y, ccz) * Math.PI * 2;
		boolean dry = Works.dry(d);
		Paint log = wood.log(Direction.Axis.Y);
		Paint earth = (x, yy, z) -> Heap.pick(d, 17, x, yy, z, cover);
		Paint skin;
		if (state == State.BURNING) {
			skin = earth;
		} else {
			skin = (x, yy, z) -> {
				double a = Math.atan2(z - cz, x - cx);
				double diff = Math.abs(Math.atan2(Math.sin(a - coverFrom), Math.cos(a - coverFrom)));
				return diff < 1.1 && Math.hypot(x - cx, z - cz) > r * 0.3 ? earth.at(x, yy, z) : log.at(x, yy, z);
			};
		}
		// the slopes
		// mostly the earth itself in whole sods; here and there a streak of soot where the smoke has broken through
		Shape.Picker turfStairs = (x, yy, z) -> {
			double u = Noise.unit(d.seed() ^ 0x5EE, x, yy, z);
			return u < 0.18 ? Blocks.BLACKSTONE_STAIRS : u < 0.3 ? Blocks.MUD_BRICK_STAIRS : null;
		};
		Shape.Picker turfSlab = (x, yy, z) -> Noise.unit(d.seed() ^ 0x5EE, x, yy, z) < 0.2 ? Blocks.BLACKSTONE_SLAB : null;
		Shape.Skin sk = state == State.BURNING ? new Shape.Skin(skin, turfStairs, turfSlab) : new Shape.Skin(skin, (Shape.Picker) null, null);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (Works.in(d, x, z) && Math.hypot(x - cx, z - cz) <= r) {
					Heap.footing(d, x, y, z, new Block[]{Blocks.COARSE_DIRT}, 3);
				}
			}
		}
		crown = Shape.body(d, state == State.BURNING ? Shape.mound(cx, cz, r + 0.45, height - 0.6) : Shape.dome(cx, cz, r, height), y, x0, z0, x1, z1, sk, log.inStage(StageKind.FRAME), StageKind.EXTERIOR);
		if (crown == Integer.MIN_VALUE) {
			crown = y;
		}
		// a sod stair with more mound over it is a whole sod (a step nobody can stand on reads to folk as a stair
		// climbing into the mound's side)
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int yy = y; yy < crown; yy++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						var b = d.get(x, yy, z);
						var over = d.get(x, yy + 1, z);
						if (b != null && b.getBlock() instanceof net.minecraft.world.level.block.StairBlock && over != null && !over.isAir()) {
							var sod = skin.at(x, yy, z);
							if (sod != null) {
								d.set(x, yy, z, sod);
							}
						}
					}
				}
			}
		}
		if (state == State.BURNING) {
			// turf on the flats, green branches laid round the foot
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						double t = Math.hypot(x - cx, z - cz) / r;
						if (!Works.in(d, x, z) || t > 1) {
							continue;
						}
						if (t > 0.78 && leaves.has(Form.FULL) && Noise.unit(d.seed() ^ 0x1EAF, x, y, z) < 0.45) {
							var at = d.get(x, y, z);
							if (at != null && !at.is(Blocks.BLACKSTONE_STAIRS)) {
								d.set(x, y, z, leaves.leaves());
							}
						}
						if (!dry) {
							for (int yy = crown; yy >= y; yy--) {
								var b = d.get(x, yy, z);
								if (b != null && !b.isAir()) {
									if (b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
										&& !b.is(Blocks.BLACKSTONE) && d.isEmpty(x, yy + 1, z) && Noise.value(d.seed() ^ 0x7AF, x / 1.5, 0, z / 1.5) > 0.55) {
										d.set(x, yy + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
									}
									break;
								}
							}
						}
					}
				}
			}
		}
		if (state == State.BURNING) {
			// the vent at the crown, smouldering on straw so the smoke climbs
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(ccx, crown, ccz, Stack.fire());
				d.set(ccx, crown - 1, ccz, Blocks.HAY_BLOCK.defaultBlockState());
				// whole sods round the vent: the fire's cap is set over them, and a sod stair under it reads as the top of a
				// flight with no head room
				for (Direction n : Direction.Plane.HORIZONTAL) {
					var b = d.get(ccx + n.getStepX(), crown - 1, ccz + n.getStepZ());
					var sod = skin.at(ccx + n.getStepX(), crown - 1, ccz + n.getStepZ());
					if (b != null && b.getBlock() instanceof net.minecraft.world.level.block.StairBlock && sod != null) {
						d.set(ccx + n.getStepX(), crown - 1, ccz + n.getStepZ(), sod);
					}
				}
			}
		} else {
			// the centre pole the billets are stood round, standing out of the stack
			Mat pole = Works.timber(d);
			try (var s = d.stage(StageKind.FRAME)) {
				for (int k = 1; k <= 2; k++) {
					if (d.allowed(ccx, crown + k, ccz)) {
						d.set(ccx, crown + k, ccz, pole.has(Form.FENCE) ? pole.fence() : wood.log(Direction.Axis.Y));
					}
				}
			}
		}
		return new int[]{ccx, ccz, crown};
	}

	/** The earth a clamp is covered with: coarse earth, turf and rooted sods (sand and red earth in the dry lands). */
	public static Block[] cover(Draft d) {
		if (Works.dry(d)) {
			return new Block[]{Blocks.COARSE_DIRT, Blocks.PACKED_MUD, Blocks.BLACKSTONE, Blocks.COARSE_DIRT, Blocks.MUD};
		}
		return new Block[]{Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.MUD, Blocks.ROOTED_DIRT};
	}

	/** The ground round a clamp, blackened: cinders and soot in the trodden earth within radius r. */
	public static void soot(Draft d, double cx, double cz, double r, int y) {
		Block[] mix = {Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.BLACKSTONE, Blocks.PODZOL, Blocks.COARSE_DIRT};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
				for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
					if (!Works.in(d, x, z) || Math.hypot(x - cx, z - cz) > r || !d.isEmpty(x, y, z)) {
						continue;
					}
					if (!Garden.groundLike(d, x, y - 1, z) && !(d.isAbsent(x, y - 1, z) && d.site().ground(x, z) == y)) {
						continue;
					}
					d.set(x, y - 1, z, Heap.pick(d, 23, x, y - 1, z, mix));
				}
			}
		}
	}

	/** A burnt clamp raked open: a low heap of charcoal and cinders, ash round its edge. */
	private static void raked(Draft d, double cx, double cz, double r, int y) {
		Block[] coal = {Blocks.COAL_BLOCK, Blocks.BLACKSTONE, Blocks.COAL_BLOCK, Blocks.BLACKSTONE, Blocks.GRAVEL};
		BlockState slab = Blocks.BLACKSTONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM);
		Heap.heap(d, cx, cz, r, r * 0.8, 1.4, y, coal, (xx, yy, zz) -> slab, 31);
	}
}
