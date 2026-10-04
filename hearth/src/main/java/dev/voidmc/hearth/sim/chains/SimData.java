package dev.voidmc.hearth.sim.chains;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.ResourceSite;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The resource economy as data (docs/SIM.md §16): production chains ({@code hearth_sim/recipes}), what each trade
 * gathers from which kind of site ({@code hearth_sim/gathering}), the site kinds' capacity and regrowth
 * ({@code hearth_sim/sites}), what the land of each biome gives ({@code hearth_sim/land}) and the goods' model
 * ({@code hearth_sim/model.json}: what folk can eat, what spoils, what extraction yields). Any data pack can add to or
 * override them under any namespace; a file with the same id as one of ours replaces it.
 *
 * <p>Immutable once built; read on any thread (histories run on world generation workers). The current set is swapped
 * whole on server start and data pack reload.
 */
public final class SimData {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	private static final String ROOT = "hearth_sim";
	private static volatile @Nullable SimData current;

	private final List<Recipe> recipes;
	private final Map<JobType, List<Recipe>> recipesByJob = new EnumMap<>(JobType.class);
	private final Map<Identifier, List<Recipe>> recipesByWorkplace = new LinkedHashMap<>();
	private final Map<JobType, Gathering> gathering;
	private final Map<ResourceSite.Kind, SiteKind> sites;
	private final BiomeTable land;
	private final Model model;
	private final double[] edible;
	private final double[] perishable;
	private final boolean[] covers = new boolean[JobType.values().length];
	private final Map<Identifier, BiomeTable.Specialty> specialties = new LinkedHashMap<>();
	private final java.util.Set<Identifier> cool;

	SimData(List<Recipe> recipes, Map<JobType, Gathering> gathering, Map<ResourceSite.Kind, SiteKind> sites, BiomeTable land, Model model) {
		this.recipes = List.copyOf(recipes);
		this.gathering = Collections.unmodifiableMap(new EnumMap<>(gathering.isEmpty() ? new EnumMap<>(JobType.class) : gathering));
		this.sites = Collections.unmodifiableMap(sites.isEmpty() ? new EnumMap<>(ResourceSite.Kind.class) : new EnumMap<>(sites));
		this.land = land;
		this.model = model;
		this.edible = model.edibleTable();
		this.perishable = model.perishableTable();
		this.cool = java.util.Set.copyOf(model.cool());
		for (Recipe r : this.recipes) {
			if (r.job() != null) {
				this.recipesByJob.computeIfAbsent(r.job(), j -> new ArrayList<>()).add(r);
				this.covers[r.job().ordinal()] = true;
			} else if (r.workplace() != null) {
				this.recipesByWorkplace.computeIfAbsent(r.workplace(), w -> new ArrayList<>()).add(r);
			}
		}
		for (JobType j : this.gathering.keySet()) {
			this.covers[j.ordinal()] = true;
		}
		for (BiomeTable.Entry e : land.biomes().values()) {
			for (BiomeTable.Specialty s : e.specialties()) {
				this.specialties.putIfAbsent(s.item(), s);
			}
		}
		for (BiomeTable.Entry e : land.tags().values()) {
			for (BiomeTable.Specialty s : e.specialties()) {
				this.specialties.putIfAbsent(s.item(), s);
			}
		}
	}

	/** Food value of a unit eaten as it is, by ordinal (FOOD 1). Do not change it. */
	public double[] edible() {
		return this.edible;
	}

	/** Daily spoilage outside cool stores, by ordinal. Do not change it. */
	public double[] perishable() {
		return this.perishable;
	}

	/** Whether the data says what this trade gathers or makes (then the config's per-day row for it is ignored). */
	public boolean covers(JobType job) {
		return this.covers[job.ordinal()];
	}

	/** The land's rarity of this item (who gathers it), or null. */
	public BiomeTable.@Nullable Specialty specialty(@Nullable Identifier item) {
		return item == null ? null : this.specialties.get(item);
	}

	/** Whether this archetype's stores keep food from spoiling. */
	public boolean cool(Identifier archetype) {
		return this.cool.contains(archetype);
	}

	/** What an extraction archetype's cuts yield, or null if it is not one. */
	public @Nullable Extraction extraction(Identifier archetype) {
		return this.model.extraction().get(archetype.getPath());
	}

	// ---------------------------------------------------------------- access

	/** The data in force: what the server loaded, or before any server (tests, previews) what ships in the mod. */
	public static SimData current() {
		SimData d = current;
		if (d == null) {
			synchronized (SimData.class) {
				d = current;
				if (d == null) {
					d = current = builtin();
				}
			}
		}
		return d;
	}

	public static void setCurrent(SimData data) {
		current = data;
	}

	public List<Recipe> recipes() {
		return this.recipes;
	}

	/**
	 * The recipes a worker of {@code job} at {@code workplace} (an archetype id, or null for none) can follow, best first:
	 * the job's own recipes that name this workplace or none, then recipes any worker at this workplace follows.
	 */
	public List<Recipe> recipesFor(JobType job, @Nullable Identifier workplace) {
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : this.recipesByJob.getOrDefault(job, List.of())) {
			if (r.workplace() == null || r.workplace().equals(workplace)) {
				out.add(r);
			}
		}
		if (workplace != null) {
			out.addAll(this.recipesByWorkplace.getOrDefault(workplace, List.of()));
		}
		out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
		return out;
	}

	public @Nullable Gathering gathering(JobType job) {
		return this.gathering.get(job);
	}

	public Map<JobType, Gathering> gatheringByJob() {
		return this.gathering;
	}

	public SiteKind site(ResourceSite.Kind kind) {
		SiteKind k = this.sites.get(kind);
		return k != null ? k : SiteKind.fallback(kind);
	}

	public BiomeTable land() {
		return this.land;
	}

	public Model model() {
		return this.model;
	}

	// ---------------------------------------------------------------- records

	/** Goods amounts as {@code {"logs": 4, "fuel": 1}} in JSON, a dense array by {@link Goods#ordinal()} in code. */
	public static final Codec<double[]> GOODS = Codec.unboundedMap(Goods.CODEC, Codec.DOUBLE).xmap(m -> {
		double[] a = new double[Goods.VALUES.length];
		m.forEach((g, v) -> a[g.ordinal()] = v);
		return a;
	}, a -> {
		Map<Goods, Double> m = new LinkedHashMap<>();
		for (Goods g : Goods.VALUES) {
			if (a[g.ordinal()] != 0) {
				m.put(g, a[g.ordinal()]);
			}
		}
		return m;
	});

	/**
	 * What one kind of land gives (docs/LIVING_WORLD.md §7.1).
	 *
	 * @param perCell    capacity, in units of the site's goods, of one 32 × 32 block cell of it at full density
	 * @param regen      the share of its capacity it regrows a day (0: stone and ore never come back)
	 * @param primary    the trade whose yields describe the site ({@link ResourceSite#yields})
	 * @param maxCells   survey cells one site spans at most (a vast forest becomes several woods)
	 * @param maxSites   sites of this kind a settlement keeps at most (the richest and nearest)
	 * @param material   the block it gives when the land does not say (woods: oak, stone: stone)
	 */
	public record SiteKind(ResourceSite.Kind kind, double perCell, double regen, JobType primary, int maxCells, int maxSites,
		Optional<Identifier> material) {
		public static final Codec<SiteKind> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceSite.Kind.CODEC.fieldOf("kind").forGetter(SiteKind::kind),
			Codec.DOUBLE.optionalFieldOf("per_cell", 1000.0).forGetter(SiteKind::perCell),
			Codec.DOUBLE.optionalFieldOf("regen", 0.0).forGetter(SiteKind::regen),
			JobType.CODEC.optionalFieldOf("primary", JobType.NONE).forGetter(SiteKind::primary),
			Codec.intRange(1, 1024).optionalFieldOf("max_cells", 16).forGetter(SiteKind::maxCells),
			Codec.intRange(0, 256).optionalFieldOf("max_sites", 8).forGetter(SiteKind::maxSites),
			Identifier.CODEC.optionalFieldOf("material").forGetter(SiteKind::material)
		).apply(i, SiteKind::new));

		static SiteKind fallback(ResourceSite.Kind kind) {
			return new SiteKind(kind, 1000, 0, JobType.NONE, 16, 8, Optional.empty());
		}
	}

	/**
	 * What a trade gathers from the land.
	 *
	 * @param split   whether a worker's day is shared out over the kinds of site (a forester: woods, a hunter: woods and
	 *                pasture), or every kind gives its yields at once (an odd-job labourer forages, picks up stone and
	 *                gathers firewood in one day)
	 * @param noSite  what a worker scrapes together where the territory has none of the kinds (per worker-day)
	 * @param tools   tools worn per worker-day (negative: the config's default)
	 */
	public record Gathering(JobType job, List<Entry> sites, double[] noSite, boolean split, double tools) {
		public static final Codec<Gathering> CODEC = RecordCodecBuilder.create(i -> i.group(
			JobType.CODEC.fieldOf("job").forGetter(Gathering::job),
			Entry.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(Gathering::sites),
			GOODS.optionalFieldOf("no_site", new double[Goods.VALUES.length]).forGetter(Gathering::noSite),
			Codec.BOOL.optionalFieldOf("split", true).forGetter(Gathering::split),
			Codec.DOUBLE.optionalFieldOf("tools", -1.0).forGetter(Gathering::tools)
		).apply(i, Gathering::new));

		/**
		 * @param share how much of a split day goes to this kind, relative to the others the territory has
		 * @param wear  units of the site's capacity a worker-day uses up (negative: the sum of the yields)
		 */
		public record Entry(ResourceSite.Kind kind, double[] yields, double share, double wear) {
			public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceSite.Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
				GOODS.fieldOf("yields").forGetter(Entry::yields),
				Codec.DOUBLE.optionalFieldOf("share", 1.0).forGetter(Entry::share),
				Codec.DOUBLE.optionalFieldOf("wear", -1.0).forGetter(Entry::wear)
			).apply(i, Entry::new));

			public double wearPerDay() {
				if (this.wear >= 0) {
					return this.wear;
				}
				double sum = 0;
				for (double y : this.yields) {
					sum += y;
				}
				return sum;
			}
		}

		public @Nullable Entry entry(ResourceSite.Kind kind) {
			for (Entry e : this.sites) {
				if (e.kind == kind) {
					return e;
				}
			}
			return null;
		}
	}

	/**
	 * The goods' model.
	 *
	 * @param edible     food value of a unit of goods eaten as it is (cooked at home): what folk fall back on when
	 *                   there is no bread
	 * @param perishable the share of each good that spoils a day outside a granary
	 * @param extraction what an excavation's cuts yield, by extraction archetype path ({@code extract/quarry_pit})
	 * @param cool       archetypes whose stores keep food from spoiling (granaries, cellars, ice houses)
	 */
	public record Model(Map<Goods, Double> edible, Map<Goods, Double> perishable, Map<String, Extraction> extraction, List<Identifier> cool) {
		public static final Model EMPTY = new Model(Map.of(), Map.of(), Map.of(), List.of());
		public static final Codec<Model> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Goods.CODEC, Codec.DOUBLE).optionalFieldOf("edible", Map.of()).forGetter(Model::edible),
			Codec.unboundedMap(Goods.CODEC, Codec.DOUBLE).optionalFieldOf("perishable", Map.of()).forGetter(Model::perishable),
			Codec.unboundedMap(Codec.STRING, Extraction.CODEC).optionalFieldOf("extraction", Map.of()).forGetter(Model::extraction),
			Identifier.CODEC.listOf().optionalFieldOf("cool", List.of()).forGetter(Model::cool)
		).apply(i, Model::new));

		/** Food value per unit by {@link Goods#ordinal()}; FOOD itself is 1. */
		public double[] edibleTable() {
			double[] t = new double[Goods.VALUES.length];
			this.edible.forEach((g, v) -> t[g.ordinal()] = v);
			t[Goods.FOOD.ordinal()] = 1;
			return t;
		}

		public double[] perishableTable() {
			double[] t = new double[Goods.VALUES.length];
			this.perishable.forEach((g, v) -> t[g.ordinal()] = v);
			return t;
		}

		Model merge(Model o) {
			Map<Goods, Double> e = new EnumMap<>(Goods.class);
			e.putAll(this.edible);
			e.putAll(o.edible);
			Map<Goods, Double> p = new EnumMap<>(Goods.class);
			p.putAll(this.perishable);
			p.putAll(o.perishable);
			Map<String, Extraction> x = new LinkedHashMap<>(this.extraction);
			x.putAll(o.extraction);
			List<Identifier> c = new ArrayList<>(this.cool);
			for (Identifier id : o.cool) {
				if (!c.contains(id)) {
					c.add(id);
				}
			}
			return new Model(e, p, x, c);
		}
	}

	/**
	 * What an extraction archetype's cuts give (docs/LIVING_WORLD.md §7.3).
	 *
	 * @param job     the trade that works it (its labour advances the plot, not builders')
	 * @param kind    the kind of site it works
	 * @param yields  goods per cut block below the natural ground (a plantation: per felled block, any height); ore and
	 *                fuel on top of these come from the site's survey estimate
	 * @param anyHeight whether cuts above the natural ground count too (trees felled in a woodlot)
	 */
	public record Extraction(JobType job, ResourceSite.Kind kind, double[] yields, boolean anyHeight) {
		public static final Codec<Extraction> CODEC = RecordCodecBuilder.create(i -> i.group(
			JobType.CODEC.fieldOf("job").forGetter(Extraction::job),
			ResourceSite.Kind.CODEC.fieldOf("kind").forGetter(Extraction::kind),
			GOODS.optionalFieldOf("yields", new double[Goods.VALUES.length]).forGetter(Extraction::yields),
			Codec.BOOL.optionalFieldOf("any_height", false).forGetter(Extraction::anyHeight)
		).apply(i, Extraction::new));
	}

	// ---------------------------------------------------------------- loading

	/** Reads every {@code hearth_sim} file the server's data packs hold. */
	public static SimData load(ResourceManager resources) {
		Map<Identifier, JsonElement> files = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> e : resources.listResources(ROOT, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				files.put(e.getKey(), JsonParser.parseReader(reader));
			} catch (IOException | RuntimeException ex) {
				LOG.warn("hearth: could not read {}: {}", e.getKey(), ex.toString());
			}
		}
		SimData d = parse(files);
		LOG.info("hearth: sim data: {} recipes, {} gathering trades, {} site kinds, {} biomes described", d.recipes.size(), d.gathering.size(),
			d.sites.size(), d.land.size());
		return d;
	}

	/** What ships in the mod, read from the class path (for tests and anything that runs before a server). */
	public static SimData builtin() {
		Map<Identifier, JsonElement> files = new TreeMap<>();
		try {
			URL url = SimData.class.getClassLoader().getResource("data/hearth/" + ROOT);
			if (url != null) {
				URI uri = url.toURI();
				if ("jar".equals(uri.getScheme())) {
					FileSystem fs;
					try {
						fs = FileSystems.getFileSystem(uri);
					} catch (FileSystemNotFoundException e) {
						fs = FileSystems.newFileSystem(uri, Map.of());
					}
					read(fs.provider().getPath(uri), files);
				} else {
					read(Path.of(uri), files);
				}
			}
		} catch (Exception e) {
			LOG.warn("hearth: could not read the built-in sim data", e);
		}
		return parse(files);
	}

	private static void read(Path root, Map<Identifier, JsonElement> files) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.filter(f -> f.toString().endsWith(".json")).toList()) {
				String rel = root.relativize(p).toString().replace('\\', '/');
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					files.put(Identifier.fromNamespaceAndPath("hearth", ROOT + "/" + rel), JsonParser.parseReader(r));
				}
			}
		}
	}

	/** Builds the data from files keyed by their resource id ({@code ns:hearth_sim/recipes/timber.json}). */
	public static SimData parse(Map<Identifier, JsonElement> files) {
		List<Recipe> recipes = new ArrayList<>();
		Map<JobType, Gathering> gathering = new EnumMap<>(JobType.class);
		Map<ResourceSite.Kind, SiteKind> sites = new EnumMap<>(ResourceSite.Kind.class);
		BiomeTable land = BiomeTable.EMPTY;
		Model model = Model.EMPTY;
		for (Map.Entry<Identifier, JsonElement> e : files.entrySet()) {
			String path = e.getKey().getPath().substring(ROOT.length() + 1);
			JsonElement json = e.getValue();
			try {
				if (path.startsWith("recipes/")) {
					String base = e.getKey().getNamespace() + ":" + path.substring("recipes/".length(), path.length() - 5);
					if (json.isJsonObject() && json.getAsJsonObject().has("recipes")) {
						int n = 0;
						for (JsonElement r : json.getAsJsonObject().getAsJsonArray("recipes")) {
							recipes.add(recipe(r, base + "#" + n++));
						}
					} else {
						recipes.add(recipe(json, base));
					}
				} else if (path.startsWith("gathering/")) {
					Gathering g = Gathering.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
					gathering.put(g.job(), g);
				} else if (path.startsWith("sites/")) {
					SiteKind k = SiteKind.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
					sites.put(k.kind(), k);
				} else if (path.startsWith("land/")) {
					land = land.merge(BiomeTable.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
				} else if (path.equals("model.json")) {
					model = model.merge(Model.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
				}
			} catch (RuntimeException ex) {
				LOG.warn("hearth: {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return new SimData(recipes, gathering, sites, land, model);
	}

	private static Recipe recipe(JsonElement json, String id) {
		JsonObject o = json.getAsJsonObject().deepCopy();
		if (!o.has("id")) {
			o.addProperty("id", id.replace('#', '_'));
		}
		return Recipe.CODEC.parse(JsonOps.INSTANCE, o).getOrThrow();
	}
}
