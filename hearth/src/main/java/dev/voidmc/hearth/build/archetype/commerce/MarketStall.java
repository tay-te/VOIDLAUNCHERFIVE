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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.sociallife.Fair;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/market_stall}: a merchant's stall on a square or a high street, as a market day puts them up. A
 * low trestle of boards across the front with the day's goods laid out on it (one trade to a stall: produce, pots,
 * grain, cloth, candles, bread), under a canopy two high on four poles: undyed linen, or boards, leaning a little up
 * toward the back; crates, sacks and baskets on the ground at its ends and stock behind. Nothing closes it in, so a
 * square of ten reads as a market and not a fairground. The customer's place in front of the trestle is the
 * {@code MARKET_STALL}, the merchant's behind it the {@code COUNTER}, and a barrel at the back is the merchant's job site.
 *
 * <p>This is the worked example in docs/BUILDING_GUIDE.md: small, but it uses the parts every archetype does (info,
 * params, levelling, materials by role, a canopy, furniture, POIs, an entrance, clearing).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code canopy} (string: {@code linen}, {@code boards}, {@code striped}): what the canopy is (default by seed:
 *   linen mostly, boards often, striped canvas only in the mediterranean and the desert)</li>
 *   <li>{@code wares} (string: {@code produce}, {@code pots}, {@code grain}, {@code cloth}, {@code candles},
 *   {@code bread}): what the stall sells (default by seed)</li>
 * </ul>
 */
public final class MarketStall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/market_stall"), ArchetypeCategory.COMMERCE, Set.of(JobType.MERCHANT), 0,
		4, 7, 4, 6, 4, 7, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	private static final String[] WARES = {"produce", "pots", "grain", "cloth", "candles", "bread"};

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
		int x0 = 0;
		int x1 = w - 1;
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

		// four poles at the corners: two high on the street edge, three at the back where the canopy is higher
		Mat frame = d.mat(Role.FRAME);
		Mat pole = frame.has(Form.FENCE) ? frame : Hall.woodFence(d);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, 0}, {x1, 0}, {x0, zb}, {x1, zb}}) {
				int top = rise(p[1], zc, zb) == 2 ? 3 : 2;
				for (int y = 0; y < top; y++) {
					d.set(p[0], y, p[1], pole.fence());
				}
			}
		}

		// the canopy over the poles, from the street edge to the back, a half block up at a time
		canopy(d, x0, x1, zc, zb, frame);

		// the trestle: boards the width of the stall behind the front poles, a gap by one end to get behind it, the goods
		// on it
		Mat wood = d.mat(Role.FURNITURE);
		String trade = d.param("wares", WARES[(int) (Noise.unit(d.seed() ^ 0x3A7E, 0, 0, 0) * WARES.length) % WARES.length]);
		int gap = Noise.unit(d.seed() ^ 0x6A9, 1, 0, 0) < 0.5 ? x0 + 1 : x1 - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				if (x == gap) {
					continue;
				}
				d.set(x, 0, zc, wood.slab(SlabType.TOP));
				wares(d, trade, x, 1, zc);
			}
		}
		int mid = (x0 + x1) / 2 == gap ? (x0 + x1) / 2 + (gap == x0 + 1 ? 1 : -1) : (x0 + x1) / 2;
		d.poi(PoiType.MARKET_STALL, mid, 0, zc - 1, Dirs.BACK);
		d.poi(PoiType.COUNTER, mid, 0, zc + 1, Dirs.FRONT);

		// on the ground: a basket of the goods by a front pole, away from the gap and the customer's place
		int bx = gap == x0 + 1 ? x1 - 1 : x0 + 1;
		if (w >= 5 && bx != mid && bx != w / 2) {
			basket(d, trade, bx, 0, 0);
		}

		// stock at the back: the merchant's barrel (job site), a chest, crates and sacks between
		if (zb - zc >= 3) {
			Furniture.workstation(d, JobType.MERCHANT, x0 + 1, 0, zb, Dirs.FRONT);
			Furniture.chest(d, x1 - 1, 0, zb, Dirs.FRONT, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:apple", 4}));
			if (x1 - x0 >= 4) {
				Props.sacks(d, x0 + 2, 0, zb, Dirs.RIGHT, 1, trade.equals("bread"));
				Props.crates(d, x1 - 2, 0, zb, Dirs.LEFT, Math.min(2, x1 - x0 - 4));
			}
		} else {
			// a shallow stall: the merchant works from the trestle itself, his stock at his back
			int jx = gap == x0 + 1 ? x1 - 1 : x0 + 1;
			Furniture.workstation(d, JobType.MERCHANT, jx, 0, zb, Dirs.FRONT);
			int cx = jx == x0 + 1 ? x1 - 1 : x0 + 1;
			if (cx != jx && d.isEmpty(cx, 0, zb)) {
				Props.crates(d, cx, 0, zb, Dirs.RIGHT, 1);
			}
		}
		// a lantern hung under the high back of the canopy
		int lz = rise(zb - 1, zc, zb) == 2 ? zb - 1 : zb;
		Furniture.hangingLantern(d, mid, 3, lz, 0);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, 5));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * How far the canopy has risen over row z, in half blocks: 0 (a slab at two, over the street edge and the trestle), 1
	 * (a top slab at two) and 2 (a slab at three, over the back) by the last third.
	 */
	private static int rise(int z, int zc, int zb) {
		if (z <= zc) {
			return 0;
		}
		return (z - zc) / (double) (zb - zc) >= 0.6 ? 2 : 1;
	}

	/**
	 * The canopy over x0..x1 from the street edge (z 0) to the back row: slabs of undyed linen, of boards (the frame's
	 * timber), or in the south of canvas striped with the culture's one colour, stepping up a half block toward the back.
	 */
	private static void canopy(Draft d, int x0, int x1, int zc, int zb, Mat frame) {
		boolean south = Fair.striped(d);
		boolean boards = frame.has(Form.SLAB);
		double u = Noise.unit(d.seed() ^ 0xCA90, 2, 0, 0);
		String fallback = south && u < 0.6 ? "striped" : boards && u < 0.35 ? "boards" : "linen";
		String kind = d.param("canopy", fallback);
		if (kind.equals("boards") && !boards) {
			kind = "linen";
		}
		// linen is undyed: white, now and then the grey of a coarser weave
		DyeColor linen = Noise.unit(d.seed() ^ 0x11E, 3, 0, 0) < 0.25 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE;
		DyeColor dyed = DyeColor.byName(Fair.houseColour(d), DyeColor.RED);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = 0; z <= zb; z++) {
				int h = rise(z, zc, zb);
				int y = h == 2 ? 3 : 2;
				SlabType type = h == 1 ? SlabType.TOP : SlabType.BOTTOM;
				for (int x = x0; x <= x1; x++) {
					Paint p = switch (kind) {
						case "boards" -> frame.slab(type);
						case "striped" -> Paint.of(States.slab(Blocks.WOOL_SLAB.pick(((x - x0) & 1) == 0 ? DyeColor.WHITE : dyed), type));
						default -> Paint.of(States.slab(Blocks.WOOL_SLAB.pick(linen), type));
					};
					d.set(x, y, z, p);
				}
			}
		}
	}

	/** One of the day's goods on the trestle top at (x, y, z), by the stall's trade; now and then a gap in the row. */
	private static void wares(Draft d, String trade, int x, int y, int z) {
		double u = Noise.unit(d.seed(), x, y, z);
		BlockState b = switch (trade) {
			case "produce" -> u < 0.3 ? Blocks.MELON.defaultBlockState() : u < 0.55 ? Blocks.PUMPKIN.defaultBlockState()
				: u < 0.8 ? Blocks.COMPOSTER.defaultBlockState().trySetValue(BlockStateProperties.LEVEL_COMPOSTER, 7) : null;
			case "pots" -> u < 0.4 ? Blocks.DECORATED_POT.defaultBlockState() : u < 0.75 ? Blocks.FLOWER_POT.defaultBlockState() : null;
			case "grain" -> u < 0.45 ? States.slab(sack(false), SlabType.BOTTOM) : u < 0.7 ? Blocks.HAY_BLOCK.defaultBlockState()
				: u < 0.85 ? States.slab(sack(true), SlabType.BOTTOM) : null;
			case "cloth" -> u < 0.35 ? States.slab(Blocks.WOOL_SLAB.pick(DyeColor.WHITE), SlabType.BOTTOM)
				: u < 0.6 ? Blocks.CARPET.pick(u < 0.5 ? DyeColor.LIGHT_GRAY : DyeColor.BROWN).defaultBlockState()
				: u < 0.8 ? States.slab(Blocks.WOOL_SLAB.pick(DyeColor.byName(Fair.houseColour(d), DyeColor.RED)), SlabType.BOTTOM) : null;
			case "candles" -> u < 0.6 ? Blocks.CANDLE.defaultBlockState().trySetValue(BlockStateProperties.CANDLES, 1 + (int) (u * 6) % 4)
				: u < 0.8 ? Blocks.HONEYCOMB_BLOCK.defaultBlockState() : null;
			case "bread" -> u < 0.35 ? Blocks.CAKE.defaultBlockState() : u < 0.65 ? Blocks.HAY_BLOCK.defaultBlockState()
				: u < 0.8 ? States.slab(sack(true), SlabType.BOTTOM) : null;
			default -> null;
		};
		if (b == null) {
			if (trade.equals("produce") || trade.equals("pots")) {
				Garden.pot(d, x, y, z);
			}
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, b);
		}
	}

	/** A basket of the trade's goods on the ground at (x, y, z): a full composter, a decorated pot, a hay bale or a sack. */
	private static void basket(Draft d, String trade, int x, int y, int z) {
		BlockState b = switch (trade) {
			case "pots" -> Blocks.DECORATED_POT.defaultBlockState();
			case "grain", "bread" -> d.chance(0.5) ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.WOOL.pick(trade.equals("bread") ? DyeColor.WHITE : DyeColor.BROWN).defaultBlockState();
			default -> Blocks.COMPOSTER.defaultBlockState().trySetValue(BlockStateProperties.LEVEL_COMPOSTER, 7);
		};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, b);
		}
	}

	/** Sacking for a sack slab: brown for grain, white for flour. */
	private static Block sack(boolean flour) {
		return Blocks.WOOL_SLAB.pick(flour ? DyeColor.WHITE : DyeColor.BROWN);
	}
}
