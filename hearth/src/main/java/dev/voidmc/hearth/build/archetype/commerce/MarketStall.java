package dev.voidmc.hearth.build.archetype.commerce;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/market_stall}: a merchant's stall on a square or a high street. Four posts carry a striped cloth
 * awning that slopes down over the counter; the counter faces the street with wares on it and a gap at one end for the
 * merchant; crates, barrels and a chest of stock stand behind. The customer's place in front of the counter is the
 * {@code MARKET_STALL}, the merchant's behind it the {@code COUNTER}, and a barrel at the back is the merchant's job site.
 *
 * <p>This is the worked example in docs/BUILDING_GUIDE.md: small, but it uses the parts every archetype does (info,
 * params, levelling, materials by role, a roof field, furniture, POIs, an entrance, clearing).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code stripes} (boolean): stripe the awning with white (default by seed, mostly yes)</li>
 * </ul>
 */
public final class MarketStall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/market_stall"), ArchetypeCategory.COMMERCE, Set.of(JobType.MERCHANT), 0,
		4, 7, 4, 6, 4, 7, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		// customers come in from the street at the middle of the front
		d.entrance(w / 2, 0, -1);
		// the plot levelled to the floor, the stall paved
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int x0 = w >= 6 ? 1 : 0;
		int x1 = w >= 6 ? w - 2 : w - 1;
		int zc = 1;
		int zb = dp - 1;
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z <= zb; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}

		// four posts, three blocks high
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, zc}, {x1, zc}, {x0, zb}, {x1, zb}}) {
				for (int y = 0; y < 3; y++) {
					d.set(p[0], y, p[1], frame.log(Direction.Axis.Y));
				}
			}
		}

		// the awning: a lean-to field of cloth from above the back posts down over the counter and a block beyond
		Mat cloth = d.mat(Role.CLOTH);
		Roofs.Field awning = Roofs.shed(x0, 0, x1, zb, Dirs.BACK, Roofs.Profile.straight(0.5));
		Roofs.Roof roof = Roofs.raster(d, awning, 3, x0, 0, x1, zb, cloth, cloth, Habits.Ridge.SLAB);
		if (d.param("stripes", d.chance(0.75))) {
			stripe(d, x0, x1, 0, zb, roof);
		}
		// the posts carried up to meet it
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, zc}, {x1, zc}, {x0, zb}, {x1, zb}}) {
				for (int y = 3; y < roof.bottomY(p[0], p[1]); y++) {
					d.set(p[0], y, p[1], frame.log(Direction.Axis.Y));
				}
			}
		}

		// the counter: wares on slab tops and barrels, a gap at one end to get behind it
		Mat wood = d.mat(Role.FURNITURE);
		int gap = d.chance(0.5) ? x0 + 1 : x1 - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				if (x == gap) {
					continue;
				}
				if ((x - x0) % 3 == 2) {
					d.set(x, 0, zc, States.facing(Blocks.BARREL, Dirs.FRONT));
				} else {
					d.set(x, 0, zc, wood.slab(SlabType.TOP));
					wares(d, x, 1, zc);
				}
			}
		}
		int mid = (x0 + x1) / 2 == gap ? (x0 + x1) / 2 + (gap == x0 + 1 ? 1 : -1) : (x0 + x1) / 2;
		d.poi(PoiType.MARKET_STALL, mid, 0, zc - 1, Dirs.BACK);
		d.poi(PoiType.COUNTER, mid, 0, zc + 1, Dirs.FRONT);

		// stock at the back: the merchant's barrel (job site), a chest, crates
		if (zb - zc >= 3) {
			Furniture.workstation(d, JobType.MERCHANT, x0 + 1, 0, zb, Dirs.FRONT);
			Furniture.chest(d, x1 - 1, 0, zb, Dirs.FRONT, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:apple", 4}));
			if (x1 - x0 >= 4) {
				Garden.barrels(d, x0 + 2, 0, zb, Dirs.RIGHT, Math.min(2, x1 - x0 - 3));
			}
		} else {
			// a shallow stall: the merchant works from the counter itself
			Furniture.workstation(d, JobType.MERCHANT, gap == x0 + 1 ? x1 - 1 : x0 + 1, 0, zb, Dirs.FRONT);
		}
		// a lantern hung under the high side of the awning
		if (roof.covers(mid, zb - 1) && roof.bottomY(mid, zb - 1) >= 3) {
			Furniture.hangingLantern(d, mid, roof.bottomY(mid, zb - 1), zb - 1, 0);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, roof.ridgeY + 2);
	}

	/** Every other column of the awning in white cloth of the same shapes. */
	private static void stripe(Draft d, int x0, int x1, int z0, int z1, Roofs.Roof roof) {
		for (int x = x0 + 1; x <= x1; x += 2) {
			for (int z = z0; z <= z1; z++) {
				for (int y = roof.bottomY(x, z); y <= roof.surfaceY(x, z); y++) {
					BlockState s = d.get(x, y, z);
					if (s == null || s.isAir()) {
						continue;
					}
					BlockState white = s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
						? Blocks.WOOL_SLAB.pick(DyeColor.WHITE).withPropertiesOf(s)
						: s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.STAIRS_SHAPE)
						? Blocks.WOOL_STAIRS.pick(DyeColor.WHITE).withPropertiesOf(s)
						: Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState();
					d.restate(x, y, z, white);
				}
			}
		}
	}

	/** Something for sale on a counter top: a pot, a candle, a melon, nothing. */
	private static void wares(Draft d, int x, int y, int z) {
		double u = dev.voidmc.hearth.build.draft.Noise.unit(d.seed(), x, y, z);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (u < 0.3) {
				Garden.pot(d, x, y, z);
			} else if (u < 0.5) {
				d.set(x, y, z, Blocks.CANDLE.defaultBlockState());
			} else if (u < 0.65) {
				d.set(x, y, z, Blocks.DECORATED_POT.defaultBlockState());
			} else if (u < 0.75) {
				d.set(x, y, z, Blocks.MELON.defaultBlockState());
			}
		}
	}
}
