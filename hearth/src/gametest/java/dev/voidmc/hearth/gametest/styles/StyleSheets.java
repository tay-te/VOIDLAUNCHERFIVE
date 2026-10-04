package dev.voidmc.hearth.gametest.styles;

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
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.PreviewScene;
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
 * Reference renders for the style bible ({@code docs/buildings/styles/}): for each culture and each of its dressings, a
 * cottage, a town house and a chapel, then a street of the dressing; the stations of a culture side by side; the
 * regional styles over their cultures; local materials against none. Every blueprint is checked. Run through the init
 * script {@code docs/buildings/styles/styles_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=temperate
 *   ./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=stations -Pstyle=all
 *   ./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=regions -Pstyle=old_forest,volcanic
 *   ./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=local -Pstyle=temperate "-Pparams=wood=minecraft:birch,stone=expanse:limestone"
 *   args: mode (dress|stations|regions|local|street)  styles (cultures, regions, or all)  outDir  [variants: -1,0,3 or all]  [terrain]  [params k=v,k=v]
 *   (the param px=16 renders larger: pixels per block, default 9)
 * </pre>
 */
public final class StyleSheets {
	private static final int BG = 0xFFE9E4DA;
	private static final String[] STATIONS = {"poor", "peasant", "artisan", "merchant", "gentry", "lord"};
	/** Each region's home cultures (where the sim's culture map puts its land), for the region sheets. */
	private static final String[][] REGION_HOMES = {
		{"old_forest", "temperate", "northern"}, {"glade", "wisteria", "temperate"}, {"cloud_forest", "tropical", "karst"}, {"volcanic", "moorland", "alpine"},
		{"fjord", "maritime", "northern"}, {"tepui", "tropical", "mesa"}, {"deep", "northern", "temperate", "desert"}
	};

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String mode = args.length > 0 ? args[0] : "dress";
		String styleArg = args.length > 1 ? args[1] : "temperate";
		Path out = Path.of(args.length > 2 ? args[2] : "build/previews/styles");
		String variantArg = args.length > 3 && !args[3].isBlank() ? args[3] : "all";
		String terrain = args.length > 4 && !args[4].isBlank() ? args[4] : "flat";
		CompoundTag extra = args.length > 5 ? parse(args[5]) : new CompoundTag();
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		StyleSheets sheets = new StyleSheets(service, renderer, terrain, extra, out);
		long t0 = System.currentTimeMillis();
		switch (mode) {
			case "dress" -> {
				for (Culture c : cultures(styleArg)) {
					sheets.dress(c, variantArg);
				}
			}
			case "stations" -> {
				for (Culture c : cultures(styleArg)) {
					sheets.stations(c);
				}
			}
			case "regions" -> {
				for (String[] home : REGION_HOMES) {
					if (styleArg.equals("all") || List.of(styleArg.split(",")).contains(home[0])) {
						sheets.region(home);
					}
				}
			}
			case "local" -> {
				for (Culture c : cultures(styleArg)) {
					sheets.local(c);
				}
			}
			case "street" -> {
				for (Culture c : cultures(styleArg)) {
					sheets.town(c);
				}
			}
			case "set" -> {
				for (Culture c : cultures(styleArg)) {
					sheets.set(c);
				}
			}
			default -> throw new IllegalArgumentException("mode is dress, stations, regions, local, street or set");
		}
		System.out.printf(Locale.ROOT, "style sheets in %.1f s to %s%n", (System.currentTimeMillis() - t0) / 1000.0, out.toAbsolutePath());
	}

	private static List<Culture> cultures(String arg) {
		List<Culture> out = new ArrayList<>();
		for (Culture c : Culture.values()) {
			if (arg.equals("all") || List.of(arg.split(",")).contains(c.getSerializedName())) {
				out.add(c);
			}
		}
		return out;
	}

	private final BlueprintServiceImpl service;
	private final IsoRenderer renderer;
	private final String terrain;
	private final CompoundTag extra;
	private final Path out;

	private StyleSheets(BlueprintServiceImpl service, IsoRenderer renderer, String terrain, CompoundTag extra, Path out) {
		this.service = service;
		this.renderer = renderer;
		this.terrain = terrain;
		this.extra = extra;
		this.out = out;
	}

	private record Tile(BufferedImage image, String label) {
	}

	private SyntheticTerrain probe(Culture c) {
		float temperature = c == Culture.TUNDRA ? -0.3F : c == Culture.ALPINE || c == Culture.NORTHERN ? 0.3F : 0.8F;
		return new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(this.terrain.toUpperCase(Locale.ROOT)), 42, temperature);
	}

	/** One building, seen from its front-left corner. */
	private Tile building(Culture c, String archetype, long seed, CompoundTag params, int tier, String label) {
		ArchetypeInfo info = this.service.archetype(dev.voidmc.hearth.Hearth.id(archetype));
		SyntheticTerrain probe = probe(c);
		CompoundTag p = this.extra.copy();
		p.merge(params);
		p.putString("facing", "south");
		p.putInt("tier", tier);
		Style style = this.service.styles().get(StyleRegistry.styleId(c));
		PlotSpec spec = dev.voidmc.hearth.gametest.BlueprintPreview.spec(info, style, seed, 0, probe, p);
		BlueprintImpl bp = this.service.compiled(spec, probe);
		check(bp, spec, probe);
		Draft d = this.service.draw(spec, probe);
		Voxels world = PreviewScene.build(bp, spec, probe, 2, c);
		BufferedImage img = this.renderer.render(world, new View(38, 28), this.extra.getIntOr("px", 9), 2, 0);
		return new Tile(img, label + " · " + d.dressing() + (d.station() != null ? " (" + d.station().getSerializedName() + ")" : ""));
	}

	private void check(BlueprintImpl bp, PlotSpec spec, TerrainProbe probe) {
		for (String problem : BlueprintChecks.all(bp, probe)) {
			System.out.println("CHECK " + spec.archetype().getPath() + " " + spec.style().getPath() + " seed " + spec.seed() + " " + spec.params() + ": "
				+ problem);
		}
	}

	/** A street of attached houses (townhouse, rowhouse, shophouse...) along x facing south, each with the given params. */
	private Tile street(Culture c, List<CompoundTag> plots, String[] archetypes, long seed, int tier, String label) {
		SyntheticTerrain probe = probe(c);
		List<PlotSpec> specs = new ArrayList<>();
		int count = plots.size();
		int x = -count * 4;
		for (int i = 0; i < count; i++) {
			ArchetypeInfo info = this.service.archetype(dev.voidmc.hearth.Hearth.id(archetypes[i % archetypes.length]));
			long s = seed * 1000 + i * 37;
			java.util.Random r = new java.util.Random(s);
			int frontage = info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * (0.15 + 0.5 * r.nextDouble()));
			int depth = Math.min(info.maxDepth(), Math.max(info.minDepth(), 10));
			int floor = probe.groundY(x + frontage / 2, 0);
			CompoundTag params = this.extra.copy();
			params.merge(plots.get(i));
			boolean attach = info.flags().contains(ArchetypeInfo.SiteFlag.ATTACHABLE);
			params.putBoolean("party_left", attach && i < count - 1 && attachable(archetypes[(i + 1) % archetypes.length]));
			params.putBoolean("party_right", attach && i > 0 && attachable(archetypes[(i - 1 + archetypes.length) % archetypes.length]));
			params.putString("zone", "core");
			params.putInt("street_y", floor);
			if (!params.contains("storeys")) {
				params.putInt("storeys", info.flags().contains(ArchetypeInfo.SiteFlag.VERTICAL) ? 3 + (i % 2) : 2);
			}
			specs.add(new PlotSpec(info.id(), StyleRegistry.styleId(c), s, new BlockPos(x, floor, -depth), frontage, depth, Direction.SOUTH, info.maxHeight(),
				tier, params));
			x += frontage;
		}
		Voxels world = new Voxels();
		int x0 = specs.get(0).origin().getX() - 3;
		int x1 = x + 2;
		int z0 = -16;
		int z1 = 5;
		for (int gx = x0; gx <= x1; gx++) {
			for (int gz = z0; gz <= z1; gz++) {
				int g = probe.groundY(gx, gz);
				for (int y = g - 3; y < g; y++) {
					BlockState s = y < g - 1 ? Blocks.DIRT.defaultBlockState() : switch (c) {
						case DESERT -> Blocks.SAND.defaultBlockState();
						case MESA -> Blocks.RED_SAND.defaultBlockState();
						default -> Blocks.GRASS_BLOCK.defaultBlockState();
					};
					world.set(gx, y, gz, s);
				}
				if (gz >= 0 && gz <= 3) {
					world.set(gx, g - 1, gz, (Math.floorMod(gx * 7 + gz * 3, 5) == 0 ? Blocks.ANDESITE : Blocks.STONE_BRICKS).defaultBlockState());
				}
				if (c == Culture.TUNDRA) {
					world.set(gx, g, gz, Blocks.SNOW.defaultBlockState());
				}
			}
		}
		for (PlotSpec spec : specs) {
			BlueprintImpl bp = this.service.compiled(spec, probe);
			check(bp, spec, probe);
			for (int st = 0; st < bp.stageCount(); st++) {
				for (int i = 0; i < bp.stepCount(st); i++) {
					bp.step(st, i, (bx, by, bz, state, tag) -> world.set(bx, by, bz, state));
				}
			}
		}
		BufferedImage img = this.renderer.render(world, new View(22, 22), Math.max(4, this.extra.getIntOr("px", 9) - 1), 2, 0);
		return new Tile(img, label);
	}

	private boolean attachable(String archetype) {
		ArchetypeInfo info = this.service.archetype(dev.voidmc.hearth.Hearth.id(archetype));
		return info.flags().contains(ArchetypeInfo.SiteFlag.ATTACHABLE);
	}

	/** Every dressing of a culture: cottage, town house, chapel and a street of it. */
	private void dress(Culture c, String variantArg) throws java.io.IOException {
		Style style = this.service.styles().get(StyleRegistry.styleId(c));
		List<Integer> vs = new ArrayList<>();
		if (variantArg.equals("all")) {
			for (int v = -1; v < style.variants().size(); v++) {
				vs.add(v);
			}
		} else {
			for (String s : variantArg.split(",")) {
				vs.add(Integer.parseInt(s.trim()));
			}
		}
		List<Tile> overview = new ArrayList<>();
		for (int v : vs) {
			CompoundTag p = new CompoundTag();
			p.putInt("variant", v);
			int tier = this.extra.getIntOr("tier", 2);
			List<Tile> row = new ArrayList<>();
			Tile cottage = building(c, "house/cottage", 1007L + v * 13, p, tier, "cottage");
			row.add(cottage);
			overview.add(cottage);
			row.add(building(c, "house/townhouse", 2011L + v * 13, with(p, "storeys", 3), tier, "townhouse"));
			row.add(building(c, "civic/chapel", 3001L + v * 13, p, Math.max(tier, 3), "chapel"));
			List<CompoundTag> plots = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				plots.add(p.copy());
			}
			Tile st = street(c, plots, new String[]{"house/townhouse", "house/rowhouse", "house/shophouse", "house/townhouse", "house/rowhouse",
				"house/shophouse"}, 5 + v, tier, "street");
			String name = style.dressingName(v);
			write(this.out.resolve(c.getSerializedName() + "_" + slug(v, name) + ".png"), c.getSerializedName() + " — " + name, List.of(row, List.of(st)));
		}
		write(this.out.resolve(c.getSerializedName() + "_all.png"), c.getSerializedName() + ": every dressing (cottage)", rows(overview, 5));
	}

	/** The stations of a culture: a cottage and a town house for each, the station given as the planner gives it. */
	private void stations(Culture c) throws java.io.IOException {
		List<Tile> cottages = new ArrayList<>();
		List<Tile> towns = new ArrayList<>();
		int tier = this.extra.getIntOr("tier", 2);
		for (String s : STATIONS) {
			CompoundTag p = new CompoundTag();
			p.putString("station", s);
			cottages.add(building(c, "house/cottage", 1007L, p, tier, s));
			towns.add(building(c, "house/townhouse", 2011L, with(p, "storeys", 3), tier, s));
		}
		List<CompoundTag> plots = new ArrayList<>();
		for (String s : new String[]{"poor", "artisan", "merchant", "merchant", "gentry", "artisan"}) {
			CompoundTag p = new CompoundTag();
			p.putString("station", s);
			plots.add(p);
		}
		Tile st = street(c, plots, new String[]{"house/rowhouse", "house/shophouse", "house/townhouse", "house/townhouse", "house/townhouse",
			"house/rowhouse"}, 9, tier, "street: poor, artisan, merchant, merchant, gentry, artisan");
		write(this.out.resolve(c.getSerializedName() + "_stations.png"), c.getSerializedName() + " by station (tier " + tier + ")",
			List.of(cottages, towns, List.of(st)));
	}

	/** A region worn over its home cultures. */
	private void region(String[] home) throws java.io.IOException {
		List<List<Tile>> rows = new ArrayList<>();
		for (int i = 1; i < home.length; i++) {
			Culture c = Culture.valueOf(home[i].toUpperCase(Locale.ROOT));
			CompoundTag p = new CompoundTag();
			p.putString("region", home[0]);
			if (home[0].equals("deep")) {
				p.putBoolean("underground", true);
			}
			int tier = this.extra.getIntOr("tier", 2);
			List<Tile> row = new ArrayList<>();
			row.add(building(c, "house/cottage", 1007L, p, tier, c.getSerializedName() + " cottage"));
			row.add(building(c, "house/townhouse", 2011L, with(p, "storeys", 3), tier, "townhouse"));
			row.add(building(c, "civic/chapel", 3001L, p, Math.max(3, tier), "chapel"));
			rows.add(row);
			if (i == 1) {
				List<CompoundTag> plots = new ArrayList<>();
				for (int k = 0; k < 6; k++) {
					plots.add(p.copy());
				}
				rows.add(List.of(street(c, plots, new String[]{"house/townhouse", "house/rowhouse", "house/shophouse", "house/townhouse", "house/rowhouse",
					"house/shophouse"}, 7, tier, "street (" + c.getSerializedName() + ")")));
			}
		}
		write(this.out.resolve("region_" + home[0] + ".png"), "region: " + home[0], rows);
	}

	/** Local materials against none: the same buildings with the {@code local} params given in the sheet's params. */
	private void local(Culture c) throws java.io.IOException {
		CompoundTag local = new CompoundTag();
		for (String k : this.extra.keySet()) {
			if (k.equals("tier")) {
				continue;
			}
			local.putString(k, this.extra.getStringOr(k, ""));
		}
		List<List<Tile>> rows = new ArrayList<>();
		int tier = this.extra.getIntOr("tier", 2);
		for (int pass = 0; pass < 2; pass++) {
			CompoundTag p = new CompoundTag();
			if (pass == 1) {
				p.put("local", local);
			}
			String tag = pass == 0 ? "none" : "local " + local;
			List<Tile> row = new ArrayList<>();
			row.add(building(c, "house/cottage", 1007L, p, tier, tag));
			row.add(building(c, "house/townhouse", 2011L, with(p, "storeys", 3), tier, "townhouse"));
			row.add(building(c, "civic/chapel", 3001L, p, 3, "chapel"));
			rows.add(row);
		}
		write(this.out.resolve(c.getSerializedName() + "_local.png"), c.getSerializedName() + ": local materials", rows);
	}

	/** A street as a planner would issue it, nothing forced: districts and stations choose the dress. */
	private void town(Culture c) throws java.io.IOException {
		List<List<Tile>> rows = new ArrayList<>();
		String[][] districts = {{"crafts", "crafts", "market", "market", "market", "crafts"}, {"rich", "rich", "rich", "market", "rich", "rich"}};
		for (int k = 0; k < districts.length; k++) {
			List<CompoundTag> plots = new ArrayList<>();
			for (String dist : districts[k]) {
				CompoundTag p = new CompoundTag();
				p.putString("district", dist);
				plots.add(p);
			}
			rows.add(List.of(street(c, plots, new String[]{"house/townhouse", "house/rowhouse", "house/shophouse", "house/townhouse", "house/townhouse",
				"house/rowhouse"}, 21 + k, 3, "street, districts " + String.join(",", districts[k]))));
		}
		write(this.out.resolve(c.getSerializedName() + "_streets.png"), c.getSerializedName() + ": streets by district", rows);
	}

	/** The buildings of a settlement as the planner issues them, nothing forced: each archetype wears what it draws. */
	private static final String[] SET = {"house/cottage", "house/farmhouse", "work/barn", "work/smithy", "commerce/inn", "house/townhouse",
		"civic/chapel", "house/manor", "defense/watchtower", "storage/granary", "house/hut", "commerce/market_hall"};

	private void set(Culture c) throws java.io.IOException {
		List<Tile> tiles = new ArrayList<>();
		int tier = this.extra.getIntOr("tier", 2);
		long base = this.extra.getIntOr("seed", 11);
		for (int i = 0; i < SET.length; i++) {
			if (this.service.archetype(dev.voidmc.hearth.Hearth.id(SET[i])) == null) {
				continue;
			}
			CompoundTag p = new CompoundTag();
			if (SET[i].equals("house/townhouse")) {
				p.putInt("storeys", 3);
			}
			tiles.add(building(c, SET[i], base * 7919 + i * 131, p, SET[i].equals("civic/chapel") || SET[i].equals("house/manor") ? Math.max(3, tier) : tier,
				SET[i].substring(SET[i].indexOf('/') + 1)));
		}
		write(this.out.resolve(c.getSerializedName() + "_set" + (this.extra.contains("biome") ? "_" + slug(-1, this.extra.getStringOr("biome", "")).substring(5) : "")
			+ (this.extra.contains("seed") ? "_s" + base : "") + ".png"), c.getSerializedName() + ": a settlement's buildings as drawn" + (this.extra.contains("biome") ? " in " + this.extra.getStringOr("biome", "") : ""),
			rows(tiles, 4));
	}

	private static CompoundTag with(CompoundTag t, String k, int v) {
		CompoundTag c = t.copy();
		c.putInt(k, v);
		return c;
	}

	private static String slug(int v, String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		return (v < 0 ? "base" : String.format(Locale.ROOT, "v%02d", v)) + "_" + s;
	}

	private static List<List<Tile>> rows(List<Tile> tiles, int per) {
		List<List<Tile>> out = new ArrayList<>();
		for (int i = 0; i < tiles.size(); i += per) {
			out.add(tiles.subList(i, Math.min(tiles.size(), i + per)));
		}
		return out;
	}

	private static void write(Path file, String title, List<List<Tile>> rows) throws java.io.IOException {
		int pad = 10;
		int labelH = 16;
		int w = 0;
		int h = 32;
		for (List<Tile> row : rows) {
			int rw = pad;
			int rh = 0;
			for (Tile t : row) {
				rw += t.image.getWidth() + pad;
				rh = Math.max(rh, t.image.getHeight());
			}
			w = Math.max(w, rw);
			h += rh + labelH + pad;
		}
		BufferedImage img = new BufferedImage(Math.max(w, 400), h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(BG, true));
		g.fillRect(0, 0, img.getWidth(), h);
		g.setColor(new Color(0x3A3226));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		g.drawString(title, pad, 21);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		int y = 32;
		for (List<Tile> row : rows) {
			int x = pad;
			int rh = 0;
			for (Tile t : row) {
				rh = Math.max(rh, t.image.getHeight());
			}
			for (Tile t : row) {
				g.drawString(t.label, x, y + 12);
				g.drawImage(t.image, x, y + labelH + rh - t.image.getHeight(), null);
				x += t.image.getWidth() + pad;
			}
			y += rh + labelH + pad;
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
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
}
