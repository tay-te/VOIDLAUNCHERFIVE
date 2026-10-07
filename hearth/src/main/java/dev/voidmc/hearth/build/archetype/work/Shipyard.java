package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.craft.Hatch;
import dev.voidmc.hearth.build.kit.craft.Rails;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Ship;
import dev.voidmc.hearth.build.kit.industry.Timber;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/shipyard}: the boatwrights' yard on the water. A slipway runs from the yard down into the water at
 * the back, its ways two lines of timber, and on it a ship stands on the stocks: the keel on its blocks, the stem
 * rearing at the bow toward the street, the frames standing up from the keel like the bones of a fish, the strakes of
 * planking closing them in from the keel upward (the fore part planked to the sheer, the after part still bare ribs),
 * shores propping her bilges, a staging of planks along her side for the shipwrights. A crane leans over the slip with a
 * baulk on its chain. Down one side the timber racks of seasoning planks and the stacked keel timber; by the street the
 * sail loft, its upper floor spread with sail cloth, a loading door and hoist in its gable; a steam box over a fire for
 * bending planks, a pitch kettle, coils of rope in barrels. By culture:
 * <ul>
 *   <li><b>stocks</b> (Bohemia's rivers, the wisteria vale, the bayou): as above;</li>
 *   <li><b>boathouse</b> (the north, the moor, the coasts, the mountains' lakes): the hull built under a long open shed
 *   (a naust) over the slip, its roof on posts, the timber racks along the side, no crane;</li>
 *   <li><b>beach</b> (the Mediterranean, the dry lands, the steppe's rivers): the hull on the strand with no loft, a rope
 *   walk along the side (posts carrying the rope being laid) and the pitch kettle under a reed shade;</li>
 *   <li><b>junk</b> (the karst, the tropics): a broad, shallow flat-bottomed hull with staging along both sides.</li>
 * </ul>
 *
 * <p>Job sites (BOATWRIGHT): on the staging and at the hull's side, at the timber racks, at the steam box, at the sail
 * loft's bench. Storage: chests in the sail loft, barrels of pitch and nails.
 *
 * <p>Params: {@code plan} ({@code stocks}, {@code boathouse}, {@code beach}, {@code junk}; default by culture),
 * {@code roof} (the loft's form), {@code variant}.
 */
public final class Shipyard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/shipyard"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BOATWRIGHT), 0,
		11, 17, 15, 25, 9, 15, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.WATERFRONT), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.SALTBOX, RoofForm.PAGODA,
		RoofForm.FLAT};

	/** How a land builds its boats. */
	private enum Plan {
		STOCKS, BOATHOUSE, BEACH, JUNK
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	private static Plan plan(Draft d) {
		String p = d.param("plan", "");
		if (!p.isEmpty()) {
			try {
				return Plan.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// by the culture below
			}
		}
		return switch (Works.culture(d)) {
			case NORTHERN, TUNDRA, MARITIME, MOORLAND, ALPINE -> Plan.BOATHOUSE;
			case MEDITERRANEAN, DESERT, MESA, STEPPE -> Plan.BEACH;
			case KARST, TROPICAL -> Plan.JUNK;
			default -> Plan.STOCKS;
		};
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		Plan plan = plan(d);
		int ex = Yard.entrance(d);
		boolean loftLeft = d.chance(0.5);
		Mat logs = Works.logs(d);
		Mat plank = Works.timber(d);
		Mat frame = d.mat(Role.FRAME);

		// where the water begins at the back: the rows from the back that are mostly water (the yard keeps twelve rows
		// at the least; water anywhere else on the plot is filled)
		int slipX = loftLeft ? w - 1 - Math.max(3, (w - 6) / 2) : Math.max(3, (w - 6) / 2);
		int wetRows = 0;
		for (int z = dp - 1; z >= 0; z--) {
			int wet = 0;
			for (int x = 0; x < w; x++) {
				if (d.site().isWater(x, z)) {
					wet++;
				}
			}
			if (wet * 5 < w * 3) {
				break;
			}
			wetRows++;
		}
		int zw = Math.max(dp - wetRows, Math.min(dp, 12));
		Groundwork.level(d, 0, 0, w - 1, zw - 1, 0, true);
		Yard.trodden(d, 0, 0, w - 1, zw - 1, 0);

		// the slip: the ways along it on the yard, sloping on into the water
		Mat stone = Works.rubble(d);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = 3; z < dp; z++) {
				for (int k : new int[]{-1, 1}) {
					int x = slipX + k;
					if (z < zw) {
						d.set(x, -1, z, logs.log(Direction.Axis.Z));
					} else {
						int y = -1 - (z - zw + 1) / 2;
						int g = d.site().ground(x, z);
						if (y < g - 1) {
							break;
						}
						for (int yy = g - 1; yy <= y; yy++) {
							d.set(x, yy, z, yy == y ? logs.log(Direction.Axis.Z) : stone.full());
						}
					}
				}
			}
		}
		// the ship on the stocks: bow toward the street, stern over the water's edge
		int zBow = 4;
		int zStern = Math.min(dp - 1, zw + 1);
		zStern = Math.min(zStern, zBow + 16);
		int halfBeam = (w >= 15 ? 3 : 2) + (plan == Plan.JUNK && w >= 13 ? 1 : 0);
		int depth = Math.max(3, Math.min(plan == Plan.JUNK ? 3 : 4, d.maxHeight() - 6));
		int top = depth + 3;
		if (zStern - zBow >= 6) {
			// the keel blocks
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int z = zBow; z <= zStern; z += 2) {
					if (z < zw) {
						d.set(slipX, 0, z, stone.full());
					}
				}
			}
			Ship.Hull hull = Ship.hull(d, slipX, zBow, zStern, halfBeam, depth, 1, d.between(45, 70) / 100.0, plank, frame);
			Ship.shores(d, hull, 0, 3, frame);
			top = Math.max(top, hull.top());
			staging(d, pois, hull, loftLeft);
			if (plan == Plan.JUNK) {
				staging(d, pois, hull, !loftLeft);
			}
			// the hull's gunwale railed where the shipwrights walk her deck over the drop to the slip
			for (int y = 1; y <= hull.top() + 1; y++) {
				Rails.guard(d, slipX - halfBeam - 1, zBow - 1, slipX + halfBeam + 1, zStern + 1, y, List.of());
			}
		}

		// the boathouse: a long open shed down the side of the slip where the sails and gear are kept and strakes are bent, its
		// back and far side boarded (a roof over the hull itself would close her hold into a room nobody enters)
		int sx0 = loftLeft ? 0 : Math.max(0, w - 5);
		int sx1 = loftLeft ? Math.min(w - 1, 4) : w - 1;
		boolean shedFits = sx1 - sx0 >= 3 && (loftLeft ? sx1 < slipX - halfBeam - 2 : sx0 > slipX + halfBeam + 2);
		if (plan == Plan.BOATHOUSE && shedFits) {
			int sh = Math.min(4, d.maxHeight() - 5);
			RoofForm sf = Hall.pick(d, RoofForm.GABLE, RoofForm.GABLE, RoofForm.HALF_HIP);
			Roofs.Roof shed = Shed.open(d, sx0, 3, sx1, Math.min(zw - 1, 14), 0, sh, sf, Direction.Axis.Z, 1, loftLeft ? Dirs.LEFT : Dirs.RIGHT, Dirs.BACK);
			top = Math.max(top, shed != null ? shed.ridgeY : sh + 1);
		}

		// the sail loft at the front corner on its side (none on a beach, nor where the boathouse stands)
		int lw = Math.min(6, Math.max(5, w - (2 * halfBeam + 6)));
		int lx0 = loftLeft ? 0 : w - lw;
		int lx1 = lx0 + lw - 1;
		int lz0 = 2;
		int lz1 = Math.min(zw - 1, lz0 + 6);
		RoofForm form = Hall.pick(d, FORMS);
		if (plan != Plan.BEACH && plan != Plan.BOATHOUSE && lx1 - lx0 >= 4 && lz1 - lz0 >= 4 && Math.abs(slipX - (lx0 + lx1) / 2) > halfBeam + 3) {
			top = Math.max(top, loft(d, pois, lx0, lz0, lx1, lz1, form, loftLeft));
		} else if (plan == Plan.BEACH) {
			ropeWalk(d, pois, loftLeft ? 1 : w - 2, 2, Math.min(zw - 2, 12));
		}

		// down the far side: timber racks and the keel timber
		int rx = loftLeft ? w - 1 : 0;
		int rz0 = 3;
		int rz1 = Math.min(zw - 2, rz0 + 7);
		if (rz1 - rz0 >= 3 && Math.abs(rx - slipX) > halfBeam + 2) {
			Timber.rack(d, rx, 0, rz0, Dirs.BACK, rz1 - rz0 + 1, d.mat(Role.FLOOR));
			pois.siteIf(d, rx + (loftLeft ? -1 : 1), 0, (rz0 + rz1) / 2, loftLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.BOATWRIGHT);
		}
		int kx = loftLeft ? w - 3 : 1;
		if (rz1 + 2 < zw - 1 && Math.abs(kx - slipX) > halfBeam + 1) {
			Timber.deck(d, kx, 0, rz1 + 2, Direction.Axis.Z, Math.min(5, zw - rz1 - 3), 2, 2, logs);
		}
		// the steam box over its fire and the pitch kettle by the slip's head
		int bx = slipX + (loftLeft ? halfBeam + 2 : -halfBeam - 2);
		int bz = 2;
		if (Works.in(d, bx, bz) && d.isEmpty(bx, 0, bz) && Math.abs(bx - ex) > 1) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(bx, -1, bz, States.lit(Blocks.CAMPFIRE, true));
				d.set(bx, 0, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			}
			if (plan == Plan.BEACH) {
				// the pitch kettle under a reed shade
				dev.voidmc.hearth.build.kit.homestead.Adobe.ramada(d, bx - 1, bz - 1, bx + 1, bz + 1, 0);
			}
			pois.siteIf(d, bx, 0, bz - 1, Dirs.BACK, JobType.BOATWRIGHT);
			pois.store(d, bx + (loftLeft ? 1 : -1), 0, bz, Dirs.FRONT, false, Works.items("minecraft:string", 32, "minecraft:iron_nugget", 64));
		}
		// a crane over the slip's head lifting a baulk
		int cx = slipX + (loftLeft ? -halfBeam - 2 : halfBeam + 2);
		int cz = Math.max(2, zBow - 1);
		if ((plan == Plan.STOCKS || plan == Plan.JUNK) && Works.in(d, cx, cz) && d.isEmpty(cx, 0, cz) && Math.abs(cx - ex) > 1) {
			int jib = Lifts.crane(d, cx, cz, 0, Math.min(9, d.maxHeight() - 2), loftLeft ? Dirs.RIGHT : Dirs.LEFT, Math.abs(cx - slipX),
				logs.log(Direction.Axis.Z).at(slipX, 2, cz));
			if (jib != Integer.MIN_VALUE) {
				top = Math.max(top, jib + 1);
			}
		}
		// the yard's fence along the street, the sign, a lamp at the slip's head
		List<int[]> gaps = new java.util.ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		int sgx = ex + (loftLeft ? 2 : -2);
		if (Works.in(d, sgx, 0)) {
			Signs.post(d, sgx, 0, 0, loftLeft ? Dirs.RIGHT : Dirs.LEFT, 3, "shipyard", "Shipwright");
		}
		Works.lampPost(d, slipX + (loftLeft ? halfBeam + 2 : -halfBeam - 2), 0, Math.min(zw - 2, zBow + 5));
		Works.keepDoors(d);
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{ex, 0, 1}, new int[]{ex, 0, 2});
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, Math.min(dp, zw + 1) - 1, Math.min(d.maxHeight() - 1, top + 1));
	}

	/**
	 * A rope walk along column x from z0 to z1: posts every other cell carrying the rope being laid (a beam of stripped
	 * wood at shoulder height), the spinning wheel (a grindstone) at its head, barrels of hemp. The ropemaker walks it.
	 */
	private static void ropeWalk(Draft d, Pois pois, int x, int z0, int z1) {
		Mat post = Hall.woodFence(d);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0 + 1; z <= z1; z++) {
				if (!Works.in(d, x, z) || !d.isEmpty(x, 0, z) || !d.isEmpty(x, 1, z)) {
					continue;
				}
				if ((z - z0) % 2 == 1) {
					d.set(x, 0, z, post.fence());
				}
				d.set(x, 1, z, beam.log(Direction.Axis.Z));
			}
		}
		if (Works.in(d, x, z0) && d.isEmpty(x, 0, z0)) {
			pois.grindstone(d, x, 0, z0, Dirs.FRONT, JobType.BOATWRIGHT);
		}
		pois.store(d, x, 0, z1 + 1, Dirs.FRONT, false, Works.items("minecraft:string", 64, "minecraft:lead", 8));
	}

	/** A staging of planks along the hull's side on posts, reached by a ladder, the shipwrights' places on it and below. */
	private static void staging(Draft d, Pois pois, Ship.Hull h, boolean loftLeft) {
		int side = loftLeft ? -1 : 1;
		Mat plank = Works.timber(d);
		Mat post = Hall.woodFence(d);
		int x = h.cx() + side * (h.halfBeam() + 2);
		int z0 = h.zBow() + 2;
		int z1 = Math.min(h.zStern() - 1, z0 + 5);
		if (!Works.in(d, x, z0) || !Works.in(d, x, z1)) {
			return;
		}
		int y = Math.min(h.depth() - 1, 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				if (!d.isEmpty(x, y, z)) {
					continue;
				}
				d.set(x, y, z, plank.slab(SlabType.TOP));
				if (z == z0 || z == z1 || (z - z0) % 3 == 0) {
					for (int yy = 0; yy < y; yy++) {
						if (d.isEmpty(x, yy, z)) {
							d.set(x, yy, z, post.fence());
						}
					}
				}
			}
		}
		pois.siteIf(d, x, 0, (z0 + z1) / 2 + 1, side > 0 ? Dirs.LEFT : Dirs.RIGHT, JobType.BOATWRIGHT);
		pois.siteIf(d, x - side, 0, z0 - 1, Dirs.BACK, JobType.BOATWRIGHT);
	}

	/**
	 * The sail loft: two floors of the culture's walls, the street door below, a loading door with a hoist in the front
	 * gable above; below the chandlery (ropes, chests of nails and pitch), above the loft floor spread with sail cloth.
	 * Returns its top.
	 */
	private static int loft(Draft d, Pois pois, int x0, int z0, int x1, int z1, RoofForm form, boolean left) {
		int[] heights = Hall.budget(d, x0, z0, x1, z1, 0, new int[]{4, 4}, false, form, 1, Direction.Axis.Z);
		Hall h = Hall.of(d, x0, z0, x1, z1, 0, heights, false, false);
		int door = (x0 + x1) / 2;
		h.facade(door, 1, 1, 0.4).ground(false).shell().openings(true);
		door = h.door != null ? h.door : door;
		int[] well = h.storeys() >= 2 ? h.switchback(0, left ? Dirs.LEFT : Dirs.RIGHT) : null;
		h.roof(form, Direction.Axis.Z, 1, null);
		// where the turning stair will not fit, a ladder up through a hatch; the stairwell railed on the loft floor
		int[] ladder = h.storeys() >= 2 && well == null ? Hatch.ladder(d, h, 1, null, Dirs.BACK, left ? Dirs.LEFT : Dirs.RIGHT) : null;
		if (h.storeys() >= 2 && well == null && ladder == null) {
			h.openLoft(1);
		}
		Rails.well(d, h, 0, well);
		int[] in = h.inner(0);
		Floorplan fp = h.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		if (well != null) {
			for (int x = well[4]; x <= well[6]; x++) {
				for (int z = well[5]; z <= well[7]; z++) {
					if (x != well[0] || z != well[1]) {
						fp.block(x, z);
					}
				}
			}
			fp.keep(well[0], well[1]);
		}
		if (ladder != null) {
			fp.keep(ladder[0], ladder[1]);
		}
		// below: chests of pitch and nails, coils of rope, the bench
		for (Direction wall : new Direction[]{Dirs.BACK, left ? Dirs.RIGHT : Dirs.LEFT}) {
			for (int[] c : fp.along(wall)) {
				if (fp.take(c[0], c[1], wall.getOpposite())) {
					pois.store(d, c[0], 0, c[1], wall.getOpposite(), true, Works.items("minecraft:iron_nugget", 64, "minecraft:string", 64,
						"minecraft:white_wool", 32));
					break;
				}
			}
		}
		for (int[] c : fp.along(Dirs.BACK)) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				pois.station(d, Blocks.LOOM, c[0], 0, c[1], Dirs.FRONT, JobType.BOATWRIGHT);
				break;
			}
		}
		Lighting.room(d, fp.room());
		// above: the sail cloth spread on the loft floor
		if (h.storeys() >= 2) {
			int[] up = h.inner(1);
			int y = h.floorY(1);
			Block cloth = Blocks.CARPET.pick(DyeColor.WHITE);
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x = up[0]; x <= up[2]; x++) {
					for (int z = up[1]; z <= up[3]; z++) {
						boolean hole = well != null && x >= Math.min(well[4], well[6]) && x <= Math.max(well[4], well[6]) && z >= Math.min(well[5], well[7])
							&& z <= Math.max(well[5], well[7]) || ladder != null && Math.abs(x - ladder[0]) + Math.abs(z - ladder[1]) <= 1;
						if (!hole && d.isEmpty(x, y, z) && d.isSolid(x, y - 1, z) && (x + z) % 3 != 0) {
							d.set(x, y, z, cloth.defaultBlockState());
						}
					}
				}
			}
			Lighting.room(d, up[0], up[1], up[2], up[3], y);
			// the loading door and hoist in the front gable
			var front = h.side(1, Dirs.FRONT);
			int i = front.middle();
			if (h.roof != null) {
				Lifts.hoist(d, front, i, Math.min(h.roof.ridgeY - 1, y + 3), 2, 2, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
			}
		}
		return h.top;
	}
}
