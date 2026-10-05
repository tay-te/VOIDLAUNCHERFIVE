package dev.voidmc.hearth.build.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.terrain.Site;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/**
 * The style model of phase 3 (sub-styles, conditions, regions, local materials, station finish, pinned town dress) and
 * the shipped style data: every material named resolves, every dressing of every region resolves the forms the kit
 * cuts, and buildings in every region, station and local material hold the blueprint invariants.
 */
class StyleModelTest {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private static Style culture(Culture c) {
		return SERVICE.styles().get(StyleRegistry.styleId(c));
	}

	private static StyleContext context(String archetype, int tier, String... params) {
		CompoundTag p = new CompoundTag();
		for (int i = 0; i + 1 < params.length; i += 2) {
			p.putString(params[i], params[i + 1]);
		}
		ArchetypeInfo info = SERVICE.archetype(dev.voidmc.hearth.Hearth.id(archetype));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 42);
		PlotSpec spec = spec(info, Culture.TEMPERATE, 7, probe, p, tier);
		return StyleContext.of(spec, new Site(spec, probe), Culture.TEMPERATE);
	}

	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, TerrainProbe probe, CompoundTag params, int tier) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		int ox = -frontage / 2;
		int oz = -depth / 2;
		int floor = probe.groundY(ox + frontage / 2, oz + depth);
		if (probe.waterY(ox + frontage / 2, oz + depth) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(ox + frontage / 2, oz + depth) + 1);
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), frontage, depth, Direction.SOUTH,
			Math.max(info.maxHeight(), 8), tier, params);
	}

	// ------------------------------------------------------------------------------------------------ the model

	@Test
	void oldJsonDrawsExactlyAsBefore() {
		String json = """
			{"name": "old", "roles": {"wall": "minecraft:bricks"}, "base_weight": 2,
			 "variants": [{"name": "a", "w": 1, "roles": {"wall": "minecraft:stone"}}, {"name": "b", "w": 0.5}]}""";
		Style s = StyleLoader.parse(Identifier.fromNamespaceAndPath("test", "old"), JsonParser.parseString(json).getAsJsonObject());
		for (long seed = 0; seed < 2000; seed++) {
			long h = Noise.hash(seed, 0x7A1A);
			// the draw of phase 2: base weight first, then the variants in order
			double u = Noise.unit(h, 0x7A1, 0, 0) * 3.5;
			int old = u < 2 ? -1 : u < 3 ? 0 : 1;
			assertEquals(old, s.pickVariant(h));
			assertEquals(old, s.pickVariant(h, context("house/cottage", 2, "station", "lord", "district", "rich")));
		}
		assertSame(s, s.dress(StyleContext.NONE, 1, null), "an old style is worn undressed");
	}

	@Test
	void conditionsReadTheContext() {
		When w = When.parse(JsonParser.parseString("""
			[{"stations": ["merchant"], "tiers": {"min": 2}}, {"categories": ["religious"]}, {"archetypes": ["noble/*"], "not": {"tiers": [0, 1]}}]"""));
		assertTrue(w.test(context("house/townhouse", 2, "station", "merchant")));
		assertFalse(w.test(context("house/townhouse", 1, "station", "merchant")));
		assertTrue(w.test(context("civic/chapel", 2)), "a chapel is religious");
		assertTrue(w.test(context("landmark/cathedral", 3)));
		assertFalse(w.test(context("house/cottage", 2)));
		When biome = When.parse(JsonParser.parseString("{\"biomes\": [\"expanse:*grove\"], \"landforms\": [\"flat\"]}"));
		assertTrue(biome.test(context("house/cottage", 2, "biome", "expanse:lumen_grove")));
		assertFalse(biome.test(context("house/cottage", 2, "biome", "expanse:fjordlands")));
		// Expanse's renamed biomes answer to their old and new ids alike
		When oak = When.parse(JsonParser.parseString("{\"biomes\": [\"expanse:ancient_oakwood\"]}"));
		assertTrue(oak.test(context("house/cottage", 2, "biome", "expanse:lumen_grove")));
		assertTrue(oak.test(context("house/cottage", 2, "biome", "expanse:ancient_oakwood")));
		When vale = When.parse(JsonParser.parseString("{\"biomes\": [\"expanse:wisteria_vale\"]}"));
		assertTrue(vale.test(context("house/cottage", 2, "biome", "expanse:woodland_glades")));
		assertFalse(vale.test(context("house/cottage", 2, "biome", "expanse:ancient_oakwood")));
		// stations implied by the archetype and the district
		assertEquals(Station.GENTRY, context("house/manor", 2).station());
		assertEquals(Station.CLERGY, context("civic/chapel", 2).station());
		assertEquals(Station.MERCHANT, context("house/rowhouse", 2, "district", "market").station());
		assertNull(context("house/rowhouse", 2).station());
		assertFalse(context("house/manor", 2).explicitStation());
		assertTrue(context("house/cottage", 2, "station", "poor").explicitStation());
		assertTrue(context("defense/watchtower", 2).isA("fort"));
	}

	@Test
	void substylesFlattenAfterTheFashions() {
		Style t = culture(Culture.TEMPERATE);
		assertEquals("cottage", t.baseName());
		assertEquals("brick nogging", t.variants().get(0).name(), "fashions keep their indices");
		int burgher = index(t, "burgher brick");
		assertTrue(burgher > 3);
		assertEquals("burgher brick", t.variants().get(burgher).substyle());
		assertEquals("burgher brick · limewashed", t.variants().get(burgher + 1).name());
		// a sub-style's roles stand at tier 3 (the culture's tier-3 plaster does not reach it)
		assertSame(Blocks.BRICKS, t.palette(Role.WALL, 3, burgher).main().block(Form.FULL));
		assertSame(Blocks.SMOOTH_QUARTZ, t.palette(Role.WALL, 3, -1).main().block(Form.FULL));
		// a fashion's own tiers win over the culture's
		int rubble = index(t, "rubble stone");
		assertNotEquals(Blocks.SMOOTH_QUARTZ, t.palette(Role.WALL, 3, rubble).main().block(Form.FULL));
		// a sub-style's habits are its own
		assertEquals(Habits.WallKind.BRICK, t.habits(burgher).walls().kind());
		assertEquals(Habits.WallKind.TIMBER_FRAME, t.habits(-1).walls().kind());
	}

	private static int index(Style s, String name) {
		for (int i = 0; i < s.variants().size(); i++) {
			if (s.variants().get(i).name().equals(name)) {
				return i;
			}
		}
		throw new AssertionError("no dressing " + name + " in " + s);
	}

	@Test
	void stationsAndPurposesChooseTheDress() {
		Style t = culture(Culture.TEMPERATE);
		int town = 0;
		int burgher = 0;
		int village = 0;
		int church = 0;
		int castle = 0;
		int hall = 0;
		int library = 0;
		int warehouse = 0;
		int bell = 0;
		int n = 400;
		for (long seed = 0; seed < n; seed++) {
			String merchant = substyle(t, t.pickVariant(seed, context("house/townhouse", 2, "station", "merchant")));
			town += "bohemian town".equals(merchant) ? 1 : 0;
			burgher += "burgher brick".equals(merchant) ? 1 : 0;
			String lord = substyle(t, t.pickVariant(seed, context("house/manor", 3)));
			assertTrue(lord != null && !lord.equals("bohemian village"), "the gentry never wear a village manner: " + lord);
			castle += "bohemian castle".equals(lord) ? 1 : 0;
			String chapel = substyle(t, t.pickVariant(seed, context("civic/chapel", 3)));
			assertTrue("norman stone".equals(chapel) || "bohemian church".equals(chapel), "chapels are white Bohemian churches or Norman stone: " + chapel);
			church += "bohemian church".equals(chapel) ? 1 : 0;
			String cottage = substyle(t, t.pickVariant(seed, context("house/cottage", 2)));
			assertTrue(cottage == null || cottage.equals("bohemian village"), "an ordinary cottage wears a village manner: " + cottage);
			village += "bohemian village".equals(cottage) ? 1 : 0;
			hall += "bohemian town".equals(substyle(t, t.pickVariant(seed, context("civic/town_hall", 3)))) ? 1 : 0;
			library += "bohemian town".equals(substyle(t, t.pickVariant(seed, context("civic/library", 2)))) ? 1 : 0;
			warehouse += "burgher brick".equals(substyle(t, t.pickVariant(seed, context("storage/warehouse", 2)))) ? 1 : 0;
			bell += "bohemian church".equals(substyle(t, t.pickVariant(seed, context("landmark/bell_tower", 3)))) ? 1 : 0;
		}
		// the Bohemian countryside is the temperate culture's main look; the English manners stay among it
		assertTrue(village > n * 0.8 && village < n * 0.95, "cottages are mostly Bohemian: " + village + " of " + n);
		assertTrue(town > n * 0.75 && burgher > n * 0.05 && burgher < n * 0.25, "merchants build Bohemian town houses with the odd burgher brick: " + town + ", "
			+ burgher);
		// Bohemia wins the prominent buildings (art review item 6): 85 % of religious, civic and noble buildings
		assertTrue(church > n * 0.85, "chapels are Bohemian churches: " + church);
		assertTrue(castle > n * 0.85, "manors are rendered Bohemian stone: " + castle);
		assertTrue(hall > n * 0.85 && library > n * 0.85, "town halls and civic buildings are Bohemian town buildings: " + hall + ", " + library);
		assertTrue(bell > n * 0.85, "bell towers are the Bohemian church's: " + bell);
		assertTrue(warehouse > n * 0.85, "warehouses are burgher brick: " + warehouse);
	}

	private static String substyle(Style s, int v) {
		return v < 0 ? null : s.variants().get(v).substyle();
	}

	@Test
	void conditionalFashionsShareTheirSubstylesWeight() {
		Style t = culture(Culture.TEMPERATE);
		int barn = index(t, "bohemian village · plank barn");
		int village = index(t, "bohemian village");
		StyleContext house = context("house/cottage", 2);
		StyleContext farm = context("work/barn", 2);
		double[] wh = t.weights(house);
		double[] wf = t.weights(farm);
		assertEquals(0, wh[barn], "a house is never a plank barn");
		double sumHouse = 0;
		double sumFarm = 0;
		for (int i = 0; i < wh.length; i++) {
			if ("bohemian village".equals(t.variants().get(i).substyle())) {
				sumHouse += wh[i];
				sumFarm += wf[i];
			}
		}
		assertEquals(20, sumHouse, 1e-9, "the village manner competes with its whole weight for a house");
		assertEquals(20, sumFarm, 1e-9, "and for a barn");
		assertTrue(wf[barn] > wf[village] * 2, "barns are mostly boarded");
	}

	@Test
	void regionsAreWornWhereTheyBelong() {
		Style t = culture(Culture.TEMPERATE);
		assertEquals(7, SERVICE.styles().regions().size());
		Style oak = t.regionFor(context("house/cottage", 2, "biome", "expanse:lumen_grove"));
		assertNotNull(oak);
		assertEquals("old_forest", oak.region().name(), "Lumen Grove is now the Ancient Oakwood");
		assertSame(oak, t.regionFor(context("house/cottage", 2, "biome", "expanse:ancient_oakwood")));
		assertSame(oak, t.regionFor(context("house/cottage", 2, "region", "lumen")), "the old region name still forces it");
		assertEquals("glade", t.regionFor(context("house/cottage", 2, "biome", "expanse:wisteria_vale")).region().name());
		assertEquals("glade", t.regionFor(context("house/cottage", 2, "biome", "expanse:woodland_glades")).region().name());
		assertNull(t.regionFor(context("house/cottage", 2)));
		assertNull(t.regionFor(context("house/cottage", 2, "biome", "expanse:lumen_grove", "region", "none")));
		assertEquals("volcanic", t.regionFor(context("house/cottage", 2, "region", "volcanic")).region().name());
		CompoundTag deep = new CompoundTag();
		deep.putBoolean("underground", true);
		deep.putString("biome", "expanse:lumen_grove");
		ArchetypeInfo cottage = SERVICE.archetype(dev.voidmc.hearth.Hearth.id("house/cottage"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 42);
		PlotSpec spec = spec(cottage, Culture.TEMPERATE, 3, probe, deep, 2);
		Draft d = SERVICE.draw(spec, probe);
		assertEquals("deep", d.region(), "the deep wins over the land above it");
		// the region's roles are worn; the culture's stand where it says nothing
		Style worn = d.style();
		assertSame(t, worn.root());
		assertSame(Blocks.DEEPSLATE_TILES, worn.palette(Role.ROOF, 2, d.variant()).main().block(Form.FULL));
		assertSame(t.palette(Role.CROPS, 2, d.variant()).main(), worn.palette(Role.CROPS, 2, d.variant()).main());
	}

	@Test
	void localMaterialsBlendWithTheCulture() {
		CompoundTag local = new CompoundTag();
		local.putString("wood", "minecraft:birch_planks");
		local.putString("stone", "expanse:limestone");
		LocalMaterials l = LocalMaterials.of(local);
		assertNotNull(l);
		Style n = culture(Culture.NORTHERN);
		Palette walls = l.apply(Role.WALL, n.palette(Role.WALL, 2, -1), n.habits(-1), 1);
		assertSame(Blocks.STRIPPED_BIRCH_LOG, LocalMaterials.main(walls).material().block(Form.LOG), "a log town by a birch wood builds hewn birch logs");
		Style t = culture(Culture.TEMPERATE);
		Palette frame = l.apply(Role.FRAME, t.palette(Role.FRAME, 2, -1), t.habits(-1), 1);
		assertSame(t.palette(Role.FRAME, 2, -1), frame, "pale birch posts would vanish against white infill");
		Palette floor = l.apply(Role.FLOOR, t.palette(Role.FLOOR, 2, -1), t.habits(-1), 1);
		assertSame(Blocks.BIRCH_PLANKS, LocalMaterials.main(floor).material().block(Form.FULL));
		// stone keeps the culture's grading: dressed plinths stay dressed, in the local stone
		Palette plinth = l.apply(Role.PLINTH, t.palette(Role.PLINTH, 2, -1), t.habits(-1), 1);
		assertEquals("expanse:limestone_bricks|minecraft:polished_diorite", LocalMaterials.main(plinth).material().spec());
		Palette roof = l.apply(Role.ROOF, t.palette(Role.ROOF, 2, -1), t.habits(-1), 1);
		assertSame(t.palette(Role.ROOF, 2, -1), roof, "roofs are not quarried");
		// a whitewashed culture renders its walls whatever stone lies under them
		Style m = culture(Culture.MEDITERRANEAN);
		CompoundTag wall = new CompoundTag();
		wall.putString("wall", "expanse:limestone");
		assertSame(m.palette(Role.WALL, 2, -1), LocalMaterials.of(wall).apply(Role.WALL, m.palette(Role.WALL, 2, -1), m.habits(-1), 1));
		// some of the culture's own material stays in the mix when the household buys some in
		Palette half = l.apply(Role.FOUNDATION, t.palette(Role.FOUNDATION, 2, -1), t.habits(-1), 0.5);
		Set<String> specs = new HashSet<>();
		for (Palette.Entry e : half.entries()) {
			specs.add(e.material().spec());
		}
		assertTrue(specs.contains("minecraft:cobblestone") && specs.stream().anyMatch(s -> s.startsWith("expanse:limestone")), specs.toString());
	}

	@Test
	void stationsMoveTheFinish() {
		Style t = culture(Culture.TEMPERATE);
		Style merchant = t.dress(context("house/cottage", 2, "station", "merchant"), 1, null);
		assertSame(Blocks.BRICKS, merchant.palette(Role.ROOF, 2, -1).main().block(Form.FULL), "a merchant's house dresses a tier up (tier 3: red clay tile)");
		Style poor = t.dress(context("house/cottage", 2, "station", "poor"), 1, null);
		Palette roof = poor.palette(Role.ROOF, 2, -1);
		assertFalse(roof.patches().isEmpty(), "a hovel's roof is mended in patches");
		assertSame(Blocks.OAK_PLANKS, roof.main().block(Form.FULL), "in the rough shingles of tier 0");
		assertFalse(roof.chooseOne(5).patches().isEmpty(), "patches survive the per-building choice");
		Style lord = t.dress(context("house/manor", 2, "station", "lord"), 1, null);
		Block lantern = lord.palette(Role.LIGHT, 2, -1).main().block(Form.FULL);
		assertNotNull(lantern);
		assertTrue(BuiltInRegistries.BLOCK.getKey(lantern).getPath().contains("lantern"));
	}

	@Test
	void townWorksWearOneDress() {
		ArchetypeInfo tower = SERVICE.archetype(dev.voidmc.hearth.Hearth.id("defense/watchtower"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 42);
		for (Culture c : Culture.values()) {
			Set<Integer> variants = new HashSet<>();
			Set<Block> roofs = new HashSet<>();
			for (long seed = 1; seed <= 12; seed++) {
				Draft d = SERVICE.draw(spec(tower, c, seed * 7919, probe, new CompoundTag(), 2), probe);
				assertTrue(d.pinned());
				variants.add(d.variant());
				roofs.add(d.mat(Role.ROOF).main(Form.FULL));
			}
			assertEquals(1, variants.size(), c + ": the towers of a town wear one dressing");
			assertEquals(1, roofs.size(), c + ": and one roof");
		}
	}

	// ------------------------------------------------------------------------------------------------ the data

	/** Every block id a style names resolves (Expanse ids may fall back; vanilla ids must exist). */
	@Test
	void everyMaterialNamedExists() throws Exception {
		List<String> bad = new ArrayList<>();
		List<String> files = new ArrayList<>();
		for (Culture c : Culture.values()) {
			files.add(c.getSerializedName());
		}
		for (String r : StyleRegistry.BUILTIN_REGIONS) {
			files.add("region/" + r);
		}
		for (String f : files) {
			try (InputStream in = StyleModelTest.class.getResourceAsStream("/data/hearth/hearth_style/" + f + ".json")) {
				assertNotNull(in, f);
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : o.keySet()) {
					if (!Set.of("culture", "name", "base_name", "roles", "tiers", "habits", "base_weight", "base_when", "variants", "substyles", "stations",
						"region", "overrides").contains(k)) {
						bad.add(f + ": unknown key " + k);
					}
				}
				scan(f, o, bad);
			}
		}
		assertTrue(bad.isEmpty(), "unresolved materials:\n  " + String.join("\n  ", bad));
	}

	/** Glowing and fantasy materials, which belong underground (the deep region) and never on the surface. */
	private static final List<String> UNDERGROUND_ONLY = List.of("sea_lantern", "glowstone", "shroomlight", "froglight", "prismite", "glowcap", "lumen",
		"wisteria", "frostbloom", "opal", "amethyst", "end_rod", "crying_obsidian", "purpur", "expanse:amber", "glow_lichen", "magma");

	/** Every culture and every surface region builds in nature's materials: nothing glows or comes in fantasy colours. */
	@Test
	void theSurfaceIsNatural() throws Exception {
		List<String> bad = new ArrayList<>();
		List<String> files = new ArrayList<>();
		for (Culture c : Culture.values()) {
			files.add(c.getSerializedName());
		}
		for (String r : StyleRegistry.BUILTIN_REGIONS) {
			if (!r.equals("deep")) {
				files.add("region/" + r);
			}
		}
		for (String f : files) {
			try (InputStream in = StyleModelTest.class.getResourceAsStream("/data/hearth/hearth_style/" + f + ".json")) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				natural(f, o, bad);
			}
		}
		assertTrue(bad.isEmpty(), "fantasy materials on the surface:\n  " + String.join("\n  ", bad));
	}

	/** The dyes a medieval dyer had (madder, woad, weld) and the undyed wools: no light blue, cyan, lime, magenta, purple. */
	private static final Set<String> NATURAL_DYES = Set.of("red", "blue", "yellow", "white", "light_gray", "brown");

	/** Cloth on the surface is dyed in natural dyes only (art review item 4). */
	@Test
	void surfaceClothIsNaturallyDyed() throws Exception {
		List<String> bad = new ArrayList<>();
		for (String f : surfaceFiles()) {
			try (InputStream in = StyleModelTest.class.getResourceAsStream("/data/hearth/hearth_style/" + f + ".json")) {
				dyes(f, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)), bad);
			}
		}
		assertTrue(bad.isEmpty(), "unnatural dyes on the surface:\n  " + String.join("\n  ", bad));
	}

	private static void dyes(String where, JsonElement e, List<String> bad) {
		if (e.isJsonObject()) {
			e.getAsJsonObject().entrySet().forEach(k -> dyes(where, k.getValue(), bad));
		} else if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> dyes(where, x, bad));
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			for (String alt : e.getAsString().split("\\|")) {
				if (alt.startsWith("dye:") && !NATURAL_DYES.contains(alt.substring(4))) {
					bad.add(where + ": " + alt);
				}
			}
		}
	}

	private static List<String> surfaceFiles() {
		List<String> files = new ArrayList<>();
		for (Culture c : Culture.values()) {
			files.add(c.getSerializedName());
		}
		for (String r : StyleRegistry.BUILTIN_REGIONS) {
			if (!r.equals("deep")) {
				files.add("region/" + r);
			}
		}
		return files;
	}

	/**
	 * Walls are one material with a tonal second in patches, not camouflage (art review item 2): in every surface dressing
	 * and tier, the main material of a masonry, render or brick wall takes at least 85 % of the wall away from the foot
	 * and the eaves, and no wall material is biased to the north faces (moss over a whole face reads as damage); weathering
	 * belongs at the foot ({@code low}).
	 */
	@Test
	void wallsAreNotCamouflage() {
		List<String> bad = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		for (Culture c : Culture.values()) {
			styles.add(culture(c));
		}
		for (Style r : culture(Culture.TEMPERATE).regions()) {
			if (!r.region().name().equals("deep")) {
				styles.add(r);
			}
		}
		for (Style s : styles) {
			for (int v = -1; v < s.variants().size(); v++) {
				for (int tier = 0; tier <= 4; tier++) {
					for (Role role : new Role[]{Role.WALL, Role.GABLE, Role.MASONRY}) {
						Palette p = s.region() != null ? s.own(role, tier, v) : s.palette(role, tier, v);
						if (p == null) {
							continue;
						}
						Palette.Entry main = LocalMaterials.main(p);
						double sum = 0;
						for (Palette.Entry e : p.entries()) {
							sum += Math.max(0, e.weight());
							if (e.north() > 0 && MaterialFamilies.kind(e.material()) != MaterialFamilies.Kind.WOOD) {
								bad.add(s.id() + " " + s.dressingName(v) + " t" + tier + " " + role.key() + ": " + e.material() + " leans north");
							}
						}
						double share = main.weight() / sum;
						if (MaterialFamilies.kind(main.material()) != MaterialFamilies.Kind.WOOD && share < 0.85 - 1e-9) {
							bad.add(String.format(java.util.Locale.ROOT, "%s %s t%d %s: %s only %.0f%%", s.id(), s.dressingName(v), tier, role.key(), main.material(),
								100 * share));
						}
					}
				}
			}
		}
		assertTrue(bad.isEmpty(), bad.size() + " camouflage walls:\n  " + String.join("\n  ", new java.util.TreeSet<>(bad)));
	}

	private static void natural(String where, JsonElement e, List<String> bad) {
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> k : e.getAsJsonObject().entrySet()) {
				if (!Set.of("culture", "when", "base_when", "habits", "region", "name", "base_name").contains(k.getKey())) {
					natural(where, k.getValue(), bad);
				}
			}
		} else if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> natural(where, x, bad));
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			for (String deny : UNDERGROUND_ONLY) {
				if (e.getAsString().contains(deny)) {
					bad.add(where + ": " + e.getAsString());
				}
			}
		}
	}

	private static void scan(String where, JsonElement e, List<String> bad) {
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> k : e.getAsJsonObject().entrySet()) {
				if (k.getKey().equals("when") || k.getKey().equals("base_when") || k.getKey().equals("habits") || k.getKey().equals("region")) {
					continue;
				}
				scan(where, k.getValue(), bad);
			}
		} else if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> scan(where, x, bad));
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			String s = e.getAsString();
			if (!s.contains(":") || s.contains(" ")) {
				return;
			}
			for (String alt : s.split("\\|")) {
				if (alt.startsWith("minecraft:") || alt.startsWith("wood:minecraft:") || alt.startsWith("dye:")) {
					if (Material.of(alt).resolvedFrom().isEmpty()) {
						bad.add(where + ": " + alt);
					}
				}
			}
		}
	}

	/** The forms the kit cuts each role in, as StylesTest checks them for the cultures. */
	private static final Map<Role, Form[]> NEEDS = Map.ofEntries(
		Map.entry(Role.ROOF, new Form[]{Form.STAIRS, Form.SLAB, Form.FULL}),
		Map.entry(Role.FRAME, new Form[]{Form.LOG}),
		Map.entry(Role.BEAM, new Form[]{Form.LOG}),
		Map.entry(Role.DOOR, new Form[]{Form.DOOR}),
		Map.entry(Role.SHUTTER, new Form[]{Form.TRAPDOOR}),
		Map.entry(Role.GLASS, new Form[]{Form.PANE}),
		Map.entry(Role.FURNITURE, new Form[]{Form.STAIRS, Form.SLAB, Form.FENCE, Form.PRESSURE_PLATE, Form.TRAPDOOR}),
		Map.entry(Role.RAILING, new Form[]{Form.FENCE}),
		Map.entry(Role.CLOTH, new Form[]{Form.BED, Form.CARPET}),
		Map.entry(Role.FLOOR, new Form[]{Form.FULL, Form.SLAB}),
		Map.entry(Role.STILT, new Form[]{Form.LOG}),
		Map.entry(Role.CHIMNEY, new Form[]{Form.FULL}),
		Map.entry(Role.WALL, new Form[]{Form.FULL}),
		Map.entry(Role.FOUNDATION, new Form[]{Form.FULL}),
		Map.entry(Role.WINDOW, new Form[]{Form.STAIRS}),
		Map.entry(Role.SHEET, new Form[]{Form.FULL}),
		Map.entry(Role.MASONRY, new Form[]{Form.FULL})
	);

	@Test
	void everyRegionOverEveryCultureCutsTheFormsTheKitNeeds() {
		List<String> missing = new ArrayList<>();
		for (Style region : SERVICE.styles().regions()) {
			for (Culture c : Culture.values()) {
				Style t = culture(c);
				StyleContext ctx = context("house/cottage", 2, "region", region.region().name(), "station", "lord");
				for (int ov = -1; ov < region.variants().size(); ov++) {
					for (int v = -1; v < t.variants().size(); v++) {
						Style worn = t.dress(ctx, ov + 17, null);
						boolean thatch = worn.habits(v).roof().thatch();
						for (int tier = 0; tier <= 4; tier++) {
							for (var e : NEEDS.entrySet()) {
								Role role = thatch && e.getKey() == Role.ROOF ? Role.THATCH : e.getKey();
								Palette p = worn.palette(role, tier, v);
								for (Form f : e.getValue()) {
									if (!p.has(f)) {
										missing.add(region.region().name() + " over " + c.getSerializedName() + " (" + t.dressingName(v) + ") tier " + tier + " "
											+ role.key() + ":" + f);
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), missing.size() + " missing forms:\n  " + String.join("\n  ", missing.subList(0, Math.min(40, missing.size()))));
	}

	/** Buildings in every region (over every culture), every station and with local materials hold the invariants. */
	@Test
	void dressedBuildingsHoldTheInvariants() {
		String[] archetypes = {"house/cottage", "house/townhouse", "civic/chapel", "house/hut", "work/smithy", "defense/watchtower", "house/stilt_house",
			"house/longhouse"};
		List<String> problems = new ArrayList<>();
		int n = 0;
		SyntheticTerrain slope = SyntheticTerrain.named("slope", 42);
		for (String a : archetypes) {
			ArchetypeInfo info = SERVICE.archetype(dev.voidmc.hearth.Hearth.id(a));
			for (Culture c : Culture.values()) {
				List<CompoundTag> dresses = new ArrayList<>();
				for (String r : StyleRegistry.BUILTIN_REGIONS) {
					CompoundTag p = new CompoundTag();
					p.putString("region", r);
					dresses.add(p);
				}
				for (String s : new String[]{"poor", "merchant", "lord"}) {
					CompoundTag p = new CompoundTag();
					p.putString("station", s);
					dresses.add(p);
				}
				CompoundTag local = new CompoundTag();
				CompoundTag l = new CompoundTag();
				l.putString("wood", "minecraft:birch");
				l.putString("stone", "minecraft:tuff");
				local.put("local", l);
				dresses.add(local);
				for (int i = 0; i < dresses.size(); i++) {
					// the seeds the shared invariants test draws undressed (so a failure here is the dress's, not the plot's)
					long seed = 7919L * (i % 2 + 1) + c.ordinal() * 104729L;
					PlotSpec spec = spec(info, c, seed, slope, dresses.get(i), 2);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, slope), slope)) {
						problems.add(a + " " + c.getSerializedName() + " " + dresses.get(i) + ": " + p);
					}
					n++;
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	@Test
	void dressingIsDeterministic() {
		ArchetypeInfo info = SERVICE.archetype(dev.voidmc.hearth.Hearth.id("house/townhouse"));
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 42);
		CompoundTag p = new CompoundTag();
		p.putString("station", "merchant");
		p.putString("region", "lumen");
		CompoundTag l = new CompoundTag();
		l.putString("stone", "minecraft:tuff");
		p.put("local", l);
		PlotSpec spec = spec(info, Culture.TEMPERATE, 99, probe, p, 2);
		var a = SERVICE.draw(spec, probe);
		var b = SERVICE.draw(spec, probe);
		assertEquals(a.dressing(), b.dressing());
		assertTrue(BlueprintChecks.same(dev.voidmc.hearth.build.blueprint.Compiler.compile(a), dev.voidmc.hearth.build.blueprint.Compiler.compile(b)));
	}
}
