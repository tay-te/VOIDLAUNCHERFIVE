package dev.voidmc.hearth.gametest.extraction;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.BlueprintPreview;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.NightLight;
import dev.voidmc.hearth.gametest.render.Voxels;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders excavations the way they must be judged: in the ground. The ordinary preview shows a building on a thin slab
 * of earth; a quarry or a mine is mostly what is not there, so this one builds the land deep (the topsoil, then the
 * plot's rock in strata with its seams and pockets, down below the deepest cut) and shows, per blueprint:
 *
 * <ul>
 *   <li>the working from its two corners, finished;</li>
 *   <li>a section through the middle of the plot along its depth (the land in front of the cut taken away), by day and
 *   by night (the galleries' lanterns; light-level colours as in the night previews);</li>
 *   <li>the working as it grows, part by part: after the opening works and at a third, two thirds and the end of its
 *   benches or galleries — what a player sees coming back after weeks.</li>
 * </ul>
 *
 * Run through the init script {@code docs/buildings/extraction_preview.gradle}:
 * <pre>
 *   ./gradlew -I docs/buildings/extraction_preview.gradle extractionPreview -Parchetype=quarry_pit -Pstyle=temperate -Pseeds=2 -Pterrain=slope
 * </pre>
 * args: archetype(s) style(s) seeds terrain[;k=v...] outDir. Output {@code build/previews/extraction/}: a sheet per
 * archetype and terrain ({@code sheet_*.png}) and a panel per blueprint. Problems print as {@code CHECK ...}.
 */
public final class ExtractionPreview {
	private static final int BG = 0xFFE9E4DA;
	private static final int NIGHT = 0xFF10131C;

	private ExtractionPreview() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String archetypeArg = args.length > 0 ? args[0] : "extract/quarry_pit";
		String styleArg = args.length > 1 ? args[1] : "temperate";
		int seeds = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		String terrainArg = args.length > 3 ? args[3] : "flat";
		Path out = Path.of(args.length > 4 ? args[4] : "build/previews/extraction");
		CompoundTag extra = new CompoundTag();
		if (terrainArg.contains(";")) {
			extra = parseParams(terrainArg.substring(terrainArg.indexOf(';') + 1));
			terrainArg = terrainArg.substring(0, terrainArg.indexOf(';'));
		}
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		List<Archetype> archetypes = new ArrayList<>();
		for (Archetype a : registry.all()) {
			String p = a.info().id().getPath();
			for (String want : archetypeArg.split(",")) {
				if (want.equals("all") ? p.startsWith("extract/") : p.equals(want) || p.endsWith("/" + want)) {
					archetypes.add(a);
					break;
				}
			}
		}
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			Style s = service.styles().get(StyleRegistry.styleId(c));
			if (s != null && (styleArg.equals("all") || Arrays.asList(styleArg.split(",")).contains(c.getSerializedName()))) {
				styles.add(s);
			}
		}
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		boolean panels = !Boolean.getBoolean("hearth.preview.sheetsOnly");
		long t0 = System.currentTimeMillis();
		for (Archetype a : archetypes) {
			for (String terrain : terrainArg.split(",")) {
				List<BufferedImage> cells = new ArrayList<>();
				List<String> labels = new ArrayList<>();
				for (Style style : styles) {
					for (int s = 0; s < seeds; s++) {
						long seed = extra.contains("seed") ? extra.getIntOr("seed", 0) + s : 1000L * (s + 1) + 7;
						BufferedImage front = renderOne(service, renderer, a.info(), style, seed, s, terrain, extra, out, panels);
						cells.add(front);
						labels.add(style.culture().getSerializedName() + " s" + s);
					}
				}
				sheet(cells, labels, styles.size(), seeds, out.resolve("sheet_" + a.info().id().getPath().replace('/', '_') + "_" + terrain + ".png"),
					a.info().id() + " on " + terrain);
			}
		}
		System.out.printf(Locale.ROOT, "rendered in %.1f s to %s%n", (System.currentTimeMillis() - t0) / 1000.0, out.toAbsolutePath());
	}

	private static BufferedImage renderOne(BlueprintServiceImpl service, IsoRenderer renderer, ArchetypeInfo info, Style style, long seed, int index,
		String terrain, CompoundTag extra, Path out, boolean panels) throws Exception {
		Culture culture = style.culture();
		float temperature = culture == Culture.TUNDRA ? -0.3F : culture == Culture.ALPINE || culture == Culture.NORTHERN ? 0.3F : 0.8F;
		SyntheticTerrain probe = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), 42, temperature);
		PlotSpec spec = BlueprintPreview.spec(info, style, seed, index, probe, extra);
		BlueprintImpl bp = service.compiled(spec, probe);
		List<String> problems = dev.voidmc.hearth.build.check.BlueprintChecks.all(bp, probe);
		if (!problems.isEmpty()) {
			System.out.println("CHECK " + info.id().getPath() + " " + culture.getSerializedName() + " s" + index + " " + terrain + ": " + String.join(" | ", problems));
		}
		Draft draft = service.draw(spec, probe);
		Rock rock = Rock.of(draft);
		LocalFrame frame = new LocalFrame(spec);
		double yaw = switch (spec.facing()) {
			case SOUTH -> 45;
			case EAST -> 135;
			case NORTH -> 225;
			default -> 315;
		};
		Voxels world = scene(bp, spec, probe, culture, rock, bp.stageCount());
		BufferedImage front = renderer.render(world, new View(yaw, 30), 12, 2, 0);
		if (!panels) {
			return front;
		}
		BufferedImage back = renderer.render(world, new View(yaw + 180, 30), 12, 2, 0);
		// the section: the land on the plot's left of its middle taken away, seen from the left and the front
		int cut = spec.frontage() / 2;
		Voxels section = section(world, frame, cut);
		double sYaw = cornerYaw(frame.toWorld(Dirs.LEFT), frame.toWorld(Dirs.FRONT));
		BufferedImage sec = renderer.render(section, new View(sYaw, 22), 10, 1, 0);
		BufferedImage night = renderer.render(section, new View(sYaw, 22), 10, 1, NIGHT, NightLight.of(section));
		// the working as it grows: the opening works, then a third, two thirds and all of its parts
		int parts = bp.campaignCount();
		List<BufferedImage> grow = new ArrayList<>();
		List<String> growLabels = new ArrayList<>();
		boolean underground = info.id().getPath().equals("extract/mine") || info.id().getPath().equals("extract/shaft");
		for (int k : new int[]{0, Math.max(1, (parts - 1) / 3), Math.max(1, 2 * (parts - 1) / 3), parts - 1}) {
			int upTo = 0;
			while (upTo < bp.stageCount() && bp.campaign(upTo) <= k) {
				upTo++;
			}
			Voxels v = scene(bp, spec, probe, culture, rock, upTo);
			if (underground) {
				v = section(v, frame, cut);
			}
			grow.add(renderer.render(v, new View(underground ? sYaw : yaw, 30), 6, 1, 0));
			growLabels.add(k == 0 ? "opened (campaign 0)" : "after campaign " + k + " of " + (parts - 1));
		}
		String title = info.id() + "  " + culture.getSerializedName() + " s" + index + " " + spec.frontage() + "x" + spec.depth() + " " + spec.facing().getSerializedName()
			+ "  " + terrain + "  " + bp.stageCount() + " stages in " + parts + " campaigns, " + bp.totalSteps() + " steps, rock " + rock.kind()
			+ (draft.variant() >= 0 ? "  · " + draft.dressing() : "");
		BufferedImage panel = panel(title, List.of(front, back), List.of(sec, night), List.of("section", "section by night"), grow, growLabels);
		Path dir = out.resolve(info.id().getPath().replace('/', '_'));
		Files.createDirectories(dir);
		ImageIO.write(panel, "png", dir.resolve(culture.getSerializedName() + "_s" + index + "_" + terrain + ".png").toFile());
		return front;
	}

	/** The yaw that looks at the scene from the corner between two world directions (e.g. west and south). */
	private static double cornerYaw(Direction a, Direction b) {
		boolean east = a == Direction.EAST || b == Direction.EAST;
		boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
		boolean west = a == Direction.WEST || b == Direction.WEST;
		boolean north = a == Direction.NORTH || b == Direction.NORTH;
		if (east && south) {
			return 45;
		}
		if (east && north) {
			return 135;
		}
		if (west && north) {
			return 225;
		}
		return 315;
	}

	/** The scene with the land in front of local column {@code cut} (its left) taken away. */
	private static Voxels section(Voxels world, LocalFrame frame, int cut) {
		Voxels v = new Voxels();
		for (var e : world.all().long2ObjectEntrySet()) {
			long k = e.getLongKey();
			int x = Voxels.kx(k);
			int y = Voxels.ky(k);
			int z = Voxels.kz(k);
			if (frame.localX(x, z) < cut) {
				continue;
			}
			v.set(x, y, z, e.getValue());
		}
		return v;
	}

	/**
	 * The land round the plot down to below the deepest cut, then the blueprint's first {@code stages} stages applied:
	 * topsoil by culture, then the rock in strata (seams of the rock's neighbours, gravel pockets, a little ore).
	 */
	static Voxels scene(BlueprintImpl bp, PlotSpec spec, TerrainProbe probe, Culture culture, Rock rock, int stages) {
		Voxels v = new Voxels();
		int m = 3;
		int x0 = spec.origin().getX() - m;
		int z0 = spec.origin().getZ() - m;
		int x1 = spec.origin().getX() + spec.sizeX() - 1 + m;
		int z1 = spec.origin().getZ() + spec.sizeZ() - 1 + m;
		int bottom = Math.min(bp.bounds().minY(), spec.floorY()) - 3;
		boolean snowy = probe.temperature(spec.origin().getX(), spec.origin().getZ()) < 0.15F;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int g = probe.groundY(x, z);
				int water = probe.waterY(x, z);
				for (int y = bottom; y < g; y++) {
					v.set(x, y, z, ground(culture, rock, g - 1 - y, water != TerrainProbe.NO_WATER, snowy, x, y, z));
				}
				if (water != TerrainProbe.NO_WATER) {
					for (int y = g; y < water; y++) {
						v.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
				}
			}
		}
		for (int s = 0; s < Math.min(stages, bp.stageCount()); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, state, tag) -> v.set(x, y, z, state));
			}
		}
		return v;
	}

	private static BlockState ground(Culture c, Rock rock, int depth, boolean wet, boolean snowy, int x, int y, int z) {
		int h = Math.floorMod(x * 31 + z * 17, 7);
		if (wet) {
			return depth == 0 ? (h < 3 ? Blocks.SAND : h < 5 ? Blocks.GRAVEL : Blocks.DIRT).defaultBlockState()
				: depth < 3 ? Blocks.DIRT.defaultBlockState() : rockAt(rock, x, y, z);
		}
		int soil = 2 + (int) (Noise.unit(77, x, 0, z) * 2);
		if (depth >= soil) {
			return rockAt(rock, x, y, z);
		}
		switch (c) {
			case DESERT -> {
				return Blocks.SAND.defaultBlockState();
			}
			case MESA -> {
				return depth == 0 ? Blocks.RED_SAND.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
			}
			default -> {
			}
		}
		if (depth == 0) {
			return snowy ? Blocks.GRASS_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, true)
				: Blocks.GRASS_BLOCK.defaultBlockState();
		}
		return Blocks.DIRT.defaultBlockState();
	}

	/** The rock at a cell: the host rock in strata, with seams of its neighbours, gravel pockets and a little ore. */
	private static BlockState rockAt(Rock rock, int x, int y, int z) {
		Block base = rock.natural();
		double seam = Noise.value(911, x / 9.0, y / 3.0, z / 9.0);
		double pocket = Noise.value(913, x / 4.0, y / 4.0, z / 4.0);
		double ore = Noise.unit(917, x, y, z);
		if (base == Blocks.STONE || base == Blocks.ANDESITE || base == Blocks.GRANITE || base == Blocks.DIORITE) {
			if (ore < 0.012) {
				return Blocks.COAL_ORE.defaultBlockState();
			}
			if (ore < 0.018) {
				return Blocks.IRON_ORE.defaultBlockState();
			}
			if (ore < 0.021) {
				return Blocks.COPPER_ORE.defaultBlockState();
			}
			if (pocket > 0.82) {
				return Blocks.GRAVEL.defaultBlockState();
			}
			if (seam > 0.72) {
				return (base == Blocks.ANDESITE ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState();
			}
			if (seam < 0.2) {
				return Blocks.TUFF.defaultBlockState();
			}
			if (y < 0) {
				return Blocks.DEEPSLATE.defaultBlockState();
			}
		} else if (base == Blocks.SANDSTONE || base == Blocks.RED_SANDSTONE) {
			if (seam > 0.75) {
				return (base == Blocks.SANDSTONE ? Blocks.SMOOTH_SANDSTONE : Blocks.TERRACOTTA).defaultBlockState();
			}
		} else if (pocket > 0.85) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return base.defaultBlockState();
	}

	private static CompoundTag parseParams(String s) {
		CompoundTag t = new CompoundTag();
		for (String kv : s.split("[;,]")) {
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

	private static BufferedImage panel(String title, List<BufferedImage> row1, List<BufferedImage> row2, List<String> row2Labels, List<BufferedImage> row3,
		List<String> row3Labels) {
		int pad = 12;
		int w = pad;
		int h1 = 0;
		for (BufferedImage i : row1) {
			w += i.getWidth() + pad;
			h1 = Math.max(h1, i.getHeight());
		}
		int w2 = pad;
		int h2 = 0;
		for (BufferedImage i : row2) {
			w2 += i.getWidth() + pad;
			h2 = Math.max(h2, i.getHeight());
		}
		int w3 = pad;
		int h3 = 0;
		for (BufferedImage i : row3) {
			w3 += i.getWidth() + pad;
			h3 = Math.max(h3, i.getHeight());
		}
		int width = Math.max(w, Math.max(w2, w3));
		int height = 34 + h1 + pad + 18 + h2 + pad + 18 + h3 + pad;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, width, height);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, pad, 22);
		int x = pad;
		for (BufferedImage i : row1) {
			g.drawImage(i, x, 34 + h1 - i.getHeight(), null);
			x += i.getWidth() + pad;
		}
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		int y = 34 + h1 + pad;
		x = pad;
		for (int k = 0; k < row2.size(); k++) {
			g.drawString(row2Labels.get(k), x, y + 12);
			g.drawImage(row2.get(k), x, y + 18, null);
			x += row2.get(k).getWidth() + pad;
		}
		y += 18 + h2 + pad;
		x = pad;
		for (int k = 0; k < row3.size(); k++) {
			g.drawString(row3Labels.get(k), x, y + 12);
			g.drawImage(row3.get(k), x, y + 18, null);
			x += row3.get(k).getWidth() + pad;
		}
		g.dispose();
		return img;
	}

	private static void sheet(List<BufferedImage> cells, List<String> labels, int rows, int cols, Path file, String title) throws Exception {
		int cell = 360;
		int pad = 8;
		int labelH = 16;
		int w = cols * (cell + pad) + pad;
		int h = 30 + rows * (cell + labelH + pad) + pad;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 20);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < cells.size(); i++) {
			int r = i / cols;
			int c = i % cols;
			int x = pad + c * (cell + pad);
			int y = 30 + r * (cell + labelH + pad);
			g.drawString(labels.get(i), x, y + 12);
			BufferedImage im = cells.get(i);
			double sc = Math.min(1.0, Math.min((double) cell / im.getWidth(), (double) cell / im.getHeight()));
			int iw = (int) (im.getWidth() * sc);
			int ih = (int) (im.getHeight() * sc);
			g.drawImage(im, x + (cell - iw) / 2, y + labelH + (cell - ih), iw, ih, null);
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
	}
}
