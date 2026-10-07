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
import dev.voidmc.hearth.build.kit.landscape.Edges;
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
 * grain, cloth, candles, bread), crates, sacks and baskets on the ground at its ends and stock behind; over it what the
 * culture's market puts up:
 * <ul>
 *   <li>the timber north (Bohemia, the fjords, the Alps, the moors, the coast, the bayou): a booth of boards, its back
 *   and low cheeks planked, a hood of shingles leaning up to the back;</li>
 *   <li>the dry lands (desert, mesa, steppe): a souk booth, a flat awning of striped canvas on four poles, cloth hung
 *   down its sides and back, rugs on the merchant's floor;</li>
 *   <li>the karst and the blossom vale: a lacquered pavilion stall, posts under a little hipped roof with its corners
 *   turned up, a paper lantern at the front;</li>
 *   <li>the mediterranean: a barrow on two wheels under a striped parasol;</li>
 *   <li>the tropics: a thatched gable on four poles.</li>
 * </ul>
 * Nothing closes it in at the front, so a square of ten reads as a market and not a fairground. The customer's place in
 * front of the trestle is the {@code MARKET_STALL}, the merchant's behind it the {@code COUNTER}, and a barrel at the
 * back is the merchant's job site.
 *
 * <p>This is the worked example in docs/BUILDING_GUIDE.md: small, but it uses the parts every archetype does (info,
 * params, levelling, materials by role, a canopy, furniture, POIs, an entrance, clearing).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code form} (string: {@code booth}, {@code souk}, {@code pavilion}, {@code barrow}, {@code thatch},
 *   {@code canopy}): the stall's build (default by culture; {@code canopy} is the plain linen canopy on four poles)</li>
 *   <li>{@code canopy} (string: {@code linen}, {@code boards}, {@code striped}): what the plain canopy is (default by seed:
 *   linen mostly, boards often, striped canvas one stall in five and only in the mediterranean and the desert)</li>
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
		Kind kind = kind(d);
		if (kind == Kind.BARROW && w < 5) {
			kind = Kind.CANOPY;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z <= zb; z++) {
				for (int x = x0; x <= x1; x++) {
					// the booth stands on a floor of boards behind its front row; everything else on the square's paving
					d.set(x, -1, z, kind == Kind.BOOTH && z >= zc ? d.mat(Role.FLOOR).full() : paving.full());
				}
			}
		}
		Mat frame = d.mat(Role.FRAME);
		Mat pole = frame.has(Form.FENCE) ? frame : Hall.woodFence(d);
		switch (kind) {
			case BOOTH -> booth(d, x0, x1, zc, zb, pole);
			case SOUK -> souk(d, x0, x1, zc, zb, pole);
			case PAVILION -> pavilion(d, x0, x1, zb, frame);
			case BARROW -> barrow(d, x0, x1, zc, zb, pole);
			case THATCH -> thatch(d, x0, x1, zb, pole);
			default -> {
				// four poles at the corners: two high on the street edge, three at the back where the canopy is higher
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
			}
		}

		// the trestle: boards the width of the stall behind the front poles, a gap by one end to get behind it, the goods
		// on it
		Mat wood = d.mat(Role.FURNITURE);
		String trade = d.param("wares", WARES[(int) (Noise.unit(d.seed() ^ 0x3A7E, 0, 0, 0) * WARES.length) % WARES.length]);
		int gap = Noise.unit(d.seed() ^ 0x6A9, 1, 0, 0) < 0.5 ? x0 + 1 : x1 - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				if (x == gap || kind == Kind.BARROW && (x <= x0 + 1 || x >= x1 - 1)) {
					continue;
				}
				// the barrow's bed is its own trestle (drawn with it); the others lay boards on trestles
				if (kind != Kind.BARROW) {
					d.set(x, 0, zc, wood.slab(SlabType.TOP));
				}
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
			if (kind == Kind.BOOTH) {
				// under the booth's planked back a chest's lid would not open: a cask of stock instead
				Furniture.barrel(d, x1 - 1, 0, zb, Dirs.FRONT);
			} else {
				Furniture.chest(d, x1 - 1, 0, zb, Dirs.FRONT, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:apple", 4}));
			}
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
		// a lantern hung under the roof (the pavilion hangs its paper lantern at the front, the barrow has none)
		if (kind == Kind.CANOPY) {
			int lz = rise(zb - 1, zc, zb) == 2 ? zb - 1 : zb;
			Furniture.hangingLantern(d, mid, 3, lz, 0);
		} else if (kind == Kind.BOOTH || kind == Kind.SOUK || kind == Kind.THATCH) {
			int lz = Math.max(zc + 1, zb - 1);
			if (d.isEmpty(mid, 2, lz) && !d.isEmpty(mid, 3, lz)) {
				Furniture.hangingLantern(d, mid, 3, lz, 0);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, 5));
		Edges.guard(d);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** What a culture's market puts up. */
	enum Kind {
		CANOPY, BOOTH, SOUK, PAVILION, BARROW, THATCH
	}

	static Kind kind(Draft d) {
		String p = d.param("form", "");
		if (!p.isEmpty()) {
			try {
				return Kind.valueOf(p.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// the culture's own
			}
		}
		return switch (dev.voidmc.hearth.build.kit.landscape.Manner.culture(d)) {
			case DESERT, MESA, STEPPE -> Kind.SOUK;
			case KARST, WISTERIA -> Kind.PAVILION;
			case MEDITERRANEAN -> Kind.BARROW;
			case TROPICAL -> Kind.THATCH;
			default -> Kind.BOOTH;
		};
	}

	/**
	 * The timber north's booth: the back planked full height, low cheeks of boards along the sides behind the trestle,
	 * posts at the front corners, and a hood of the roof's shingles over all of it leaning up from the street edge to the
	 * back, its eave out over the customer.
	 */
	private static void booth(Draft d, int x0, int x1, int zc, int zb, Mat pole) {
		Mat boards = d.mat(Role.FURNITURE);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{x0, x1}) {
				for (int y = 0; y < 2; y++) {
					d.set(x, y, 0, pole.fence());
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				for (int y = 0; y <= 2; y++) {
					d.set(x, y, zb, boards.full());
				}
			}
			for (int x : new int[]{x0, x1}) {
				for (int z = zc + 1; z < zb; z++) {
					d.set(x, 0, z, boards.full());
					d.set(x, 1, z, boards.slab(SlabType.BOTTOM));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			int half = (zb + 1) / 2;
			for (int z = 0; z <= zb; z++) {
				int y = z < half ? 2 : 3;
				for (int x = x0; x <= x1; x++) {
					d.set(x, y, z, roof.has(Form.STAIRS) ? roof.stairs(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.BOTTOM)
						: roof.slab(SlabType.BOTTOM));
				}
				if (y == 3) {
					// the step in the hood boarded under its edge
					for (int x = x0; x <= x1 && z == half; x++) {
						d.set(x, 2, z, boards.full());
					}
				}
			}
		}
	}

	/**
	 * The dry lands' souk booth: four poles, a flat awning of canvas striped in the culture's colour, cloth hung from its
	 * edge down the sides and across the back (a curtain's top, clear of the merchant's head), rugs on the floor behind the
	 * trestle.
	 */
	private static void souk(Draft d, int x0, int x1, int zc, int zb, Mat pole) {
		DyeColor dyed = DyeColor.byName(Fair.houseColour(d), DyeColor.RED);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, 0}, {x1, 0}, {x0, zb}, {x1, zb}}) {
				for (int y = 0; y < 3; y++) {
					d.set(p[0], y, p[1], pole.fence());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = 0; z <= zb; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, 3, z, States.slab(Blocks.WOOL_SLAB.pick(((x - x0) & 1) == 0 ? DyeColor.WHITE : dyed), SlabType.BOTTOM));
				}
			}
			// the hangings: down the sides and across the back, one block of cloth under the awning's edge
			for (int z = 1; z < zb; z++) {
				for (int x : new int[]{x0, x1}) {
					d.set(x, 2, z, Blocks.WOOL.pick(dyed).defaultBlockState());
				}
			}
			for (int x = x0 + 1; x < x1; x++) {
				d.set(x, 2, zb, Blocks.WOOL.pick((x & 1) == 0 ? dyed : DyeColor.WHITE).defaultBlockState());
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = zc + 1; z <= zb; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, 0, z, Blocks.CARPET.pick((x + z & 1) == 0 ? dyed : DyeColor.WHITE).defaultBlockState());
				}
			}
		}
	}

	/**
	 * The karst's and the blossom vale's pavilion stall: four lacquered posts, a little hipped roof of the culture's tiles
	 * with its eaves turned up at the corners, a paper lantern hung at the front.
	 */
	private static void pavilion(Draft d, int x0, int x1, int zb, Mat frame) {
		Mat roof = d.mat(Role.ROOF);
		int top = d.maxHeight() - 1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, 0}, {x1, 0}, {x0, zb}, {x1, zb}}) {
				for (int y = 0; y < 3; y++) {
					d.set(p[0], y, p[1], frame.log(Direction.Axis.Y).orElse(frame.full()));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = 0; z <= zb; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean ex = x == x0 || x == x1;
					boolean ez = z == 0 || z == zb;
					if (ex || ez) {
						// the eave: stairs falling outward, the corners turned up a half block
						Direction out = ez ? (z == 0 ? Dirs.FRONT : Dirs.BACK) : (x == x0 ? Dirs.LEFT : Dirs.RIGHT);
						d.set(x, 3, z, roof.has(Form.STAIRS) ? roof.stairs(out.getOpposite(), net.minecraft.world.level.block.state.properties.Half.BOTTOM)
							: roof.slab(SlabType.BOTTOM));
						if (ex && ez && top >= 4) {
							d.set(x, 4, z, roof.slab(SlabType.BOTTOM));
						}
					} else if (top >= 4) {
						boolean ridge = (z == zb / 2 || z == (zb + 1) / 2) && x > x0 + 1 && x < x1 - 1;
						d.set(x, 4, z, ridge && top >= 5 ? roof.full() : roof.slab(SlabType.BOTTOM));
						if (ridge && top >= 5) {
							d.set(x, 5, z, roof.slab(SlabType.BOTTOM));
						}
					} else {
						d.set(x, 3, z, roof.slab(SlabType.BOTTOM));
					}
				}
			}
		}
		// the paper lantern under the front eave
		int mx = (x0 + x1) / 2;
		if (d.isEmpty(mx, 2, 0)) {
			Furniture.hangingLantern(d, mx, 3, 0, 0);
		}
	}

	/**
	 * The mediterranean barrow: a cart on two wheels (its bed the trestle, a way round either end) under a parasol of
	 * striped canvas on a pole behind it.
	 */
	private static void barrow(Draft d, int x0, int x1, int zc, int zb, Mat pole) {
		Mat wood = d.mat(Role.FURNITURE);
		DyeColor dyed = DyeColor.byName(Fair.houseColour(d), DyeColor.RED);
		try (var s = d.stage(StageKind.INTERIOR)) {
			// the bed between the wheels, the wheels at its ends (dark log discs seen edge on), a way round either end
			for (int x = x0 + 2; x <= x1 - 2; x++) {
				d.set(x, 0, zc, wood.slab(SlabType.TOP));
			}
			for (int x : new int[]{x0 + 1, x1 - 1}) {
				d.set(x, 0, zc, States.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
			}
		}
		// the parasol: a pole behind the barrow, a canvas of three by three (or the stall's width) at three
		int px = (x0 + x1) / 2;
		int pz = Math.min(zb, zc + 2);
		int r = Math.min(2, Math.max(1, (x1 - x0) / 2));
		try (var s = d.stage(StageKind.FRAME)) {
			for (int y = 0; y < 3; y++) {
				d.set(px, y, pz, pole.fence());
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = pz - r; z <= pz + r; z++) {
				for (int x = px - r; x <= px + r; x++) {
					if (x < x0 || x > x1 || z < 0 || z > zb) {
						continue;
					}
					boolean rim = Math.abs(x - px) == r || Math.abs(z - pz) == r;
					d.set(x, 3, z, States.slab(Blocks.WOOL_SLAB.pick(((x + z) & 1) == 0 ? DyeColor.WHITE : dyed), rim ? SlabType.BOTTOM : SlabType.TOP));
				}
			}
			if (d.maxHeight() - 1 >= 4) {
				d.set(px, 4, pz, States.slab(Blocks.WOOL_SLAB.pick(dyed), SlabType.BOTTOM));
			}
		}
	}

	/** The tropical stall: four poles under a steep thatched gable, its ridge across the stall. */
	private static void thatch(Draft d, int x0, int x1, int zb, Mat pole) {
		Mat roof = d.mat(Role.ROOF);
		int top = d.maxHeight() - 1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, 0}, {x1, 0}, {x0, zb}, {x1, zb}}) {
				for (int y = 0; y < 2; y++) {
					d.set(p[0], y, p[1], pole.fence());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			int mid = zb / 2;
			for (int z = 0; z <= zb; z++) {
				int k = Math.min(z, zb - z);
				int y = Math.min(top, 2 + k);
				boolean ridge = zb % 2 == 0 ? z == mid : z == mid || z == mid + 1;
				Direction out = z <= mid ? Dirs.FRONT : Dirs.BACK;
				for (int x = x0; x <= x1; x++) {
					d.set(x, y, z, ridge ? roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : roof.full()
						: roof.has(Form.STAIRS) ? roof.stairs(out.getOpposite(), net.minecraft.world.level.block.state.properties.Half.BOTTOM) : roof.full());
					// the thatch thick under its slopes
					for (int yy = 2; yy < y; yy++) {
						if (x == x0 || x == x1) {
							d.set(x, yy, z, roof.full());
						}
					}
				}
			}
		}
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
	 * timber), or now and then in the south (one stall in five) of canvas striped with the culture's one colour, so a
	 * square of ten still reads as a linen market, stepping up a half block toward the back.
	 */
	private static void canopy(Draft d, int x0, int x1, int zc, int zb, Mat frame) {
		boolean south = Fair.striped(d);
		boolean boards = frame.has(Form.SLAB);
		double u = Noise.unit(d.seed() ^ 0xCA90, 2, 0, 0);
		String fallback = south && u < 0.2 ? "striped" : boards && u < 0.35 ? "boards" : "linen";
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
			case "grain", "bread" -> d.chance(0.5) ? Blocks.HAY_BLOCK.defaultBlockState() : States.slab(sack(trade.equals("bread")), SlabType.BOTTOM);
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
