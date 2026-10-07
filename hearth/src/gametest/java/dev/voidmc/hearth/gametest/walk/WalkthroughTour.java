package dev.voidmc.hearth.gametest.walk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The playability walkthrough: buildings of every common kind, in Bohemia and three other cultures, built on raised
 * pads in a superflat world (part A), and a temperate town grown by the real sim on the Earth world (part B). For each
 * building: the doors a hand opens are opened, a player-sized model searches the building from the street ({@link
 * WalkModel}: rooms, stairs, head room, light, doors, POIs), the folk's own pathfinder is asked for a way to every bed,
 * job site and store, and the real client player is driven along the model's path to every room, bed, job site and
 * stair top with the game's own movement and collision ({@code LocalPlayer.move}: steps, stairs, jumps, ladders). A
 * path the model finds and the player cannot follow is a stuck point (photographed). Then first-person shots with the
 * HUD hidden: the main room, a bedroom, the stair. In part B residents are watched at night (in their beds?) and in
 * the morning (at work?).
 *
 * <p>{@code -Dhearth.tour=walk}; {@code -Dhearth.walk.out=DIR} (JPEGs and the results), {@code -Dhearth.walk.parts=a,b},
 * {@code -Dhearth.walk.only=cottage,inn}, {@code -Dhearth.walk.cultures=temperate,desert}, {@code -Dhearth.walk.days=N}
 * (part B's growth, default 120), {@code -Dhearth.walk.shots=false}.
 */
public class WalkthroughTour implements FabricClientGameTest {
	private static final Path OUT = Path.of(System.getProperty("hearth.walk.out", "build/walk"));
	private static final String PARTS = System.getProperty("hearth.walk.parts", "a,b");
	private static final String ONLY = System.getProperty("hearth.walk.only", "");
	private static final int DAYS = Integer.getInteger("hearth.walk.days", 120);
	private static final boolean SHOTS = Boolean.parseBoolean(System.getProperty("hearth.walk.shots", "true"));
	private static final int PAD = 10;

	private static final List<String> TYPES = List.of("house/cottage", "house/farmhouse", "house/townhouse", "house/tenement", "house/merchant_house",
		"commerce/inn", "commerce/tavern", "work/smithy", "work/bakery", "work/windmill", "civic/chapel", "civic/town_hall", "civic/library",
		"commerce/market_hall", "house/manor", "noble/keep", "extract/mine");
	private static final List<Culture> CULTURES = List.of(Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.NORTHERN, Culture.DESERT);

	private final List<Audit> audits = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("walk")) {
			return;
		}
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve("walk_results.md"), "# Walkthrough results (raw, written by WalkthroughTour)\n\n", StandardCharsets.UTF_8);
			Files.writeString(OUT.resolve("walk_summary.tsv"), String.join("\t", "culture", "archetype", "verdict", "rooms", "entrances", "pois", "folk",
				"walks", "stuck", "badSteps/steps", "jumps", "tight", "dark0/dim/cells", "badDoors/doors") + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		context.getInput().resizeWindow(1280, 720);
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(8);
			mc.options.fov().set(80);
		});
		if (PARTS.contains("a")) {
			partA(context);
		}
		if (PARTS.contains("b")) {
			partB(context);
		}
		log("done: " + this.audits.size() + " buildings walked");
	}

	// ---------------------------------------------------------------- part A: built on pads

	private void partA(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261007");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
				s.setGenerateStructures(false);
				for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList()) {
					if (e.preset() != null && e.preset().is(WorldPresets.FLAT)) {
						s.setWorldType(e);
					}
				}
			})
			.create()) {
			for (String c : List.of("gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_monsters false", "gamerule spawn_mobs false",
				"gamerule fall_damage false", "weather clear", "time set 6000", "gamemode creative @a")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			int base = world.getServer().computeOnServer(server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0));
			int ground = base + PAD;
			log("superflat ground " + base + ", pads to " + ground);
			calibrate(context, world, ground);
			Set<String> only = ONLY.isBlank() ? Set.of() : Set.of(ONLY.split(","));
			String cultures = System.getProperty("hearth.walk.cultures", "");
			for (int ci = 0; ci < CULTURES.size(); ci++) {
				Culture culture = CULTURES.get(ci);
				if (!cultures.isBlank() && !List.of(cultures.split(",")).contains(culture.getSerializedName())) {
					continue;
				}
				for (int ti = 0; ti < TYPES.size(); ti++) {
					String type = TYPES.get(ti);
					String name = type.substring(type.indexOf('/') + 1);
					if (!only.isEmpty() && !only.contains(name)) {
						continue;
					}
					int x0 = ti * 112;
					int z0 = 200 + ci * 160;
					teleport(world, x0 + 10, ground + 30, z0 + 70, 180, 30, true);
					settle(context, 2_500);
					long seed = 11 + ti * 7L + ci * 101L;
					Blueprint bp = world.getServer().computeOnServer(server -> build(server.overworld(), type, culture, seed, x0, z0, base, ground));
					if (bp == null) {
						continue;
					}
					settle(context, 5_000);
					walk(context, world, new Audit(culture.getSerializedName() + " " + name, culture.getSerializedName(), type, bp), true);
				}
			}
			settle(context, 3_000);
		}
	}

	/** A test rig of known pieces (a stair flight, a ladder, a full-block step, a low beam) to check the walker. */
	private void calibrate(ClientGameTestContext context, TestSingleplayerContext world, int ground) {
		int x0 = -300;
		int z0 = -300;
		teleport(world, x0, ground + 10, z0 + 10, 180, 30, true);
		settle(context, 2_000);
		List<double[]> results = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int x = x0 - 6; x <= x0 + 16; x++) {
				for (int z = z0 - 6; z <= z0 + 16; z++) {
					for (int y = ground - PAD; y < ground; y++) {
						level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2 | 16);
					}
				}
			}
			// a flight of four oak stairs going north onto a platform 4 up, a ladder up its side, a block step east
			for (int k = 0; k < 4; k++) {
				BlockPos s = new BlockPos(x0, ground + k, z0 + 8 - k);
				level.setBlock(s, Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH), 2 | 16);
				for (int y = ground; y < ground + k; y++) {
					level.setBlock(new BlockPos(x0, y, z0 + 8 - k), Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
				}
			}
			for (int x = x0 - 2; x <= x0 + 2; x++) {
				for (int z = z0; z <= z0 + 4; z++) {
					for (int y = ground; y < ground + 4; y++) {
						level.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
					}
				}
			}
			for (int y = ground; y < ground + 4; y++) {
				level.setBlock(new BlockPos(x0 + 3, y, z0 + 2), Blocks.LADDER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 2 | 16);
			}
			level.setBlock(new BlockPos(x0 + 8, ground, z0 + 8), Blocks.STONE.defaultBlockState(), 2 | 16);
			// a dirt path before a bottom slab (a 0.5625 rise), and a carpet after a slab
			level.setBlock(new BlockPos(x0 + 10, ground - 1, z0 + 10), Blocks.DIRT_PATH.defaultBlockState(), 2 | 16);
			level.setBlock(new BlockPos(x0 + 10, ground, z0 + 9), Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2 | 16);
			level.setBlock(new BlockPos(x0 + 12, ground, z0 + 10), Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2 | 16);
			level.setBlock(new BlockPos(x0 + 12, ground, z0 + 9), Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
			level.setBlock(new BlockPos(x0 + 12, ground + 1, z0 + 9), Blocks.MOSS_CARPET.defaultBlockState(), 2 | 16);
			return null;
		});
		settle(context, 2_000);
		// fake blueprint-free model: search from the street and walk to the platform, the ladder top, the step
		Map<String, List<double[]>> paths = world.getServer().computeOnServer(server -> {
			Blueprint fake = new RigBlueprint(new BoundingBox(x0 - 4, ground - 1, z0 - 4, x0 + 12, ground + 8, z0 + 12));
			WalkModel w = new WalkModel(server.overworld(), fake);
			WalkModel.Cell street = w.standAt(x0, ground, z0 + 11);
			Map<String, List<double[]>> out = new LinkedHashMap<>();
			if (street == null) {
				return out;
			}
			w.search(street);
			for (var t : List.of(new int[]{x0, ground + 4, z0 + 2}, new int[]{x0 + 2, ground + 4, z0 + 4}, new int[]{x0 + 8, ground + 1, z0 + 8},
				new int[]{x0 + 10, ground, z0 + 9}, new int[]{x0 + 12, ground + 1, z0 + 9})) {
				WalkModel.Cell c = w.cell(t[0], t[1], t[2]);
				String key = t[0] + "," + t[1] + "," + t[2];
				if (c == null || !w.reached(c)) {
					out.put(key + " (model: not reached" + (c == null ? ", no cell" : "") + ")", List.of());
				} else {
					List<double[]> p = new ArrayList<>();
					for (WalkModel.Step s : w.path(c)) {
						p.add(new double[]{s.cell().x(), s.cell().y(), s.cell().z(), s.cell().floor(), s.edge(), s.cell().climb() ? 1 : 0});
					}
					out.put(key + " via " + edges(p), p);
				}
			}
			return out;
		});
		Map<String, String> res = context.computeOnClient(mc -> walkAll(mc.player, paths));
		for (var e : res.entrySet()) {
			log("calibration: " + e.getKey() + " -> " + e.getValue());
		}
		// the slab-to-carpet step in detail: one move, and in tenths
		String detail = context.computeOnClient(mc -> {
			LocalPlayer p = mc.player;
			Vec3 saved = p.position();
			StringBuilder b = new StringBuilder("maxUpStep " + p.maxUpStep() + " flying " + p.getAbilities().flying + " pose " + p.getPose());
			for (int mode = 0; mode < 3; mode++) {
				p.setPos(x0 + 12.5, ground + 0.5, z0 + 10.5);
				p.setDeltaMovement(Vec3.ZERO);
				p.setOnGround(true);
				if (mode == 0) {
					p.move(MoverType.SELF, new Vec3(0, -0.08, -1.0));
				} else {
					for (int k = 0; k < 10; k++) {
						p.setOnGround(true);
						p.move(MoverType.SELF, new Vec3(0, mode == 1 ? -0.08 : 0, -0.1));
					}
				}
				b.append(String.format(Locale.ROOT, "; mode %d -> %.3f,%.3f,%.3f onGround %s", mode, p.getX(), p.getY(), p.getZ(), p.onGround()));
			}
			p.setPos(saved);
			return b.toString();
		});
		log("calibration detail: " + detail);
	}

	private static String edges(List<double[]> p) {
		Map<String, Integer> n = new LinkedHashMap<>();
		for (double[] s : p) {
			n.merge(WalkModel.EDGE[(int) s[4]], 1, Integer::sum);
		}
		return n.toString();
	}

	/** Builds an archetype on a raised pad (a hill behind it for the mine), facing south, and returns its blueprint. */
	private static @Nullable Blueprint build(ServerLevel level, String type, Culture culture, long seed, int x0, int z0, int base, int ground) {
		BlueprintServiceImpl service = BuildModule.service();
		ArchetypeInfo info = service.archetype(Hearth.id(type));
		if (info == null) {
			log("unknown archetype " + type);
			return null;
		}
		if (!info.suits(culture)) {
			log(type + ": not built by the " + culture.getSerializedName() + " culture; skipped");
			return null;
		}
		java.util.Random r = new java.util.Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		boolean hill = type.equals("extract/mine");
		Pad probe = new Pad(level.getSeed(), ground, level.getMinY(), level.getMaxY(), hill ? z0 + depth - 1 : Integer.MIN_VALUE, culture);
		// the pad: stone, three of earth and the turf, ten blocks round the plot, the street side wider
		for (int x = x0 - PAD; x < x0 + frontage + PAD; x++) {
			for (int z = z0 - (hill ? 30 : PAD); z < z0 + depth + PAD + 4; z++) {
				int top = probe.groundY(x, z);
				for (int y = base; y < top; y++) {
					BlockState st = y == top - 1 ? (culture == Culture.DESERT ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState()
						: y >= top - 4 ? (culture == Culture.DESERT ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState() : Blocks.STONE.defaultBlockState();
					level.setBlock(new BlockPos(x, y, z), st, 2 | 16);
				}
			}
		}
		int tier = type.equals("house/manor") || type.equals("noble/keep") || type.equals("civic/town_hall") || type.equals("commerce/market_hall") ? 3 : 2;
		PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(culture), seed, new BlockPos(x0, ground, z0), frontage, depth, Direction.SOUTH,
			Math.max(info.maxHeight(), 8), tier, new CompoundTag());
		BlueprintImpl bp;
		try {
			bp = Compiler.compile(service.draw(spec, probe));
		} catch (RuntimeException e) {
			log(type + " " + culture.getSerializedName() + ": blueprint failed: " + e);
			return null;
		}
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					BlockPos p = new BlockPos(x, y, z);
					level.setBlock(p, state, 2 | 16);
					if (tag != null) {
						BlockEntity be = level.getBlockEntity(p);
						if (be != null) {
							be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
							be.setChanged();
						}
					}
				});
			}
		}
		log(String.format(Locale.ROOT, "built %s %s seed %d %dx%d at %d,%d,%d (%d beds, %d POIs)", type, culture.getSerializedName(), seed, frontage, depth, x0,
			ground, z0, bp.beds(), bp.pois().size()));
		return bp;
	}

	// ---------------------------------------------------------------- one building

	private void walk(ClientGameTestContext context, TestSingleplayerContext world, Audit a, boolean allShots) {
		world.getServer().runOnServer(server -> a.openDoors(server.overworld()));
		settle(context, 1_500);
		try {
			world.getServer().runOnServer(server -> a.run(server.overworld()));
		} catch (RuntimeException e) {
			a.defect("HIGH", "audit failed: " + e);
			log("audit failed for " + a.label + ": " + e);
		}
		// the real player along every path (in the same client frame: the position is put back after)
		if (a.street != null) {
			teleport(world, a.street.x() + 0.5, a.street.floor(), a.street.z() + 0.5, 0, 0, false);
			settle(context, 1_500);
			Map<String, String> res = context.computeOnClient(mc -> walkAll(mc.player, a.walks));
			a.walkResults.putAll(res);
			for (var e : res.entrySet()) {
				if (!e.getValue().equals("ok")) {
					a.defect(e.getKey().startsWith("room") || e.getKey().startsWith("stair") ? "MED" : "HIGH",
						"player gets stuck on the way to the " + e.getKey() + ": " + e.getValue());
					double[] at = stuckPoint(e.getValue());
					if (at != null) {
						a.stuckAt.add(at);
					}
				}
			}
		}
		world.getServer().runOnServer(server -> a.closeDoors(server.overworld()));
		settle(context, 800);
		if (SHOTS) {
			String slug = a.label.replace(' ', '_');
			if (a.mainCam != null) {
				shoot(context, world, a, slug + "_main", a.mainCam);
			}
			if (allShots && a.bedCam != null) {
				shoot(context, world, a, slug + "_bed", a.bedCam);
			}
			if (allShots && a.stairCam != null) {
				shoot(context, world, a, slug + "_stair", a.stairCam);
			}
			for (int i = 0; i < Math.min(2, a.stuckAt.size()); i++) {
				double[] s = a.stuckAt.get(i);
				shoot(context, world, a, slug + "_stuck" + i, s);
			}
		}
		this.audits.add(a);
		log(a.row().replace('\t', ' '));
		try {
			Files.writeString(OUT.resolve("walk_results.md"), a.markdown(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			Files.writeString(OUT.resolve("walk_summary.tsv"), a.row() + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			log("could not write results: " + e);
		}
	}

	/** "stuck ... last good x,y,z toward x,y,z" -> a camera at the last good cell looking at the next. */
	private static double @Nullable [] stuckPoint(String r) {
		int i = r.indexOf("[cam ");
		if (i < 0) {
			return null;
		}
		String[] v = r.substring(i + 5, r.indexOf(']', i)).split(" ");
		double[] out = new double[6];
		for (int k = 0; k < 6; k++) {
			out[k] = Double.parseDouble(v[k]);
		}
		return out;
	}

	/**
	 * Drives the client player along each path with the game's own movement ({@code Entity.move}: collision, the 0.6
	 * step, stairs), jumping where the model jumps and climbing where it climbs, then puts the player back.
	 */
	private static Map<String, String> walkAll(LocalPlayer p, Map<String, List<double[]>> paths) {
		Map<String, String> out = new LinkedHashMap<>();
		Vec3 saved = p.position();
		boolean flying = p.getAbilities().flying;
		p.getAbilities().flying = false;
		try {
			for (var e : paths.entrySet()) {
				out.put(e.getKey(), e.getValue().isEmpty() ? "no path" : walkOne(p, e.getValue()));
			}
		} finally {
			p.setPos(saved);
			p.setDeltaMovement(Vec3.ZERO);
			p.getAbilities().flying = flying;
		}
		return out;
	}

	private static String walkOne(LocalPlayer p, List<double[]> path) {
		double[] s0 = path.getFirst();
		p.setPos(s0[0] + 0.5, s0[3], s0[2] + 0.5);
		p.setDeltaMovement(Vec3.ZERO);
		p.setOnGround(true);
		for (int i = 1; i < path.size(); i++) {
			double[] a = path.get(i - 1);
			double[] b = path.get(i);
			int edge = (int) b[4];
			boolean climbTo = b[5] > 0;
			double tx = b[0] + 0.5;
			double tz = b[2] + 0.5;
			Vec3 from = p.position();
			String why = null;
			// in tenths of a block (a walking pace), and if that wedges on an edge, in one stride
			for (int stride = 0; stride < 2; stride++) {
				p.setPos(from);
				p.setDeltaMovement(Vec3.ZERO);
				p.setOnGround(true);
				why = moveOnce(p, b, edge, climbTo, tx, tz, stride == 0 ? 10 : 1);
				if (why == null) {
					double dh = Math.hypot(p.getX() - tx, p.getZ() - tz);
					double dv = Math.abs(p.getY() - b[3]);
					why = dh > 0.3 || dv > 0.35 ? WalkModel.EDGE[edge] + " move did not arrive" : null;
				}
				if (why == null) {
					break;
				}
			}
			if (why != null) {
				return fail(p, a, b, why);
			}
		}
		return "ok";
	}

	/** One move along an edge; null when nothing stopped it outright (arrival is checked by the caller). */
	private static @Nullable String moveOnce(LocalPlayer p, double[] b, int edge, boolean climbTo, double tx, double tz, int parts) {
		if (edge == WalkModel.UP || edge == WalkModel.DOWN) {
			p.move(MoverType.SELF, new Vec3(0, b[3] - p.getY(), 0));
			return null;
		}
		double rise = b[3] - p.getY();
		if (edge == WalkModel.JUMP || edge == WalkModel.EXIT) {
			double y0 = p.getY();
			p.move(MoverType.SELF, new Vec3(0, rise + 0.02, 0));
			if (p.getY() - y0 < rise - 0.01) {
				return "head room: the " + WalkModel.EDGE[edge] + " is stopped " + fmt(rise - (p.getY() - y0)) + " short";
			}
		}
		boolean level = edge == WalkModel.JUMP || edge == WalkModel.EXIT || climbTo;
		for (int k = 0; k < parts; k++) {
			double fx = (tx - p.getX()) / (parts - k);
			double fz = (tz - p.getZ()) / (parts - k);
			p.setOnGround(true);
			p.move(MoverType.SELF, new Vec3(fx, level ? 0 : -0.08, fz));
		}
		if (climbTo) {
			p.move(MoverType.SELF, new Vec3(0, b[3] - p.getY(), 0));
		} else {
			p.move(MoverType.SELF, new Vec3(0, -(Math.max(0, p.getY() - b[3]) + 0.2), 0));
		}
		return null;
	}

	private static String fail(LocalPlayer p, double[] a, double[] b, String why) {
		StringBuilder blocks = new StringBuilder();
		for (int dy = -1; dy <= 2; dy++) {
			BlockPos q = new BlockPos((int) b[0], (int) b[1] + dy, (int) b[2]);
			blocks.append(dy == -1 ? "" : " / ").append(dy).append(':')
				.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(q).getBlock()).getPath());
		}
		why = why + "; target column " + blocks;
		return String.format(Locale.ROOT, "stuck at step %s -> %s (%s; %s): player ended at %.2f,%.2f,%.2f [cam %.2f %.2f %.2f %.2f %.2f %.2f]",
			(int) a[0] + "," + (int) a[1] + "," + (int) a[2], (int) b[0] + "," + (int) b[1] + "," + (int) b[2], WalkModel.EDGE[(int) b[4]], why, p.getX(),
			p.getY(), p.getZ(), a[0] + 0.5, a[3], a[2] + 0.5, b[0] + 0.5, b[3] + 1.0, b[2] + 0.5);
	}

	private static String fmt(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}

	// ---------------------------------------------------------------- part B: a town grown by the sim

	private record Town(long id, String name) {
	}

	private void partB(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "weather clear", "time set 2000",
				"gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			Town town = world.getServer().computeOnServer(WalkthroughTour::foundTown);
			if (town == null) {
				log("part B: no site for a temperate town");
				return;
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town.id).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
			settle(context, 20_000);
			log("part B town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town.id);
				return s.name() + " " + s.population() + " souls, plots " + s.plotCounts() + ", embodied " + s.embodied();
			}));

			// the buildings, one of each kind first
			List<Integer> plots = world.getServer().computeOnServer(server -> pickPlots(settlement(server, town.id)));
			Map<Integer, Audit> byPlot = new LinkedHashMap<>();
			for (int id : plots) {
				Audit a = world.getServer().computeOnServer(server -> {
					SimSettlement s = settlement(server, town.id);
					SimPlot p = s.plot(id);
					SimWorld w = SimServer.world(server.overworld());
					Blueprint bp = HearthServices.blueprints().blueprint(p.spec(), w.terrain());
					String name = p.spec().archetype().getPath();
					return new Audit("grown " + name.substring(name.indexOf('/') + 1) + " #" + id, "temperate (grown " + s.name() + ")", name, bp);
				});
				BlockPos at = a.bp.spec().origin();
				teleport(world, at.getX() + 0.5, a.bp.spec().floorY() + 12, at.getZ() + 0.5, 0, 50, true);
				settle(context, 2_500);
				walk(context, world, a, byPlot.size() < 6);
				byPlot.put(id, a);
			}

			// the residents: at dusk, at midnight, in the morning
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
			world.getServer().runCommand("gamerule advance_time true");
			timeTo(world, 12_500);
			settle(context, 60_000);
			String dusk = residents(world, town, byPlot, "dusk", false);
			log("dusk: " + dusk);
			timeTo(world, 18_000);
			settle(context, 25_000);
			String night = residents(world, town, byPlot, "midnight", false);
			log("midnight: " + night);
			sleeperShot(context, world, town);
			timeTo(world, 3_500);
			settle(context, 60_000);
			String morning = residents(world, town, byPlot, "morning", true);
			log("morning: " + morning);
			try {
				Files.writeString(OUT.resolve("walk_results.md"), "## Part B residents\n\n" + dusk + "\n" + night + "\n" + morning + "\n",
					StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			} catch (IOException e) {
				log("could not write: " + e);
			}
			settle(context, 3_000);
		}
	}

	private static void timeTo(TestSingleplayerContext world, long t) {
		long now = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime() % 24_000L);
		long add = ((t - now) % 24_000L + 24_000L) % 24_000L;
		world.getServer().runCommand("time add " + add);
	}

	private static @Nullable Town foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		for (int r = 128; r <= 1600 && (best == null || bestRelief > 3 || r < 512); r += 64) {
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8 + r * 0.01;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				int g = w.terrain().groundY(at.getX(), at.getZ());
				if (g <= w.terrain().seaLevel() + 2) {
					continue;
				}
				int lo = g;
				int hi = g;
				for (int dx = -40; dx <= 40; dx += 10) {
					for (int dz = -40; dz <= 40; dz += 10) {
						int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo >= bestRelief) {
					continue;
				}
				Culture c = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, HearthServices::culture);
				if (c != Culture.TEMPERATE || w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				bestRelief = hi - lo;
				best = at;
			}
		}
		if (best == null) {
			return null;
		}
		SimSettlement s = w.found(best, Culture.TEMPERATE, null);
		for (int d = 0; d < DAYS; d += 4) {
			patron(s);
			w.simulate(s, 4);
		}
		log(String.format(Locale.ROOT, "part B: founded %s at %s (relief %d), grew %d days: %d souls, %s, plots %s", s.name(), s.center().toShortString(),
			bestRelief, DAYS, s.population(), s.tier().getSerializedName(), s.plotCounts()));
		return new Town(s.id(), s.name());
	}

	private static void patron(SimSettlement s) {
		for (Goods g : Goods.VALUES) {
			int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
			if (want > 0) {
				s.stockpile().add(g, want);
			}
		}
		if (s.treasury() < 3000) {
			s.deposit(3000 - s.treasury());
		}
	}

	/** Built plots worth walking: one of each kind first (homes, workshops, trade, civic, noble, storage), up to 16. */
	private static List<Integer> pickPlots(SimSettlement s) {
		List<Integer> out = new ArrayList<>();
		Set<String> kinds = new HashSet<>();
		for (int pass = 0; pass < 2; pass++) {
			for (SimPlot p : s.plots()) {
				String path = p.spec().archetype().getPath();
				if (p.state() != PlotState.BUILT || out.contains(p.id())) {
					continue;
				}
				if (!(path.startsWith("house/") || path.startsWith("work/") || path.startsWith("commerce/") || path.startsWith("civic/") || path.startsWith("noble/")
					|| path.startsWith("storage/") || path.startsWith("law/") || path.startsWith("extract/mine"))) {
					continue;
				}
				if (pass == 0 && !kinds.add(path)) {
					continue;
				}
				if (pass == 1 && !path.startsWith("house/")) {
					continue;
				}
				if (out.size() < 16) {
					out.add(p.id());
				}
			}
		}
		return out;
	}

	/** Where each resident is and what they are doing, against their home's beds or their work plot. */
	private static String residents(TestSingleplayerContext world, Town town, Map<Integer, Audit> walked, String when, boolean work) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, town.id);
			SimWorld w = SimServer.world(server.overworld());
			int embodied = 0;
			int inOwnBed = 0;
			int inOtherBed = 0;
			int awakeHome = 0;
			int elsewhere = 0;
			int atWork = 0;
			int workers = 0;
			int homed = 0;
			StringBuilder detail = new StringBuilder();
			Map<Integer, int[]> perPlot = new HashMap<>();
			for (Resident r : s.residents()) {
				Folk f = NpcModule.embodiment().entityOf(r.id);
				if (f == null) {
					continue;
				}
				embodied++;
				if (work) {
					if (r.work == Resident.NO_PLOT || s.plot(r.work) == null) {
						continue;
					}
					workers++;
					SimPlot p = s.plot(r.work);
					BoundingBox reach = p.reach().inflatedBy(2);
					boolean there = reach.isInside(f.blockPosition());
					int[] n = perPlot.computeIfAbsent(r.work, k -> new int[2]);
					n[1]++;
					if (there) {
						atWork++;
						n[0]++;
					} else if (detail.length() < 2500) {
						detail.append(String.format(Locale.ROOT, "    - %s (%s) not at work plot #%d %s: at %s, %s%n", f.folkName(), r.job.getSerializedName(), r.work,
							p.spec().archetype().getPath(), f.blockPosition().toShortString(), f.brain() == null ? "no brain" : f.brain().describe()));
					}
					continue;
				}
				if (r.home == Resident.NO_PLOT || s.plot(r.home) == null) {
					continue;
				}
				homed++;
				SimPlot home = s.plot(r.home);
				Blueprint bp = HearthServices.blueprints().blueprint(home.spec(), w.terrain());
				Set<BlockPos> beds = new HashSet<>();
				for (Poi p : bp.pois()) {
					if (p.type() == PoiType.BED) {
						beds.add(p.pos());
					}
				}
				int[] n = perPlot.computeIfAbsent(r.home, k -> new int[2]);
				n[1]++;
				BlockPos sleep = f.getSleepingPos().orElse(null);
				if (f.isSleeping() && sleep != null && (beds.contains(sleep) || beds.stream().anyMatch(b -> b.distManhattan(sleep) <= 1))) {
					inOwnBed++;
					n[0]++;
				} else if (f.isSleeping()) {
					inOtherBed++;
				} else if (home.reach().inflatedBy(1).isInside(f.blockPosition())) {
					awakeHome++;
				} else {
					elsewhere++;
					if (detail.length() < 2500) {
						detail.append(String.format(Locale.ROOT, "    - %s (%s) home #%d %s: at %s, %s%n", f.folkName(), r.job.getSerializedName(), r.home,
							home.spec().archetype().getPath(), f.blockPosition().toShortString(), f.brain() == null ? "no brain" : f.brain().describe()));
					}
				}
			}
			StringBuilder b = new StringBuilder();
			if (work) {
				b.append(String.format(Locale.ROOT, "- **%s** (%d): %d embodied, %d with a work plot, %d at it%n", when, server.overworld().getOverworldClockTime() % 24000,
					embodied, workers, atWork));
			} else {
				b.append(String.format(Locale.ROOT, "- **%s** (%d): %d embodied, %d with a home: %d asleep in a bed of their home, %d asleep elsewhere, %d awake at home, "
					+ "%d away from home%n", when, server.overworld().getOverworldClockTime() % 24000, embodied, homed, inOwnBed, inOtherBed, awakeHome, elsewhere));
			}
			for (var e : walked.entrySet()) {
				int[] n = perPlot.get(e.getKey());
				if (n != null) {
					String line = String.format(Locale.ROOT, "%s: %s %d/%d", when, work ? "at work" : "in own bed", n[0], n[1]);
					e.getValue().notes.add("residents " + line);
					b.append("    - ").append(e.getValue().label).append(": ").append(line).append("\n");
				}
			}
			b.append(detail);
			return b.toString();
		});
	}

	private void sleeperShot(ClientGameTestContext context, TestSingleplayerContext world, Town town) {
		double[] cam = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() == null || f.brain().settlement.id() != town.id || !f.isSleeping()) {
					continue;
				}
				BlockPos bed = f.getSleepingPos().orElse(f.blockPosition());
				for (int r = 2; r <= 3; r++) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos q = bed.relative(d, r);
						for (int dy = -1; dy <= 1; dy++) {
							BlockPos c = q.above(dy);
							if (level.getBlockState(c).getCollisionShape(level, c).isEmpty() && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()
								&& !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty() && !level.canSeeSky(c)) {
								return new double[]{c.getX() + 0.5, c.getY(), c.getZ() + 0.5, f.getX(), f.getY() + 0.3, f.getZ()};
							}
						}
					}
				}
			}
			return null;
		});
		if (cam != null) {
			Audit none = null;
			shoot(context, world, none, "grown_sleeper_night", cam);
		}
	}

	private static SimSettlement settlement(MinecraftServer server, long id) {
		return SimServer.world(server.overworld()).settlement(id);
	}

	private static void forceChunks(MinecraftServer server, Town t, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = settlement(server, t.id).bounds();
		for (int cx = (b.minX() >> 4) - 1; cx <= (b.maxX() >> 4) + 1; cx++) {
			for (int cz = (b.minZ() >> 4) - 1; cz <= (b.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 240; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
		log("applier: " + world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().blocksPlaced.sum()) + " blocks written");
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	// ---------------------------------------------------------------- camera

	private void shoot(ClientGameTestContext context, TestSingleplayerContext world, @Nullable Audit a, String name, double[] v) {
		double dx = v[3] - v[0];
		double dy = v[4] - (v[1] + 1.62);
		double dz = v[5] - v[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, v[0], v[1], v[2], yaw, Mth.clamp(pitch, -60, 60), true);
		settle(context, 2_500);
		Path png = context.takeScreenshot("hearth_walk_" + name);
		Path jpg = OUT.resolve(name + ".jpg");
		try {
			BufferedImage img = ImageIO.read(png.toFile());
			BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
			rgb.getGraphics().drawImage(img, 0, 0, null);
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.8f);
			Files.deleteIfExists(jpg);
			try (ImageOutputStream os = ImageIO.createImageOutputStream(jpg.toFile())) {
				writer.setOutput(os);
				writer.write(null, new IIOImage(rgb, null, null), param);
			}
			writer.dispose();
			Files.deleteIfExists(png);
			if (a != null) {
				a.shots.add(jpg.getFileName().toString());
			}
		} catch (IOException e) {
			log("screenshot " + name + " failed: " + e);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch, boolean fly) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = fly;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth walk] " + line);
	}

	// ---------------------------------------------------------------- terrain and the rig

	/** The pad: level at {@code ground}, or for the mine a hill rising one in one behind the front row up to 16. */
	private record Pad(long seed, int ground, int minY, int maxY, int frontZ, Culture culture) implements TerrainProbe {
		@Override
		public int seaLevel() {
			return this.minY;
		}

		@Override
		public int groundY(int x, int z) {
			if (this.frontZ == Integer.MIN_VALUE) {
				return this.ground;
			}
			int d = this.frontZ - z;
			return this.ground + Mth.clamp(d - 4, 0, 16);
		}

		@Override
		public int waterY(int x, int z) {
			return NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return switch (this.culture) {
				case DESERT -> Biomes.DESERT;
				case NORTHERN -> Biomes.TAIGA;
				case MEDITERRANEAN -> Biomes.SAVANNA;
				default -> Biomes.PLAINS;
			};
		}

		@Override
		public float temperature(int x, int z) {
			return switch (this.culture) {
				case DESERT -> 2.0F;
				case NORTHERN -> 0.25F;
				case MEDITERRANEAN -> 1.2F;
				default -> 0.8F;
			};
		}
	}

	/** A stand-in blueprint for the calibration rig: only its bounds matter. */
	private record RigBlueprint(BoundingBox bounds) implements Blueprint {
		@Override
		public PlotSpec spec() {
			return new PlotSpec(Hearth.id("house/cottage"), StyleRegistry.styleId(Culture.TEMPERATE), 0, new BlockPos(this.bounds.minX(), this.bounds.minY() + 1,
				this.bounds.minZ()), 1, 1, Direction.SOUTH, 8, 2, new CompoundTag());
		}

		@Override
		public int stageCount() {
			return 0;
		}

		@Override
		public dev.voidmc.hearth.api.StageKind stageKind(int stage) {
			throw new IndexOutOfBoundsException();
		}

		@Override
		public int stepCount(int stage) {
			return 0;
		}

		@Override
		public void step(int stage, int index, dev.voidmc.hearth.api.StepSink sink) {
		}

		@Override
		public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, dev.voidmc.hearth.api.StepSink sink) {
		}

		@Override
		public int work(int stage) {
			return 0;
		}

		@Override
		public int[] goods(int stage) {
			return new int[0];
		}

		@Override
		public List<Poi> pois() {
			return List.of();
		}

		@Override
		public List<BlockPos> entrances() {
			return List.of();
		}

		@Override
		public int beds() {
			return 0;
		}

		@Override
		public dev.voidmc.hearth.api.Silhouette silhouette() {
			return null;
		}
	}
}
