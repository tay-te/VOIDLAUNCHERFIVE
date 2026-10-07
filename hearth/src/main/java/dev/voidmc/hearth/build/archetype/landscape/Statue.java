package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Inscriptions;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/statue}: the town's monument on a stepped plinth with a lettered sign.
 *
 * <ul>
 *   <li>A figure twice a man's height (temperate, wisteria, maritime, alpine, moorland, mediterranean, bayou): the
 *   founder with a sword raised and a cloak, a robed keeper holding up a lantern, an elder leaning on a staff, a
 *   scholar with an open book; in stone, white marble in the south and the blossom vale, bronze gone green in towns
 *   of the north and the coast, carved cypress in the bayou.</li>
 *   <li>An obelisk with a capstone (desert, mesa), a carved stele (steppe), a rune stone (northern, tundra), a seated
 *   sage in jade (karst), a tiki of carved palm (tropical).</li>
 * </ul>
 * The plinth: a step all round, a pedestal with a moulded cap, the sign on its front; on bigger plots a paved surround
 * with flowers, benches facing the monument and lamps.
 *
 * <p>POIs: a MEETING_POINT before it, SEATs on the benches. Params: {@code figure} ({@code founder}, {@code keeper},
 * {@code elder}, {@code scholar}; default by seed).
 */
public final class Statue implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/statue"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		5, 7, 5, 7, 7, 14, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** A carving material: the full block and its stairs, slab and wall where the game has them. */
	record Stone(Block full, Block stairs, Block slab, Block wall) {
		static Stone of(String base, String stairs, String slab, String wall, Block fallback) {
			Block f = States.block(base, fallback);
			return new Stone(f, States.block(stairs, f), States.block(slab, f), States.block(wall, f));
		}

		BlockState stair(Direction facing, Half half) {
			return States.stairs(this.stairs, facing, half);
		}

		BlockState slabState(SlabType t) {
			return States.slab(this.slab, t);
		}
	}

	enum Monument {
		FIGURE, OBELISK, STELE, RUNESTONE, SAGE, TIKI
	}

	static Monument monument(Draft d) {
		return switch (Manner.culture(d)) {
			case DESERT, MESA -> Monument.OBELISK;
			case STEPPE -> Monument.STELE;
			case NORTHERN, TUNDRA -> Monument.RUNESTONE;
			case KARST -> Monument.SAGE;
			case TROPICAL -> Monument.TIKI;
			default -> Monument.FIGURE;
		};
	}

	static Stone stone(Draft d) {
		Culture c = Manner.culture(d);
		boolean bronze = (c == Culture.TEMPERATE || c == Culture.MARITIME || c == Culture.ALPINE) && (d.tier() >= 3 || d.chance(0.35));
		if (bronze) {
			String age = c == Culture.MARITIME ? "oxidized" : "weathered";
			return Stone.of("minecraft:waxed_" + age + "_copper", "minecraft:waxed_" + age + "_cut_copper_stairs",
				"minecraft:waxed_" + age + "_cut_copper_slab", "minecraft:waxed_" + age + "_cut_copper", Blocks.STONE);
		}
		return switch (c) {
			case MEDITERRANEAN, WISTERIA -> Stone.of("minecraft:smooth_quartz", "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_slab",
				"minecraft:diorite_wall", Blocks.CALCITE);
			case KARST -> Stone.of("minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab",
				"minecraft:prismarine_wall", Blocks.STONE);
			case BAYOU -> {
				Mat f = d.mat(Role.FRAME);
				Block planks = f.mainOr(Form.FULL, Blocks.MANGROVE_PLANKS);
				yield new Stone(f.mainOr(Form.WOOD, planks), f.mainOr(Form.STAIRS, Blocks.MANGROVE_STAIRS), f.mainOr(Form.SLAB, Blocks.MANGROVE_SLAB),
					f.mainOr(Form.FENCE, Blocks.MANGROVE_FENCE));
			}
			default -> Stone.of("minecraft:polished_andesite", "minecraft:polished_andesite_stairs", "minecraft:polished_andesite_slab",
				"minecraft:andesite_wall", Blocks.STONE);
		};
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.Pattern.RINGS, cx, cz, 2, false));
		int m = (Math.min(w, dp) - 5) / 2;
		Monument kind = monument(d);
		int base = plinth(d, cx, cz, kind);
		int top = switch (kind) {
			case OBELISK -> obelisk(d, cx, base, cz);
			case STELE -> stele(d, cx, base, cz);
			case RUNESTONE -> runestone(d, cx, base, cz);
			case SAGE -> sage(d, cx, base, cz, stone(d));
			case TIKI -> tiki(d, cx, base, cz);
			default -> figure(d, cx, base, cz, stone(d));
		};
		d.poi(PoiType.MEETING_POINT, cx, 0, Math.max(0, cz - 3), Dirs.BACK);
		if (m >= 1) {
			// a surround: flowers at the corners, benches looking at it from the sides, lamps behind
			for (int[] c : new int[][]{{0, 0}, {w - 1, 0}}) {
				Planters.bed(d, c[0], 0, c[1]);
			}
			Benches.bench(d, 0, 0, cz, Dirs.RIGHT, Dirs.BACK, 1, false);
			Benches.bench(d, w - 1, 0, cz, Dirs.LEFT, Dirs.BACK, 1, false);
			Lamps.standard(d, 0, 0, dp - 1, 4, Manner.lamp(d), null);
			Lamps.standard(d, w - 1, 0, dp - 1, 4, Manner.lamp(d), null);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
		Edges.guard(d);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * The stepped plinth: a step all round, a base moulding, on a tall plot a die of two courses
	 * carrying the lettered sign, and a cornice overhanging it. Low monuments (rune stone, stele, tiki) stand on the step
	 * alone. Returns the monument's base y.
	 */
	private static int plinth(Draft d, int cx, int cz, Monument kind) {
		Mat mas = Manner.masonry(d);
		Mat accent = Paving.band(d);
		boolean low = kind == Monument.RUNESTONE || kind == Monument.STELE || kind == Monument.TIKI;
		boolean tall = !low && d.maxHeight() >= 13;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				for (int x = cx - 2; x <= cx + 2; x++) {
					boolean ring = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
					if (ring) {
						Direction out = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2 ? null
							: Math.abs(x - cx) == 2 ? (x < cx ? Dirs.LEFT : Dirs.RIGHT) : (z < cz ? Dirs.FRONT : Dirs.BACK);
						// (a low monument's step is all slabs: a stair would climb into the stone standing on it)
						d.set(x, 0, z, out == null || low || !mas.has(Form.STAIRS) ? mas.slab(SlabType.BOTTOM) : mas.stairs(out.getOpposite(), Half.BOTTOM));
					} else {
						d.set(x, 0, z, mas.full());
					}
				}
			}
			if (low) {
				return 1;
			}
			int capY = 1;
			if (tall) {
				// the base moulding, then the die, two courses high
				for (int z = cz - 1; z <= cz + 1; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						d.set(x, 1, z, accent.full());
						for (int y = 2; y <= 3; y++) {
							d.set(x, y, z, mas.full());
						}
					}
				}
				capY = 4;
			}
			// the cornice: the edge stones overhanging as upside-down stairs, a block in the middle
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					boolean edge = x != cx || z != cz;
					boolean corner = x != cx && z != cz;
					if (corner || !edge || !accent.has(Form.STAIRS)) {
						d.set(x, capY, z, accent.full());
					} else {
						Direction in = Dirs.nearest(cx - x, cz - z);
						d.set(x, capY, z, accent.stairs(in, Half.TOP));
					}
				}
			}
			Inscriptions.wallSign(d, cx, tall ? 2 : 1, cz - 2, Dirs.FRONT, inscription(d, kind));
			return capY + 1;
		}
	}

	private static List<String[]> inscription(Draft d, Monument kind) {
		return switch (kind) {
			case OBELISK -> List.of(new String[]{"obelisk.1", "To those who"}, new String[]{"obelisk.2", "raised this town"});
			case STELE -> List.of(new String[]{"stele.1", "The riders of"}, new String[]{"stele.2", "the first ring"});
			case RUNESTONE -> List.of(new String[]{"runestone.1", "Here the clan"}, new String[]{"runestone.2", "first made fire"});
			case SAGE -> List.of(new String[]{"sage.1", "Stillness,"}, new String[]{"sage.2", "and water"});
			case TIKI -> List.of(new String[]{"tiki.1", "Guardian of"}, new String[]{"tiki.2", "the lagoon"});
			default -> switch (figureKind(d)) {
				case "keeper" -> List.of(new String[]{"keeper.1", "The Keeper"}, new String[]{"keeper.2", "of the lantern"});
				case "elder" -> List.of(new String[]{"elder.1", "The Elder"}, new String[]{"elder.2", "who walked"}, new String[]{"elder.3", "the old roads"});
				case "scholar" -> List.of(new String[]{"scholar.1", "The Scholar"}, new String[]{"scholar.2", "who kept"}, new String[]{"scholar.3", "the records"});
				default -> List.of(new String[]{"founder.1", "The Founder"}, new String[]{"founder.2", "who lit the"}, new String[]{"founder.3", "first hearth"});
			};
		};
	}

	/** Which figure stands here (the same choice the inscription made: read back from the seed's first pick). */
	private static String figureKind(Draft d) {
		long h = dev.voidmc.hearth.build.draft.Noise.hash(d.seed(), 0xF16);
		String[] kinds = {"founder", "keeper", "elder", "scholar"};
		return d.param("figure", kinds[(int) Math.floorMod(h, 4L)]);
	}

	/**
	 * A figure some three times a man's height facing the front, its base on y, in the round: the body two rows deep
	 * (front row cz - 1, back row cz) so it reads as a person from any corner, not a slab. Legs apart (or a robe to the
	 * ground, its hem sloping), hips and a chest three across, shoulders sloping to a neck, the head standing a block
	 * clear of the shoulders with a cap of slab and the back of the head rounded; the arms stand off the torso, hanging,
	 * bent forward or raised; a cloak falls behind the founder. Returns its top.
	 */
	private static int figure(Draft d, int cx, int y, int cz, Stone st) {
		String kind = figureKind(d);
		BlockState full = st.full().defaultBlockState();
		BlockState limb = st.wall().defaultBlockState();
		BlockState head = headOf(st);
		BlockState rod = States.block("minecraft:waxed_lightning_rod", Blocks.END_ROD).defaultBlockState();
		boolean robed = kind.equals("keeper") || kind.equals("scholar");
		int zf = cz - 1;
		int zb = cz;
		int top = y + 7;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// rows 0-1: the legs apart (two deep), or the robe to the ground, its hem sloping forward at the foot
			for (int r = 0; r <= 1; r++) {
				for (int z = zf; z <= zb; z++) {
					if (robed) {
						for (int x = cx - 1; x <= cx + 1; x++) {
							d.set(x, y + r, z, r == 0 && z == zf ? st.stair(Dirs.BACK, Half.BOTTOM) : full);
						}
					} else {
						d.set(cx - 1, y + r, z, z == zf && r == 0 ? st.stair(Dirs.BACK, Half.BOTTOM) : limb);
						d.set(cx + 1, y + r, z, z == zf && r == 0 ? st.stair(Dirs.BACK, Half.BOTTOM) : limb);
					}
				}
			}
			// rows 2-4: the hips and the chest, three across and two deep
			for (int r = 2; r <= 4; r++) {
				for (int z = zf; z <= zb; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						d.set(x, y + r, z, full);
					}
				}
			}
			// row 5: the shoulders sloping in to the neck; row 6 the head, its back rounded; row 7 the cap
			for (int z = zf; z <= zb; z++) {
				d.set(cx - 1, y + 5, z, st.stair(Dirs.RIGHT, Half.BOTTOM));
				d.set(cx + 1, y + 5, z, st.stair(Dirs.LEFT, Half.BOTTOM));
			}
			d.set(cx, y + 5, zf, full);
			d.set(cx, y + 5, zb, st.stair(Dirs.FRONT, Half.BOTTOM));
			d.set(cx, y + 6, zf, head);
			d.set(cx, y + 6, zb, st.stair(Dirs.FRONT, Half.BOTTOM));
			if (d.allowed(cx, y + 7, zf)) {
				d.set(cx, y + 7, zf, st.slabState(SlabType.BOTTOM));
			}
			switch (kind) {
				case "keeper" -> {
					// the left arm hangs, the right holds a lantern high beside the head
					arm(d, cx - 2, y + 2, zf, st, false, Dirs.RIGHT);
					d.set(cx + 2, y + 4, zf, st.stair(Dirs.LEFT, Half.TOP));
					d.set(cx + 2, y + 5, zf, limb);
					d.set(cx + 2, y + 6, zf, full);
					if (d.allowed(cx + 2, y + 7, zf)) {
						d.set(cx + 2, y + 7, zf, States.lantern(Lamps.lantern(d), false));
					}
				}
				case "elder" -> {
					arm(d, cx - 2, y + 2, zf, st, false, Dirs.RIGHT);
					arm(d, cx + 2, y + 2, zf, st, true, Dirs.LEFT);
					// the staff in the right hand, from the plinth to over the head
					BlockState staff = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE).fence().at(cx + 2, y, zf - 1)
						: Lamps.post(d).at(cx + 2, y, zf - 1);
					for (int k = 0; k <= 7; k++) {
						if (d.allowed(cx + 2, y + k, zf - 1)) {
							d.set(cx + 2, y + k, zf - 1, staff);
						}
					}
				}
				case "scholar" -> {
					arm(d, cx - 2, y + 2, zf, st, true, Dirs.RIGHT);
					arm(d, cx + 2, y + 2, zf, st, true, Dirs.LEFT);
					// the open book held before the chest
					d.set(cx, y + 3, zf - 1, d.mat(Role.FURNITURE).trapdoor(Dirs.BACK, Half.TOP, false).at(cx, y + 3, zf - 1));
				}
				default -> {
					// the founder: a cloak falling behind from the shoulders to the calves, the left arm at the side, the
					// right raised with a sword
					for (int r = 1; r <= 5; r++) {
						for (int x = cx - 1; x <= cx + 1; x++) {
							d.set(x, y + r, zb + 1, r == 1 || r == 5 && x != cx ? st.stair(Dirs.FRONT, Half.BOTTOM) : full);
						}
					}
					arm(d, cx - 2, y + 2, zf, st, false, Dirs.RIGHT);
					d.set(cx + 2, y + 4, zf, st.stair(Dirs.LEFT, Half.TOP));
					d.set(cx + 2, y + 5, zf, limb);
					d.set(cx + 2, y + 6, zf, full);
					for (int k = 7; k <= 8; k++) {
						if (d.allowed(cx + 2, y + k, zf)) {
							d.set(cx + 2, y + k, zf, rod);
							top = Math.max(top, y + k);
						}
					}
				}
			}
		}
		return Math.min(d.maxHeight() - 1, top);
	}

	/**
	 * An arm at the figure's side from row y: a shoulder stair at the top leaning into the chest ({@code toChest}), the
	 * arm, and the hand: hanging, or bent forward with the forearm out before the body.
	 */
	private static void arm(Draft d, int x, int y, int z, Stone st, boolean forward, Direction toChest) {
		BlockState limb = st.wall().defaultBlockState();
		d.set(x, y + 2, z, st.stair(toChest, Half.TOP));
		d.set(x, y + 1, z, limb);
		if (forward) {
			d.set(x, y, z, st.full().defaultBlockState());
			d.set(x, y, z - 1, st.slabState(SlabType.TOP));
		} else {
			d.set(x, y, z, limb);
		}
	}

	/** The head: a finer block of the same stone where there is one (chiseled, cut, smooth). */
	private static BlockState headOf(Stone st) {
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.full()).getPath();
		String alt = switch (id) {
			case "smooth_quartz" -> "chiseled_quartz_block";
			case "polished_andesite" -> "smooth_stone";
			case "dark_prismarine" -> "prismarine_bricks";
			default -> id.contains("copper") ? id.replace("copper", "cut_copper") : id;
		};
		return States.block("minecraft:" + alt, st.full()).defaultBlockState();
	}

	/**
	 * An obelisk: a flared foot of stairs round the shaft, the shaft of the dressed stone with one recessed band (a wall
	 * of the same stone) two thirds up, the pyramidion a slab over the last course. No coloured inlay: from across the
	 * square a single accent block in a slender shaft reads as a gem.
	 */
	private static int obelisk(Draft d, int cx, int y, int cz) {
		Mat stone = Manner.masonry(d);
		int h = Math.min(8, d.maxHeight() - y - 2);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < h; k++) {
				d.set(cx, y + k, cz, k == h * 2 / 3 && stone.has(Form.WALL) ? stone.wall() : stone.full());
			}
			if (stone.has(Form.STAIRS)) {
				for (Direction out : Dirs.HORIZONTAL) {
					d.set(cx + Dirs.dx(out), y, cz + Dirs.dz(out), stone.stairs(out.getOpposite(), Half.BOTTOM));
				}
			}
			d.set(cx, y + h, cz, stone.has(Form.SLAB) ? stone.slab(SlabType.BOTTOM) : stone.full());
		}
		return y + h;
	}

	private static int stele(Draft d, int cx, int y, int cz) {
		Mat stone = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 4; k++) {
				d.set(cx, y + k, cz, stone.full());
			}
			d.set(cx - 1, y, cz, stone.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
			d.set(cx + 1, y, cz, stone.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			d.set(cx, y + 4, cz, stone.slab(SlabType.BOTTOM));
			// the carved face: a panel in relief on its front
			d.set(cx, y + 2, cz - 1, d.mat(Role.FURNITURE).trapdoor(Dirs.FRONT, Half.TOP, true));
		}
		return y + 4;
	}

	/**
	 * A rune stone: a standing slab two wide and four high, its shoulder rounded, a carved band of knotwork (the chiselled
	 * course) and the runes in relief on its face, moss at the crown and the foot.
	 */
	private static int runestone(Draft d, int cx, int y, int cz) {
		Mat stone = d.mat(Role.FOUNDATION);
		BlockState carved = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		BlockState rune = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Dirs.FRONT)
			.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, Half.TOP);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 4; k++) {
				d.set(cx, y + k, cz, k == 1 ? carved : k == 3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : stone.full().at(cx, y + k, cz));
				if (k < 3) {
					d.set(cx + 1, y + k, cz, k == 1 ? carved : stone.full().at(cx + 1, y + k, cz));
				}
			}
			// the shoulder rounded off, a cap on the taller side
			d.set(cx + 1, y + 3, cz, stone.has(Form.STAIRS) ? stone.stairs(Dirs.RIGHT, Half.BOTTOM).at(cx + 1, y + 3, cz)
				: stone.slab(SlabType.BOTTOM).at(cx + 1, y + 3, cz));
			if (d.allowed(cx, y + 4, cz)) {
				d.set(cx, y + 4, cz, stone.slab(SlabType.BOTTOM).at(cx, y + 4, cz));
			}
			// the runes in relief on the face
			d.set(cx, y + 2, cz - 1, rune);
			d.set(cx + 1, y + 2, cz - 1, rune);
			d.set(cx - 1, y, cz, Blocks.MOSS_CARPET.defaultBlockState());
			d.set(cx + 2, y, cz, Blocks.MOSS_CARPET.defaultBlockState());
		}
		return y + 4;
	}

	private static int sage(Draft d, int cx, int y, int cz, Stone st) {
		BlockState full = st.full().defaultBlockState();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// seated cross-legged: the knees spread on the lap, the hands folded, a tall body, a topknot
			d.set(cx - 1, y, cz, st.stair(Dirs.RIGHT, Half.BOTTOM));
			d.set(cx + 1, y, cz, st.stair(Dirs.LEFT, Half.BOTTOM));
			d.set(cx, y, cz, full);
			d.set(cx, y, cz - 1, st.slabState(SlabType.BOTTOM));
			d.set(cx, y + 1, cz, full);
			d.set(cx, y + 2, cz, full);
			d.set(cx - 1, y + 2, cz, st.stair(Dirs.RIGHT, Half.TOP));
			d.set(cx + 1, y + 2, cz, st.stair(Dirs.LEFT, Half.TOP));
			d.set(cx - 1, y + 1, cz, st.stair(Dirs.RIGHT, Half.BOTTOM));
			d.set(cx + 1, y + 1, cz, st.stair(Dirs.LEFT, Half.BOTTOM));
			d.set(cx, y + 3, cz, headOf(st));
			d.set(cx, y + 4, cz, st.slabState(SlabType.BOTTOM));
		}
		return y + 4;
	}

	private static int tiki(Draft d, int cx, int y, int cz) {
		Mat wood = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 4; k++) {
				d.set(cx, y + k, cz, wood.log(Direction.Axis.Y).orElse(wood.full()));
			}
			// faces: a brow and a mouth of trapdoors, a crest on top
			d.set(cx, y + 1, cz - 1, wood.trapdoor(Dirs.FRONT, Half.TOP, false));
			d.set(cx, y + 3, cz - 1, wood.trapdoor(Dirs.FRONT, Half.BOTTOM, false));
			d.set(cx, y + 4, cz, d.mat(Role.ROOF).slab(SlabType.BOTTOM));
			d.set(cx - 1, y + 2, cz, wood.fence());
			d.set(cx + 1, y + 2, cz, wood.fence());
		}
		return y + 4;
	}
}
