package dev.voidmc.hearth.gametest.townhome;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a street: a row of attached buildings with party walls between them, the way the planner lays out rows
 * (each floor meeting the street at its own door, so a row steps down a hill), optionally a second row facing it
 * across the street, or a stack of cliff dwellings each raised on the roof of the one below. Every blueprint is
 * checked. Run through the init script {@code docs/buildings/townhome_street.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/townhome_street.gradle streetPreview -Prow=townhouse,shophouse,townhouse -Pstyle=temperate -Pcount=6 -Pterrain=slope
 *   args: archetypes (cycled along the row)  styles  count  terrain  seed  outDir  [params k=v,k=v: both=true, corners=true, stack=3, frontage=7, depth=11, tier=3]
 * </pre>
 */
public final class StreetPreview {
	private static final int BG = 0xFFE9E4DA;

	private StreetPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String[] row = (args.length > 0 ? args[0] : "house/townhouse").split(",");
		String[] styles = (args.length > 1 ? args[1] : "temperate").split(",");
		int count = args.length > 2 ? Integer.parseInt(args[2]) : 5;
		String terrain = args.length > 3 ? args[3] : "flat";
		long seed = args.length > 4 ? Long.parseLong(args[4]) : 1;
		Path out = Path.of(args.length > 5 ? args[5] : "build/previews/street");
		CompoundTag extra = args.length > 6 ? parse(args[6]) : new CompoundTag();
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		if (styles.length == 1 && styles[0].equals("all")) {
			List<String> all = new ArrayList<>();
			for (Culture c : Culture.values()) {
				all.add(c.getSerializedName());
			}
			styles = all.toArray(String[]::new);
		}
		for (String style : styles) {
			Culture culture = Culture.valueOf(style.toUpperCase(Locale.ROOT));
			float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
			SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
			List<PlotSpec> specs = extra.contains("stack") ? stack(service, row[0], culture, extra.getIntOr("stack", 3), probe, seed, extra)
				: street(service, row, culture, count, probe, seed, extra);
			Voxels world = new Voxels();
			int[] box = bounds(specs);
			ground(world, probe, box[0] - 4, box[1] - 9, box[2] + 4, box[3] + 4, culture);
			StringBuilder title = new StringBuilder(String.join(",", row) + " in " + style + " on " + terrain + ":");
			// a stack is drawn and checked level by level against what the levels below built
			BuiltProbe built = new BuiltProbe(probe);
			for (PlotSpec spec : specs) {
				TerrainProbe here = extra.contains("stack") ? built : probe;
				BlueprintImpl bp = service.compiled(spec, here);
				built.add(bp);
				for (String p : BlueprintChecks.all(bp, here)) {
					System.out.println("CHECK " + spec.archetype().getPath() + " " + style + " seed " + spec.seed() + " " + spec.params() + ": " + p);
				}
				for (int s = 0; s < bp.stageCount(); s++) {
					for (int i = 0; i < bp.stepCount(s); i++) {
						bp.step(s, i, (x, y, z, state, tag) -> world.set(x, y, z, state));
					}
				}
				title.append(" ").append(spec.archetype().getPath().replace("house/", "")).append("(").append(bp.beds()).append("b)");
			}
			double a = extra.getIntOr("yaw", 32);
			boolean stack = extra.contains("stack");
			// the street seen almost square on (its rhythm of fronts and rooflines), then from both ends, then behind
			BufferedImage left = renderer.render(world, new View(stack ? 270 + 8 : 8, 14), 12, 2, 0);
			BufferedImage right = renderer.render(world, new View(stack ? 270 + a : a, 26), 11, 2, 0);
			BufferedImage back = renderer.render(world, new View(stack ? 270 - a : -a, 26), 11, 1, 0);
			BufferedImage behind = renderer.render(world, new View(stack ? 90 + a : 180 - a, 30), 8, 1, 0);
			String name = "street_" + String.join("-", row).replace("house/", "").replace('/', '_') + "_" + style + "_" + terrain + "_s" + seed
				+ (extra.contains("stack") ? "_stack" : "") + ".png";
			write(out.resolve(name), title.toString(), left, right, back, behind);
			System.out.println("wrote " + out.resolve(name).toAbsolutePath());
		}
	}

	/** A row along x facing south (the street on the +z side), floors meeting the street at each door; with both, a row opposite. */
	private static List<PlotSpec> street(BlueprintServiceImpl service, String[] row, Culture culture, int count, TerrainProbe probe, long seed,
		CompoundTag extra) {
		List<PlotSpec> out = new ArrayList<>();
		boolean corners = extra.getBooleanOr("corners", false);
		for (int side = 0; side < (extra.getBooleanOr("both", false) ? 2 : 1); side++) {
			Direction facing = side == 0 ? Direction.SOUTH : Direction.NORTH;
			int x = -count * 4;
			int streetZ = 0;
			for (int i = 0; i < count; i++) {
				ArchetypeInfo info = service.archetype(id(row[(i + side) % row.length]));
				long s = seed * 1000 + i * 37 + side * 501;
				java.util.Random r = new java.util.Random(s);
				int frontage = extra.contains("frontage") ? extra.getIntOr("frontage", 6)
					: info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * (0.15 + 0.5 * r.nextDouble()));
				int depth = extra.contains("depth") ? extra.getIntOr("depth", 10) : info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
				int oz = side == 0 ? streetZ - depth : streetZ + 5;
				int fz = side == 0 ? streetZ : streetZ + 4;
				int floor = probe.groundY(x + frontage / 2, fz);
				CompoundTag params = extra.copy();
				for (String k : new String[]{"both", "corners", "frontage", "depth", "yaw", "tier", "stack"}) {
					params.remove(k);
				}
				// party walls: the building's own left/right looking out of its door (facing south, its left is +x)
				boolean lowX = i > 0;
				boolean highX = i < count - 1;
				boolean ownLeftIsHighX = facing == Direction.SOUTH;
				params.putBoolean("party_left", ownLeftIsHighX ? highX : lowX);
				params.putBoolean("party_right", ownLeftIsHighX ? lowX : highX);
				if (corners) {
					if (!lowX) {
						params.putBoolean(ownLeftIsHighX ? "corner_right" : "corner_left", true);
					}
					if (!highX) {
						params.putBoolean(ownLeftIsHighX ? "corner_left" : "corner_right", true);
					}
				}
				params.putInt("row", count);
				params.putString("zone", "core");
				params.putInt("street_y", floor);
				if (!params.contains("storeys")) {
					params.putInt("storeys", info.flags().contains(ArchetypeInfo.SiteFlag.VERTICAL) ? (info.id().getPath().contains("tenement") ? 5 : 4) : 2);
				}
				int tier = extra.getIntOr("tier", 3);
				out.add(new PlotSpec(info.id(), StyleRegistry.styleId(culture), s, new BlockPos(x, floor, oz), frontage, depth, facing, info.maxHeight(), tier,
					params));
				x += frontage;
			}
		}
		return out;
	}

	/** A stack of STACKABLE dwellings the way the planner raises them: each on the roof of the one below, set back half its depth. */
	private static List<PlotSpec> stack(BlueprintServiceImpl service, String archetype, Culture culture, int levels, TerrainProbe probe, long seed,
		CompoundTag extra) {
		List<PlotSpec> out = new ArrayList<>();
		ArchetypeInfo info = service.archetype(id(archetype));
		int frontage = extra.getIntOr("frontage", 8);
		int depth = extra.getIntOr("depth", 8);
		// facing west on rising ground: the slope climbs away from the street
		Direction facing = Direction.WEST;
		int x0 = 0;
		int z0 = 0;
		int floor = probe.groundY(x0 - 1, z0 + frontage / 2);
		int height = info.minHeight() + (info.maxHeight() - info.minHeight()) * 3 / 4;
		for (int level = 0; level < levels; level++) {
			CompoundTag params = new CompoundTag();
			params.putInt("street_y", floor);
			if (level > 0) {
				params.putBoolean("stacked", true);
				params.putInt("level", level);
				params.putInt("stacked_on", level - 1);
			}
			if (probe.groundY(x0 + depth - 1, z0) > floor + 2) {
				params.putBoolean("cut_into_slope", true);
			}
			int h = level == 0 ? height : Math.max(info.minHeight(), Math.min(info.maxHeight(), info.minHeight() + 1));
			out.add(new PlotSpec(info.id(), StyleRegistry.styleId(culture), seed * 77 + level, new BlockPos(x0, floor, z0), depth, frontage, facing, h,
				extra.getIntOr("tier", 2), params));
			int set = Math.max(2, depth / 2);
			x0 += set;
			floor += h;
		}
		return out;
	}

	private static net.minecraft.resources.Identifier id(String s) {
		return dev.voidmc.hearth.Hearth.id(s.contains("/") ? s : "house/" + s);
	}

	private static int[] bounds(List<PlotSpec> specs) {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (PlotSpec s : specs) {
			b[0] = Math.min(b[0], s.origin().getX());
			b[1] = Math.min(b[1], s.origin().getZ());
			b[2] = Math.max(b[2], s.origin().getX() + s.sizeX() - 1);
			b[3] = Math.max(b[3], s.origin().getZ() + s.sizeZ() - 1);
		}
		b[3] = Math.max(b[3], 5);
		return b;
	}

	private static void ground(Voxels v, TerrainProbe probe, int x0, int z0, int x1, int z1, Culture culture) {
		boolean snowy = culture == Culture.TUNDRA;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = probe.groundY(x, z);
				int bottom = Math.min(g, probe.groundY(x0, z0)) - 3;
				for (int y = Math.min(bottom, g - 3); y < g; y++) {
					BlockState s = y < g - 3 ? Blocks.STONE.defaultBlockState() : y < g - 1 ? Blocks.DIRT.defaultBlockState()
						: switch (culture) {
							case DESERT -> Blocks.SAND.defaultBlockState();
							case MESA -> Blocks.RED_SAND.defaultBlockState();
							default -> Blocks.GRASS_BLOCK.defaultBlockState();
						};
					v.set(x, y, z, s);
				}
				// the street: a paved band in front of the row
				if (z >= 0 && z <= 4) {
					v.set(x, g - 1, z, (Math.floorMod(x * 7 + z * 3, 5) == 0 ? Blocks.ANDESITE : Blocks.STONE_BRICKS).defaultBlockState());
				}
				int w = probe.waterY(x, z);
				if (w != TerrainProbe.NO_WATER) {
					for (int y = g; y < w; y++) {
						v.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				} else if (snowy) {
					v.set(x, g, z, Blocks.SNOW.defaultBlockState());
				}
			}
		}
	}

	private static CompoundTag parse(String s) {
		CompoundTag t = new CompoundTag();
		for (String kv : s.split(",")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String k = kv.substring(0, eq);
			String v = kv.substring(eq + 1);
			if (v.equals("true") || v.equals("false")) {
				t.putBoolean(k, Boolean.parseBoolean(v));
			} else {
				try {
					t.putInt(k, Integer.parseInt(v));
				} catch (NumberFormatException e) {
					t.putString(k, v);
				}
			}
		}
		return t;
	}

	private static void write(Path file, String title, BufferedImage a, BufferedImage b, BufferedImage c, BufferedImage e) throws java.io.IOException {
		int pad = 10;
		int w = Math.max(a.getWidth() + pad * 2, Math.max(b.getWidth() + c.getWidth() + pad * 3, e.getWidth() + pad * 2));
		int h = 30 + a.getHeight() + pad + Math.max(b.getHeight(), c.getHeight()) + pad + e.getHeight() + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, pad, 20);
		int top = 30;
		g.drawImage(a, pad, top, null);
		top += a.getHeight() + pad;
		g.drawImage(b, pad, top, null);
		g.drawImage(c, pad * 2 + b.getWidth(), top, null);
		top += Math.max(b.getHeight(), c.getHeight()) + pad;
		g.drawImage(e, pad, top, null);
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
